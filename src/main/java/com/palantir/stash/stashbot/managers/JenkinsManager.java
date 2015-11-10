// Copyright 2014 Palantir Technologies
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.palantir.stash.stashbot.managers;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.user.UserService;
import com.atlassian.bitbucket.util.Operation;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.jenkins_client_jarjar.base.Optional;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsManager implements DisposableBean {

    static final String GROOVY_GET_CREDENTIALS_TEMPLATE_FILE = "get_credential_uuid_for_user.groovy.vm";
    static final String GROOVY_CREATE_CREDENTIALS_TEMPLATE_FILE = "create_credential_for_user.groovy.vm";

    private final ConfigurationPersistenceService cpm;
    private final JobTemplateManager jtm;
    private final JenkinsJobXmlFormatter xmlFormatter;
    private final JenkinsClientManager jenkinsClientManager;
    private final RepositoryService repositoryService;
    private final StashbotUrlBuilder sub;
    private final Logger log;
    private final PluginLoggerFactory lf;
    private final SecurityService ss;
    private final UserService us;
    private final UserManager um;
    private final ExecutorService es;
    private final VelocityManager vm;

    public JenkinsManager(RepositoryService repositoryService,
        ConfigurationPersistenceService cpm, JobTemplateManager jtm, JenkinsJobXmlFormatter xmlFormatter,
        JenkinsClientManager jenkisnClientManager, StashbotUrlBuilder sub, PluginLoggerFactory lf, SecurityService ss,
        UserService us, UserManager um, VelocityManager vm) {
        this.repositoryService = repositoryService;
        this.cpm = cpm;
        this.jtm = jtm;
        this.xmlFormatter = xmlFormatter;
        this.jenkinsClientManager = jenkisnClientManager;
        this.sub = sub;
        this.lf = lf;
        this.log = lf.getLoggerForThis(this);
        this.ss = ss;
        this.us = us;
        this.um = um;
        this.es = Executors.newCachedThreadPool();
        this.vm = vm;
    }

    /**
     * This method queries to see if a credential exists. If it doesn't, it creates it. The ID of the credential is
     * returned.
     * 
     * @return the credential id
     * @param jsc
     * @param rc
     */
    public String ensureCredentialExists(JenkinsServerConfiguration jsc, RepositoryConfiguration rc) {
        try {
            JenkinsServer js = jenkinsClientManager.getJenkinsServer(jsc, rc);

            String id;
            {
                VelocityContext vc = vm.getVelocityContext();
                // for getting the existing credential, the only thing we need is $user
                vc.put("user", jsc.getStashUsername());
                VelocityEngine ve = vm.getVelocityEngine();
                StringWriter groovy = new StringWriter();
                Template template = ve.getTemplate(GROOVY_GET_CREDENTIALS_TEMPLATE_FILE);
                template.merge(vc, groovy);

                String result = js.runScript(groovy.toString());
                if (!result.startsWith("Result: ")) {
                    throw new RuntimeException("Unable to query for credentials: " + result);
                }
                id = result.split("Result: ")[1].trim();
            }

            if (id.equals("not found")) {
                // we have to create it
                {
                    VelocityContext vc = vm.getVelocityContext();
                    // for creating the credential, the args we need are: user, privKey, and id (where id is a random UUID)
                    vc.put("user", jsc.getStashUsername());
                    String uuid = UUID.randomUUID().toString();
                    vc.put("id", uuid);
                    // key contains "+" in it, which needs to be encoded when posted to the server.
                    vc.put("privKey", URLEncoder.encode(cpm.getDefaultPrivateSshKey(), "UTF-8"));
                    VelocityEngine ve = vm.getVelocityEngine();
                    StringWriter groovy = new StringWriter();
                    Template template = ve.getTemplate(GROOVY_CREATE_CREDENTIALS_TEMPLATE_FILE);
                    template.merge(vc, groovy);

                    String result = js.runScript(groovy.toString());
                    if (!result.startsWith("Result: ")) {
                        throw new RuntimeException("Unable to query for credentials: " + result);
                    }
                    id = result.split("Result: ")[1].trim();
                    if (!id.equals(uuid)) {
                        log.error("Possible problem trying to create credentials (ID should be " + uuid + " but was: "
                            + result);
                    }
                }
            }
            return id;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateRepo(Repository repo) {
        try {
            Callable<Void> visit = new UpdateAllRepositoryVisitor(
                jenkinsClientManager, jtm, cpm, repo, lf);
            visit.call();
        } catch (Exception e) {
            log.error(
                "Exception while attempting to create missing jobs for a repo: ",
                e);
        }
    }

    private FolderJob getOrCreateFolderJob(JenkinsServer js, FolderJob root, String name) {
        try {
            log.info("Attempting to fetch job '" + name + "' in folder '" + (root == null ? "/" : root) + "'");
            if (js.getJobs(root).containsKey(name)) {
                Job j = js.getJob(root, name);
                Optional<FolderJob> fj = js.getFolderJob(j);
                if (fj.isPresent()) {
                    return fj.get();
                }
                throw new IllegalStateException("job " + name + " exists in folder " + (root == null ? "/" : root)
                    + " but is not a folder");
            }
            log.info("Job " + name + " did not exist; creating.");
            js.createFolder(root, name);
            Job j = js.getJob(root, name);
            Optional<FolderJob> fj = js.getFolderJob(j);
            if (fj.isPresent()) {
                return fj.get();
            }
            throw new IllegalStateException("tried to create folder " + name + " in folder "
                + (root == null ? "/" : root) + " but it still doesn't exist");
        } catch (IOException e) {
            throw new RuntimeException("Exception while attempting to get/vivify folder chain", e);
        }
    }

    private FolderJob getPrefixFolderJob(JenkinsServer js, JenkinsServerConfiguration jsc, JobTemplate jt,
        Repository repo) {
        String prefix = jsc.getFolderPrefix();
        String folderName = jt.getPathFor(repo);
        String fullPath = "";
        if (prefix != null && !prefix.isEmpty()) {
            fullPath = prefix;
        }
        if (jsc.getUseSubFolders()) {
            if (!fullPath.isEmpty()) {
                fullPath = StringUtils.join(ImmutableList.of(fullPath, folderName), "/");
            } else {
                fullPath = folderName;
            }
        }

        FolderJob root = null;
        List<String> pathParts = ImmutableList.copyOf(StringUtils.split(fullPath, "/"));
        for (String part : pathParts) {
            root = getOrCreateFolderJob(js, root, part);
        }
        return root;
    }

    public void createJob(Repository repo, JobTemplate jobTemplate) {
        try {
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);
            final JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());
            final JenkinsServer jenkinsServer = jenkinsClientManager
                .getJenkinsServer(jsc, rc);
            final String jobName = jobTemplate.getBuildNameFor(repo);

            // if the job is using credentials, we have to ensure they are deployed first
            switch (jsc.getAuthenticationMode()) {
            case CREDENTIAL_AUTOMATIC_SSH_KEY:
                String id = ensureCredentialExists(jsc, rc);
                if (!jsc.getCredentialId().equals(id)) {
                    jsc.setCredentialId(id);
                    jsc.save();
                }
                break;
            case CREDENTIAL_MANUALLY_CONFIGURED:
            case USERNAME_AND_PASSWORD:
                // do nothing
                break;
            }
            // If we try to create a job which already exists, we still get a
            // 200... so we should check first to make
            // sure it doesn't already exist
            FolderJob root = getPrefixFolderJob(jenkinsServer, jsc, jobTemplate, repo);
            Map<String, Job> jobMap = jenkinsServer.getJobs(root);

            if (jobMap.containsKey(jobName)) {
                throw new IllegalArgumentException("Job " + jobName
                    + " already exists");
            }

            String xml = xmlFormatter.generateJobXml(jobTemplate, repo);

            log.trace("Sending XML to jenkins to create job: " + xml);
            jenkinsServer.createJob(root, jobName, xml, false);
        } catch (IOException e) {
            // TODO: something other than just rethrow?
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method IGNORES the current job XML, and regenerates it from scratch, and posts it. If any changes were made
     * to the job directly via jenkins UI, this will overwrite those changes.
     * 
     * @param repo
     * @param buildType
     */
    public void updateJob(Repository repo, JobTemplate jobTemplate) {
        try {
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);
            final JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());
            final JenkinsServer jenkinsServer = jenkinsClientManager
                .getJenkinsServer(jsc, rc);
            final String jobName = jobTemplate.getBuildNameFor(repo);

            // if the job is using credentials, we have to ensure they are deployed first
            switch (jsc.getAuthenticationMode()) {
            case CREDENTIAL_AUTOMATIC_SSH_KEY:
                String id = ensureCredentialExists(jsc, rc);
                if (!jsc.getCredentialId().equals(id)) {
                    jsc.setCredentialId(id);
                    jsc.save();
                }
                break;
            case CREDENTIAL_MANUALLY_CONFIGURED:
            case USERNAME_AND_PASSWORD:
                // do nothing
                break;
            }

            FolderJob root = getPrefixFolderJob(jenkinsServer, jsc, jobTemplate, repo);
            Map<String, Job> jobMap = jenkinsServer.getJobs(root);

            String xml = xmlFormatter.generateJobXml(jobTemplate, repo);

            if (jobMap.containsKey(jobName)) {
                if (!rc.getPreserveJenkinsJobConfig()) {
                    log.trace("Sending XML to jenkins to update job: " + xml);
                    jenkinsServer.updateJob(root, jobName, xml, false);
                } else {
                    log.trace("Skipping sending XML to jenkins. Repo Config is set to preserve jenkins job config.");
                }
                return;
            }

            log.trace("Sending XML to jenkins to update job: " + xml);
            jenkinsServer.createJob(root, jobName, xml, false);
        } catch (IOException e) {
            // TODO: something other than just rethrow?
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void triggerBuild(final Repository repo, final JobType jobType,
        final String hashToBuild, final String buildRef) {

        final String username = um.getRemoteUser().getUsername();
        final ApplicationUser su = us.findUserByNameOrEmail(username);

        es.submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                // TODO: See if we can do something like StateTransferringExecutorService here instead
                ss.impersonating(su, "Running as user '" + username + "' in alternate thread asynchronously")
                    .call(new Operation<Void, Exception>() {

                        @Override
                        public Void perform() throws Exception {
                            synchronousTriggerBuild(repo, jobType, hashToBuild, buildRef);
                            return null;
                        }
                    });
                return null;
            };
        });
    }

    public void triggerBuild(final Repository repo, final JobType jobType,
        final PullRequest pr) {

        final String username = um.getRemoteUser().getUsername();
        final ApplicationUser su = us.findUserByNameOrEmail(username);

        es.submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                // TODO: See if we can do something like StateTransferringExecutorService here instead
                ss.impersonating(su, "Running as user '" + username + "' in alternate thread asynchronously")
                    .call(new Operation<Void, Exception>() {

                        @Override
                        public Void perform() throws Exception {
                            synchronousTriggerBuild(repo, jobType, pr);
                            return null;
                        }
                    });
                return null;
            };
        });
    }

    public void synchronousTriggerBuild(Repository repo, JobType jobType,
        String hashToBuild, String buildRef) {
        try {
            RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);
            JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());
            JobTemplate jt = jtm.getJobTemplate(jobType, rc);

            String jenkinsBuildId = jt.getBuildNameFor(repo);
            String url = jsc.getUrl();
            String user = jsc.getUsername();
            String password = jsc.getPassword();

            log.info("Triggering jenkins build id " + jenkinsBuildId
                + " on hash " + hashToBuild + " (" + user + "@" + url
                + " pw: " + password.replaceAll(".", "*") + ")");

            final JenkinsServer js = jenkinsClientManager.getJenkinsServer(jsc,
                rc);
            FolderJob root = getPrefixFolderJob(js, jsc, jt, repo);
            Map<String, Job> jobMap = js.getJobs(root);
            String key = jt.getBuildNameFor(repo);

            if (!jobMap.containsKey(key)) {
                throw new RuntimeException("Build doesn't exist: " + key);
            }

            Builder<String, String> builder = ImmutableMap.builder();
            builder.put("buildHead", hashToBuild);
            builder.put("repoId", String.valueOf(repo.getId()));
            if (buildRef != null) {
                builder.put("buildRef", buildRef);
            }

            jobMap.get(key).build(builder.build(), false);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (HttpResponseException e) { // subclass of IOException thrown by
                                            // client
            if (e.getStatusCode() == 302) {
                // BUG in client - this isn't really an error, assume the build
                // triggered ok and this is just a redirect
                // to some URL after the fact.
                return;
            }
            // For other HTTP errors, log it for easier debugging
            log.error(
                "HTTP Error (resp code "
                    + Integer.toString(e.getStatusCode()) + ")", e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void synchronousTriggerBuild(Repository repo, JobType jobType,
        PullRequest pullRequest) {

        try {
            String pullRequestId = String.valueOf(pullRequest.getId());
            String hashToBuild = pullRequest.getToRef().getLatestCommit();

            RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);
            JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());
            JobTemplate jt = jtm.getJobTemplate(jobType, rc);

            String jenkinsBuildId = jt.getBuildNameFor(repo);
            String url = jsc.getUrl();
            String user = jsc.getUsername();
            String password = jsc.getPassword();

            log.info("Triggering jenkins build id " + jenkinsBuildId
                + " on hash " + hashToBuild + " (" + user + "@" + url
                + " pw: " + password.replaceAll(".", "*") + ")");

            final JenkinsServer js = jenkinsClientManager.getJenkinsServer(jsc,
                rc);
            FolderJob root = getPrefixFolderJob(js, jsc, jt, repo);
            Map<String, Job> jobMap = js.getJobs(root);
            String key = jt.getBuildNameFor(repo);

            if (!jobMap.containsKey(key)) {
                throw new RuntimeException("Build doesn't exist: " + key);
            }

            Builder<String, String> builder = ImmutableMap.builder();
            builder.put("repoId", String.valueOf(repo.getId()));
            if (pullRequest != null) {
                log.debug("Determined pullRequestId " + pullRequestId);
                builder.put("pullRequestId", pullRequestId);
                // toRef is always present in the repo
                builder.put("buildHead", pullRequest.getToRef()
                    .getLatestCommit().toString());
                // fromRef may be in a different repo
                builder.put("mergeRef", pullRequest.getFromRef().getId());
                builder.put("buildRef", pullRequest.getToRef().getId());
                builder.put("mergeRefUrl", sub.buildCloneUrl(pullRequest.getFromRef().getRepository(), jsc));
                builder.put("mergeHead", pullRequest.getFromRef()
                    .getLatestCommit().toString());
            }

            jobMap.get(key).build(builder.build(), false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (HttpResponseException e) { // subclass of IOException thrown by
                                            // client
            if (e.getStatusCode() == 302) {
                // BUG in client - this isn't really an error, assume the build
                // triggered ok and this is just a redirect
                // to some URL after the fact.
                return;
            }
            // For other HTTP errors, log it for easier debugging
            log.error(
                "HTTP Error (resp code "
                    + Integer.toString(e.getStatusCode()) + ")", e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Code to ensure a given repository has plans that exist in jenkins.
     * 
     * @author cmyers
     */
    class CreateMissingRepositoryVisitor implements Callable<Void> {

        private final JenkinsClientManager jcm;
        private final JobTemplateManager jtm;
        private final ConfigurationPersistenceService cpm;
        private final Repository r;
        private final Logger log;

        public CreateMissingRepositoryVisitor(
            JenkinsClientManager jcm, JobTemplateManager jtm,
            ConfigurationPersistenceService cpm, Repository r,
            PluginLoggerFactory lf) {
            this.jcm = jcm;
            this.jtm = jtm;
            this.cpm = cpm;
            this.r = r;
            this.log = lf.getLoggerForThis(this);
        }

        @Override
        public Void call() throws Exception {
            RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(r);
            // may someday require repo also...
            JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());

            if (!rc.getCiEnabled())
                return null;

            // make sure jobs exist
            List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
            JenkinsServer js = jcm.getJenkinsServer(jsc, rc);

            for (JobTemplate template : templates) {
                FolderJob root = getPrefixFolderJob(js, jsc, template, r);
                Map<String, Job> jobs = js.getJobs(root);
                if (!jobs.containsKey(template.getBuildNameFor(r))) {
                    log.info("Creating " + template.getName()
                        + " job for repo " + r.toString());
                    createJob(r, template);
                }
            }
            return null;
        }
    }

    public void createMissingJobs() {

        ExecutorService es = Executors.newCachedThreadPool();
        List<Future<Void>> futures = new LinkedList<Future<Void>>();

        PageRequest pageReq = new PageRequestImpl(0, 500);
        Page<? extends Repository> p = repositoryService.findAll(pageReq);
        while (true) {
            for (Repository r : p.getValues()) {
                Future<Void> f = es.submit(new CreateMissingRepositoryVisitor(
                    jenkinsClientManager, jtm, cpm, r, lf));
                futures.add(f);
            }
            if (p.getIsLastPage())
                break;
            pageReq = p.getNextPageRequest();
            p = repositoryService.findAll(pageReq);
        }
        for (Future<Void> f : futures) {
            try {
                f.get(); // don't care about return, just catch exceptions
            } catch (ExecutionException e) {
                log.error(
                    "Exception while attempting to create missing jobs for a repo: ",
                    e);
            } catch (InterruptedException e) {
                log.error("Interrupted: this shouldn't happen", e);
            }
        }
    }

    /**
     * Code to ensure a given repository has plans that exist in jenkins.
     * 
     * @author cmyers
     */
    class UpdateAllRepositoryVisitor implements Callable<Void> {

        private final JenkinsClientManager jcm;
        private final JobTemplateManager jtm;
        private final ConfigurationPersistenceService cpm;
        private final Repository r;
        private final Logger log;

        public UpdateAllRepositoryVisitor(
            JenkinsClientManager jcm, JobTemplateManager jtm,
            ConfigurationPersistenceService cpm, Repository r,
            PluginLoggerFactory lf) {
            this.jcm = jcm;
            this.jtm = jtm;
            this.cpm = cpm;
            this.r = r;
            this.log = lf.getLoggerForThis(this);
        }

        @Override
        public Void call() throws Exception {
            RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(r);
            // may someday require repo also...
            JenkinsServerConfiguration jsc = cpm
                .getJenkinsServerConfiguration(rc.getJenkinsServerName());

            if (!rc.getCiEnabled())
                return null;

            // make sure jobs are up to date
            List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
            JenkinsServer js = jcm.getJenkinsServer(jsc, rc);
            for (JobTemplate jobTemplate : templates) {
                FolderJob root = getPrefixFolderJob(js, jsc, jobTemplate, r);
                Map<String, Job> jobs = js.getJobs(root);
                if (!jobs.containsKey(jobTemplate.getBuildNameFor(r))) {
                    log.info("Creating " + jobTemplate.getName()
                        + " job for repo " + r.toString());
                    createJob(r, jobTemplate);
                } else {
                    // update job
                    log.info("Updating " + jobTemplate.getName()
                        + " job for repo " + r.toString());
                    updateJob(r, jobTemplate);
                }
            }
            return null;
        }
    }

    public void updateAllJobs() {

        ExecutorService es = Executors.newCachedThreadPool();
        List<Future<Void>> futures = new LinkedList<Future<Void>>();

        PageRequest pageReq = new PageRequestImpl(0, 500);
        Page<? extends Repository> p = repositoryService.findAll(pageReq);
        while (true) {
            for (Repository r : p.getValues()) {
                Future<Void> f = es.submit(new UpdateAllRepositoryVisitor(
                    jenkinsClientManager, jtm, cpm, r, lf));
                futures.add(f);
            }
            if (p.getIsLastPage())
                break;
            pageReq = p.getNextPageRequest();
            p = repositoryService.findAll(pageReq);
        }
        for (Future<Void> f : futures) {
            try {
                f.get(); // don't care about return, just catch exceptions
            } catch (ExecutionException e) {
                log.error(
                    "Exception while attempting to create missing jobs for a repo: ",
                    e);
            } catch (InterruptedException e) {
                log.error("Interrupted: this shouldn't happen", e);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        // on a plugin upgrade or whatever, we want to make sure all tasks get executed.
        es.shutdown();
        // This might be stupid.  I'm aware.  But the glorious unit tests say I need it.
        while (!es.isTerminated()) {
            Thread.sleep(50);
        }
    }
}
