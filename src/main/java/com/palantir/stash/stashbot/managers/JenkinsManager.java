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
import org.springframework.beans.factory.DisposableBean;

import com.atlassian.sal.api.user.UserManager;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.SecurityService;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserService;
import com.atlassian.stash.util.Operation;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
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

	public JenkinsManager(RepositoryService repositoryService,
			ConfigurationPersistenceService cpm, JobTemplateManager jtm,
			JenkinsJobXmlFormatter xmlFormatter,
			JenkinsClientManager jenkisnClientManager, StashbotUrlBuilder sub,
			PluginLoggerFactory lf, SecurityService ss, UserService us,
			UserManager um) {
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

	public void createJob(Repository repo, JobTemplate jobTemplate) {
		try {
			final RepositoryConfiguration rc = cpm
					.getRepositoryConfigurationForRepository(repo);
			final JenkinsServerConfiguration jsc = cpm
					.getJenkinsServerConfiguration(rc.getJenkinsServerName());
			final JenkinsServer jenkinsServer = jenkinsClientManager
					.getJenkinsServer(jsc, rc, repo);
            final String jobName = jobTemplate.getBuildNameFor(repo, jsc);

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
	 * This method IGNORES the current job XML, and regenerates it from scratch,
	 * and posts it. If any changes were made to the job directly via jenkins
	 * UI, this will overwrite those changes.
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
					.getJenkinsServer(jsc, rc, repo);
            final String jobName = jobTemplate.getBuildNameFor(repo, jsc);

			// If we try to create a job which already exists, we still get a
			// 200... so we should check first to make
			// sure it doesn't already exist
			Map<String, Job> jobMap = jenkinsServer.getJobs();

			String xml = xmlFormatter.generateJobXml(jobTemplate, repo);

			if (jobMap.containsKey(jobName)) {
				if (!rc.getPreserveJenkinsJobConfig()) {
					log.trace("Sending XML to jenkins to update job: " + xml);
					jenkinsServer.updateJob(jobName, xml);
				} else {
					log.trace("Skipping sending XML to jenkins. Repo Config is set to preserve jenkins job config.");
				}
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

	public void triggerBuild(final Repository repo, final JobType jobType,
			final String hashToBuild, final String buildRef) {

		final String username = um.getRemoteUser().getUsername();
		final StashUser su = us.findUserByNameOrEmail(username);

		es.submit(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				// TODO: See if we can do something like
				// StateTransferringExecutorService here instead
				ss.impersonating(
						su,
						"Running as user '" + username
								+ "' in alternate thread asynchronously").call(
						new Operation<Void, Exception>() {

							@Override
							public Void perform() throws Exception {
								synchronousTriggerBuild(repo, jobType,
										hashToBuild, buildRef);
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
		final StashUser su = us.findUserByNameOrEmail(username);

		es.submit(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				// TODO: See if we can do something like
				// StateTransferringExecutorService here instead
				ss.impersonating(
						su,
						"Running as user '" + username
								+ "' in alternate thread asynchronously").call(
						new Operation<Void, Exception>() {

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

            String jenkinsBuildId = jt.getBuildNameFor(repo, jsc);
			String url = jsc.getUrl() + jsc.getUrlForRepo(repo);
			String user = jsc.getUsername();
			String password = jsc.getPassword();

			log.info("Triggering jenkins build id " + jenkinsBuildId
					+ " on hash " + hashToBuild + " (" + user + "@" + url
					+ " pw: " + password.replaceAll(".", "*") + ")");

			final JenkinsServer js = jenkinsClientManager.getJenkinsServer(jsc,
					rc, repo);
			Map<String, Job> jobMap = js.getJobs();
            String key = jt.getBuildNameFor(repo, jsc);

			if (!jobMap.containsKey(key)) {
				List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
				for (JobTemplate jobTemplate : templates) {
					if (key.equals(jobTemplate.getBuildNameFor(repo, jsc))) {
						log.info("Build " + key + "doesn't exist, creating it");
						createJob(repo, jobTemplate);
					}
				}
				jobMap = js.getJobs();
			}

			Builder<String, String> builder = ImmutableMap.builder();
			builder.put("buildHead", hashToBuild);
			builder.put("repoId", repo.getId().toString());
			if (buildRef != null) {
				builder.put("buildRef", buildRef);
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

	public void synchronousTriggerBuild(Repository repo, JobType jobType,
			PullRequest pullRequest) {

		try {
			String pullRequestId = pullRequest.getId().toString();
			String hashToBuild = pullRequest.getToRef().getLatestChangeset();

			RepositoryConfiguration rc = cpm
					.getRepositoryConfigurationForRepository(repo);
			JenkinsServerConfiguration jsc = cpm
					.getJenkinsServerConfiguration(rc.getJenkinsServerName());
			JobTemplate jt = jtm.getJobTemplate(jobType, rc);

            String jenkinsBuildId = jt.getBuildNameFor(repo, jsc);
			String url = jsc.getUrl() + jsc.getUrlForRepo(repo);
			String user = jsc.getUsername();
			String password = jsc.getPassword();

			log.info("Triggering jenkins build id " + jenkinsBuildId
					+ " on hash " + hashToBuild + " (" + user + "@" + url
					+ " pw: " + password.replaceAll(".", "*") + ")");

			final JenkinsServer js = jenkinsClientManager.getJenkinsServer(jsc,
					rc, repo);
			Map<String, Job> jobMap = js.getJobs();
            String key = jt.getBuildNameFor(repo, jsc);

			if (!jobMap.containsKey(key)) {
				List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
				for (JobTemplate jobTemplate : templates) {
					if (key.equals(jobTemplate.getBuildNameFor(repo, jsc))) {
						log.info("Build " + key + "doesn't exist, creating it");
						createJob(repo, jobTemplate);
					}
				}
				jobMap = js.getJobs();
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
				builder.put("mergeRef", pullRequest.getFromRef().getId());
				builder.put("buildRef", pullRequest.getToRef().getId());
				builder.put("mergeRefUrl", sub.buildCloneUrl(pullRequest
						.getFromRef().getRepository(), jsc));
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

		private final JenkinsClientManager jcm;
		private final JobTemplateManager jtm;
		private final ConfigurationPersistenceService cpm;
		private final Repository r;
		private final Logger log;

		public CreateMissingRepositoryVisitor(JenkinsClientManager jcm,
				JobTemplateManager jtm, ConfigurationPersistenceService cpm,
				Repository r, PluginLoggerFactory lf) {
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
			JenkinsServer js = jcm.getJenkinsServer(jsc, rc, this.r);
			Map<String, Job> jobs = js.getJobs();

			for (JobTemplate template : templates) {
                if (!jobs.containsKey(template.getBuildNameFor(r, jsc))) {
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

		public UpdateAllRepositoryVisitor(JenkinsClientManager jcm,
				JobTemplateManager jtm, ConfigurationPersistenceService cpm,
				Repository r, PluginLoggerFactory lf) {
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
			JenkinsServer js = jcm.getJenkinsServer(jsc, rc, this.r);
			Map<String, Job> jobs = js.getJobs();
			for (JobTemplate jobTemplate : templates) {
                if (!jobs.containsKey(jobTemplate.getBuildNameFor(r, jsc))) {
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

	/**
	 * Code to delete jobs in Jenkins that haven't been run recently.
	 *
	 * Jobs that have never been run will be ignored.
	 *
	 * @author jshumway
	 */
	class CleanOldJobsVisitor implements Callable<Void> {

		private final JenkinsClientManager jcm;
		private final JobTemplateManager jtm;
		private final ConfigurationPersistenceService cpm;
		private final Repository r;
		private final Logger log;
		private final long age;
		private final boolean dryRun;

		public CleanOldJobsVisitor(JenkinsClientManager jcm,
								   JobTemplateManager jtm,
								   ConfigurationPersistenceService cpm,
								   Repository r, PluginLoggerFactory lf, int age,
								   boolean dryRun) {
			this.jcm = jcm;
			this.jtm = jtm;
			this.cpm = cpm;
			this.r = r;
			this.log = lf.getLoggerForThis(this);
			this.age = age;
			this.dryRun = dryRun;
		}

		@Override
		public Void call() throws Exception {
			RepositoryConfiguration rc = cpm
					.getRepositoryConfigurationForRepository(r);

			// Do not delete jobs from repositories with 'Preserve Jenkins Job Config'
			// marked, as they cannot be automatically recreated.
			if (rc.getPreserveJenkinsJobConfig())
				return null;

			List<JobTemplate> templates = jtm.getJenkinsJobsForRepository(rc);
			JenkinsServerConfiguration jsc = cpm
					.getJenkinsServerConfiguration(rc.getJenkinsServerName());
			JenkinsServer js = jcm.getJenkinsServer(jsc, rc, r);
			Map<String, Job> jobs = js.getJobs();

			String dryRunMessage = dryRun ? " [DryRun] " : " ";
			for (JobTemplate template: templates) {
				String jobName = template.getBuildNameFor(r, jsc);
				Job job = jobs.get(jobName);

				if (job != null && jobOlderThan(job, 1000 * 60 * 60 * 24 * age)) {
					log.info("Deleting" + dryRunMessage + "job " + job.getName() + " from Jenkins: last " +
							"job occurred over " + age + " days ago");
					if (!dryRun) {
						js.deleteJob(job.getName());
					}
				}
			}

			return null;
		}

		boolean jobOlderThan(Job job, long ageCutoff) {
			try {
				final Build lastBuild = job.details().getLastBuild();

				// Consider jobs with no builds to be newer than |ageCutoff|
				if (lastBuild == null)
					return false;

				final long lastBuildTime = lastBuild.details().getTimestamp();
				final long now = System.currentTimeMillis();
				final long elapsedTime = now - lastBuildTime;

				return elapsedTime > ageCutoff;
			} catch (IOException e) {
				return false;
			}
		}
	}

	static class RepositoryFuture {
		public Repository r;
		public Future<Void> f;
		public RepositoryFuture(Repository repo, Future<Void> future) {
			this.r = repo;
			this.f = future;
		}
	}

	/**
	 * For each repo with a Stashbot configuration that does not have
	 * 'Preserve Jenkins Config' checked, delete job plans from Jenkins
	 * if the job has not been run in more than |age| days.
	 *
	 * If a job has never been run, it will be ignored. It is possible
	 * that such jobs are brand new and should not be deleted.
	 *
	 * @param age the number of days old a job must be to be deleted
     */
	public void cleanOldJobs(int age, boolean dryRun) {

		ExecutorService es = Executors.newCachedThreadPool();
		List<RepositoryFuture> repoFutures = new LinkedList<RepositoryFuture>();

		if (dryRun) {
		    log.info("Starting clean jobs job.  Dry Run.");
		} else {
		    log.info("Starting clean jobs job.");
		}

		PageRequest pageReq = new PageRequestImpl(0, 500);
		Page<? extends Repository> p = repositoryService.findAll(pageReq);

		while (true) {
			for (Repository r : p.getValues()) {
				Future<Void> f = es.submit(new CleanOldJobsVisitor(
						jenkinsClientManager, jtm, cpm, r, lf, age, dryRun));
				repoFutures.add(new RepositoryFuture(r, f));
			}
			if (p.getIsLastPage())
				break;
			pageReq = p.getNextPageRequest();
			p = repositoryService.findAll(pageReq);
		}

		for (RepositoryFuture repoFuture : repoFutures) {
			Repository r = repoFuture.r;
			Future f = repoFuture.f;
			try {
				f.get(); // don't care about return, just catch exceptions
			} catch (ExecutionException e) {
				log.error(
						"Exception while attempting to clean old jobs for repo " +
								r.getName() + ": ",
						e);
			} catch (InterruptedException e) {
				log.error("Interrupted: this shouldn't happen", e);
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		// on a plugin upgrade or whatever, we want to make sure all tasks get
		// executed.
		es.shutdown();
		// This might be stupid. I'm aware. But the glorious unit tests say I
		// need it.
		while (!es.isTerminated()) {
			Thread.sleep(50);
		}
	}

}
