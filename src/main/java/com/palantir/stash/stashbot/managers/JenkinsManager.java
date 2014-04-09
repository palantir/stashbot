// Copyright 2013 Palantir Technologies
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
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.http.client.HttpResponseException;
import org.slf4j.Logger;

import com.atlassian.stash.hook.repository.RepositoryHookService;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.ssh.api.SshCloneUrlResolver;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsManager {

    // NOTE: this is the key used in the atlassian-plugin.xml
    private static final String TRIGGER_JENKINS_BUILD_HOOK_KEY = "com.palantir.stash.stashbot:triggerJenkinsBuildHook";

    private final ConfigurationPersistenceManager cpm;
    private final JobTemplateManager jtm;
    private final JenkinsJobXmlFormatter xmlFormatter;
    private final JenkinsClientManager jenkinsClientManager;
    private final RepositoryService repositoryService;
    private final RepositoryHookService rhs;
    private final StashbotUrlBuilder sub;
    private final Logger log;
    private final StashbotLoggerFactory lf;
    private final SshCloneUrlResolver sshCloneUrlResolver;

    public JenkinsManager(RepositoryService repositoryService, RepositoryHookService rhs,
        ConfigurationPersistenceManager cpm, JobTemplateManager jtm, JenkinsJobXmlFormatter xmlFormatter,
        JenkinsClientManager jenkisnClientManager, StashbotUrlBuilder sub, StashbotLoggerFactory lf,
        SshCloneUrlResolver sshCloneUrlResolver) {
        this.repositoryService = repositoryService;
        this.rhs = rhs;
        this.cpm = cpm;
        this.jtm = jtm;
        this.xmlFormatter = xmlFormatter;
        this.jenkinsClientManager = jenkisnClientManager;
        this.sub = sub;
        this.lf = lf;
        this.log = lf.getLoggerForThis(this);
        this.sshCloneUrlResolver = sshCloneUrlResolver;
    }

    public void updateRepo(Repository repo) {
        try {
            Callable<Void> visit = new UpdateAllRepositoryVisitor(rhs,
                jenkinsClientManager, jtm, cpm, repo, lf);
            visit.call();
        } catch (Exception e) {
            log.error(
                "Exception while attempting to create missing jobs for a repo: ",
                e);
        }
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

            // If we try to create a job which already exists, we still get a
            // 200... so we should check first to make
            // sure it doesn't already exist
            Map<String, Job> jobMap = jenkinsServer.getJobs();

            if (jobMap.containsKey(jobName)) {
                throw new IllegalArgumentException("Job " + jobName
                    + " already exists");
            }

            String xml = xmlFormatter.generateJobXml(jobTemplate, repo);

            log.trace("Sending XML to jenkins to create job: " + xml);
            jenkinsServer.createJob(jobName, xml);
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

            // If we try to create a job which already exists, we still get a
            // 200... so we should check first to make
            // sure it doesn't already exist
            Map<String, Job> jobMap = jenkinsServer.getJobs();

            String xml = xmlFormatter.generateJobXml(jobTemplate, repo);

            if (jobMap.containsKey(jobName)) {
                log.trace("Sending XML to jenkins to update job: " + xml);
                jenkinsServer.updateJob(jobName, xml);
                return;
            }

            log.trace("Sending XML to jenkins to create job: " + xml);
            jenkinsServer.createJob(jobName, xml);
        } catch (IOException e) {
            // TODO: something other than just rethrow?
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void triggerBuild(Repository repo, JobType jobType,
        String hashToBuild) {
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
            Map<String, Job> jobMap = js.getJobs();
            String key = jt.getBuildNameFor(repo);

            if (!jobMap.containsKey(key)) {
                throw new RuntimeException("Build doesn't exist: " + key);
            }

            Builder<String, String> builder = ImmutableMap.builder();
            builder.put("buildHead", hashToBuild);
            builder.put("repoId", repo.getId().toString());

            jobMap.get(key).build(builder.build());
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

    public void triggerBuild(Repository repo, JobType jobType,
        PullRequest pullRequest) {

        try {
            String pullRequestId = pullRequest.getId().toString();
            String hashToBuild = pullRequest.getToRef().getLatestChangeset();

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
            Map<String, Job> jobMap = js.getJobs();
            String key = jt.getBuildNameFor(repo);

            if (!jobMap.containsKey(key)) {
                throw new RuntimeException("Build doesn't exist: " + key);
            }

            Builder<String, String> builder = ImmutableMap.builder();
            builder.put("repoId", repo.getId().toString());
            if (pullRequest != null) {
                log.debug("Determined pullRequestId " + pullRequestId);
                builder.put("pullRequestId", pullRequestId);
                // toRef is always present in the repo
                builder.put("buildHead", pullRequest.getToRef()
                    .getLatestChangeset().toString());
                // fromRef may be in a different repo
                builder.put("mergeRef", pullRequest.getFromRef().getDisplayId());
                if( rc.getUseSsh().booleanValue() ) {
                    builder.put("mergeRefUrl", sshCloneUrlResolver.getCloneUrl(repo));
                }
                else {
                    builder.put("mergeRefUrl", sub.buildCloneUrl(repo, jsc));
                }
                builder.put("mergeHead", pullRequest.getFromRef()
                    .getLatestChangeset().toString());
            }

            jobMap.get(key).build(builder.build());
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

        private final RepositoryHookService rhs;
        private final JenkinsClientManager jcm;
        private final JobTemplateManager jtm;
        private final ConfigurationPersistenceManager cpm;
        private final Repository r;
        private final Logger log;

        public CreateMissingRepositoryVisitor(RepositoryHookService rhs,
            JenkinsClientManager jcm, JobTemplateManager jtm,
            ConfigurationPersistenceManager cpm, Repository r,
            StashbotLoggerFactory lf) {
            this.rhs = rhs;
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

            // Ensure hook is enabled
            try {
                rhs.enable(r, TRIGGER_JENKINS_BUILD_HOOK_KEY);
            } catch (Exception e) {
                log.error("Exception thrown while trying to enable hook", e);
            }

            // make sure jobs exist
            List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
            JenkinsServer js = jcm.getJenkinsServer(jsc, rc);
            Map<String, Job> jobs = js.getJobs();

            for (JobTemplate template : templates) {
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
                    rhs, jenkinsClientManager, jtm, cpm, r, lf));
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

        private final RepositoryHookService rhs;
        private final JenkinsClientManager jcm;
        private final JobTemplateManager jtm;
        private final ConfigurationPersistenceManager cpm;
        private final Repository r;
        private final Logger log;

        public UpdateAllRepositoryVisitor(RepositoryHookService rhs,
            JenkinsClientManager jcm, JobTemplateManager jtm,
            ConfigurationPersistenceManager cpm, Repository r,
            StashbotLoggerFactory lf) {
            this.rhs = rhs;
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

            // Ensure hook is enabled
            try {
                rhs.enable(r, TRIGGER_JENKINS_BUILD_HOOK_KEY);
            } catch (Exception e) {
                log.error("Exception thrown while trying to enable hook", e);
            }

            // make sure jobs are up to date
            List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
            JenkinsServer js = jcm.getJenkinsServer(jsc, rc);
            Map<String, Job> jobs = js.getJobs();
            for (JobTemplate jobTemplate : templates) {
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
                Future<Void> f = es.submit(new UpdateAllRepositoryVisitor(rhs,
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
}
