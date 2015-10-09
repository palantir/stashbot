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
package com.palantir.stash.stashbot.servlet;

import java.io.IOException;
import java.io.Writer;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatus.State;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.internal.build.InternalBuildStatus;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.SecurityService;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;
import com.palantir.stash.stashbot.util.BuildStatusAddOperation;
import com.palantir.stash.stashbot.util.PullRequestCommentAddOperation;
import com.palantir.stash.stashbot.util.PullRequestFetcherOperation;
import com.palantir.stash.stashbot.util.RepoIdFetcherOperation;

public class BuildSuccessReportingServlet extends HttpServlet {

	/**
	 * Handle information about build success / failure for a given hash and/or
	 * pull request
	 * 
	 * URL is of the form
	 * BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD
	 * /PULLREQUEST_ID] <br/>
	 * <br/>
	 * REPO_ID is the stash internal ID of the repository<br/>
	 * TYPE is "verification" or "release" STATE is "successful", "failed", or
	 * "inprogress"<br/>
	 * BUILD_NUMBER is the jenkins build number BUILD_HEAD is the sha1 hash that
	 * is being built<br/>
	 * MERGE_HEAD/PULLREQUEST_ID is the (optional) sha1 hash that was merged
	 * into, along with the pull request ID<br/>
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final Logger log;

	private final ConfigurationPersistenceService configurationPersistanceManager;
	private final RepositoryService repositoryService;
	private final BuildStatusService buildStatusService;
	private final PullRequestService pullRequestService;
	private final StashbotUrlBuilder ub;
	private final JobTemplateManager jtm;
	private final SecurityService ss;
	private final UserService us;

	/**
	 * @deprecated Use
	 *             {@link #BuildSuccessReportingServlet(ConfigurationPersistenceImpl,RepositoryService,BuildStatusService,PullRequestService,StashbotUrlBuilder,JobTemplateManager,SecurityService,PluginLoggerFactory)}
	 *             instead
	 */
	@Deprecated
	public BuildSuccessReportingServlet(
			ConfigurationPersistenceService configurationPersistenceManager,
			RepositoryService repositoryService,
			BuildStatusService buildStatusService,
			PullRequestService pullRequestService, StashbotUrlBuilder ub,
			JobTemplateManager jtm, PluginLoggerFactory lf) {
		this(configurationPersistenceManager, repositoryService,
				buildStatusService, pullRequestService, ub, jtm, null, null, lf);

	}

	public BuildSuccessReportingServlet(
			ConfigurationPersistenceService configurationPersistenceManager,
			RepositoryService repositoryService,
			BuildStatusService buildStatusService,
			PullRequestService pullRequestService, StashbotUrlBuilder ub,
			JobTemplateManager jtm, SecurityService ss, UserService us,
			PluginLoggerFactory lf) {
		this.configurationPersistanceManager = configurationPersistenceManager;
		this.repositoryService = repositoryService;
		this.buildStatusService = buildStatusService;
		this.pullRequestService = pullRequestService;
		this.ub = ub;
		this.jtm = jtm;
		this.log = lf.getLoggerForThis(this);
		this.ss = ss;
		this.us = us;
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		try {
			// Look at JenkinsManager class if you change this:
			// final two arguments could be empty...
			final String URL_FORMAT = "BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]";
			final String pathInfo = req.getPathInfo();

			final String[] parts = pathInfo.split("/");

			if (parts.length != 6 && parts.length != 8) {
				throw new IllegalArgumentException("The format of the URL is "
						+ URL_FORMAT);
			}
			final int repoId;
			final RepositoryConfiguration rc;
			try {
				repoId = Integer.valueOf(parts[1]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("The format of the URL is "
						+ URL_FORMAT, e);
			}

			// This is necessary if we want unauthenticated users to be able to
			// call this. *sigh*
			RepoIdFetcherOperation getRepoId = new RepoIdFetcherOperation(
					repositoryService, repoId);
			ss.withPermission(Permission.REPO_READ, "BUILD SUCCESS REPORT")
					.call(getRepoId);
			final Repository repo = getRepoId.getRepo();

			rc = configurationPersistanceManager
					.getRepositoryConfigurationForRepository(repo);
			if (repo == null) {
				throw new IllegalArgumentException(
						"Unable to get a repository for id " + repoId);
			}

			JobTemplate jt = jtm.fromString(rc, parts[2].toLowerCase());
			if (jt == null) {
				throw new IllegalArgumentException(
						"Unable to get a valid JobTemplate from " + parts[2]);
			}

			final State state = BuildStatus.State.fromString(parts[3]);
			if (state == null) {
				throw new IllegalArgumentException(
						"The state must be 'successful', 'failed', or 'inprogress'");
			}

			final long buildNumber;
			try {
				buildNumber = Long.parseLong(parts[4]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Unable to parse build number", e);
			}

			// TODO: ensure this hash actually exists?
			final String buildHead = parts[5];
			final String mergeHead;
			final long pullRequestId;
			final PullRequest pullRequest;

			final String retUrl;
			if (parts.length == 8 && !parts[6].isEmpty() && !parts[7].isEmpty()) {
				mergeHead = parts[6];
				try {
					// This is a pull request, so add a comment
					pullRequestId = Long.parseLong(parts[7]);
					PullRequestFetcherOperation prfo = new PullRequestFetcherOperation(
							pullRequestService, repoId, pullRequestId);
					ss.withPermission(Permission.REPO_READ,
							"BUILD SUCCESS REPORT").call(prfo);
					pullRequest = prfo.getPullRequest();

					if (pullRequest == null) {
						throw new IllegalArgumentException(
								"Unable to find pull request for repo id "
										+ repo.getId().toString() + " pr id "
										+ Long.toString(pullRequestId));
					}
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(
							"Unable to parse pull request id " + parts[7], e);
				}
				retUrl = ub.getJenkinsTriggerUrl(repo, jt.getJobType(),
						buildHead, pullRequest);
			} else {
				mergeHead = null;
				pullRequestId = 0;
				pullRequest = null;
				retUrl = ub.getJenkinsTriggerUrl(repo, jt.getJobType(),
						buildHead, null);
			}

			if (mergeHead == null) {
				BuildStatus bs;
				bs = getSuccessStatus(repo, jt, state, buildNumber, buildHead);
				log.debug("Registering build status for buildHead " + buildHead
						+ " " + bsToString(bs));
				BuildStatusAddOperation bssAdder = new BuildStatusAddOperation(
						buildStatusService, buildHead, bs);
				// Yeah, I know what you are thinking...
				// "Admin permission?  To add a build status?"
				// I tried REPO_WRITE and REPO_ADMIN and neither was enough, but
				// this worked!
				ss.withPermission(Permission.SYS_ADMIN, "BUILD SUCCESS REPORT")
						.call(bssAdder);
				printOutput(req, res);
				return;
			}

			// Update the metadata. We do this before adding the comment so that
			// any listeners consuming
			// comment events will have the updated state.

			// arg order for bools is started, success, override, failed
			if (state.equals(State.SUCCESSFUL)) {
				configurationPersistanceManager.setPullRequestMetadata(
						pullRequest, mergeHead, buildHead, null, true, null,
						false);
			} else if (state.equals(State.INPROGRESS)) {
				configurationPersistanceManager.setPullRequestMetadata(
						pullRequest, mergeHead, buildHead, true, false, null,
						null);
			} else if (state.equals(State.FAILED)) {
				configurationPersistanceManager.setPullRequestMetadata(
						pullRequest, mergeHead, buildHead, null, false, null,
						true);
			}

			// mergeHead is not null *and* pullRequest is not null if we reach
			// here.
			final StringBuffer sb = new StringBuffer();
			final String url = getJenkinsUrl(repo, jt, buildNumber);

			/*
			 * NOTE: mergeHead and buildHead are the reverse of what you might
			 * think, because we have to check out the "toRef" becasue it is the
			 * ref that is guaranteed to be in the correct repo. Nonetheless,
			 * buildHead is the commit that is being merged "into" the target
			 * branch, which is the mergeHead variable here.
			 */
			final int hashLength = 4;
			final String shortMergeHead = mergeHead.substring(0, hashLength);
			final String shortBuildHead = buildHead.substring(0, hashLength);

			final String mergeHeadUrl = ub.buildStashCommitUrl(repo, mergeHead);
			final String buildHeadUrl = ub.buildStashCommitUrl(repo, buildHead);

			final String mergeHeadLink = "[" + shortMergeHead + "]("
					+ mergeHeadUrl + ")";
			final String buildHeadLink = "[" + shortBuildHead + "]("
					+ buildHeadUrl + ")";

			final String consoleUrl = url + "/console";

			sb.append("*[Build #" + buildNumber + "](" + url + ") ");
			sb.append("(merging " + mergeHeadLink + " into " + buildHeadLink
					+ ") ");
			switch (state) {
			case INPROGRESS:
				sb.append("is in progress...*");
				break;
			case SUCCESSFUL:
				sb.append("has **passed &#x2713;**.*");
				break;
			case FAILED:
				sb.append("has* **FAILED &#x2716;**. ");
				sb.append("([*Retrigger this build* &#x27f3;](" + retUrl
						+ ") *or* [*view console output* &#x2630;]("
						+ consoleUrl + ").)");
				break;
			}

			log.debug("Registering comment on pr for buildHead " + buildHead
					+ " mergeHead " + mergeHead);
			// Still make comment so users can see links to build
			PullRequestCommentAddOperation prcao = new PullRequestCommentAddOperation(
					pullRequestService, repo.getId(), pullRequest.getId(),
					sb.toString());

			// So in order to create comments, we have to do it AS some user.
			// ss.doAsUser rather than ss.doWithPermission is the magic sauce
			// here.
			JenkinsServerConfiguration jsc = configurationPersistanceManager
					.getJenkinsServerConfiguration(rc.getJenkinsServerName());
			StashUser user = us.findUserByNameOrEmail(jsc.getStashUsername());
			ss.impersonating(user, "BUILD SUCCESS REPORT").call(prcao);

			printOutput(req, res);
		} catch (SQLException e) {
			throw new RuntimeException("Unable to get configuration", e);
		} catch (Exception e) {
			throw new RuntimeException("Unable to report build status", e);
		}
	}

	private void printOutput(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		res.reset();
		res.setStatus(200);
		res.setContentType("text/plain;charset=UTF-8");
		Writer w = res.getWriter();
		w.append("Status Updated");
		w.close();
	}

	private BuildStatus getSuccessStatus(Repository repo, JobTemplate jt,
			State state, long buildNumber, String buildHead)
			throws SQLException {
        RepositoryConfiguration rc = configurationPersistanceManager
            .getRepositoryConfigurationForRepository(repo);
        JenkinsServerConfiguration jsc = configurationPersistanceManager
            .getJenkinsServerConfiguration(rc.getJenkinsServerName());

		Date now = new Date(java.lang.System.currentTimeMillis());

		DateFormat df = DateFormat.getDateInstance();
		// key will be the jenkins name
        String key = jt.getBuildNameFor(repo, jsc);
		String name = key + " (build " + Long.toString(buildNumber) + ")";
		String description = "Build " + Long.toString(buildNumber) + " "
				+ state.toString() + " at " + df.format(now);
		String url = getJenkinsUrl(repo, jt, buildNumber);
		BuildStatus bs = new InternalBuildStatus(state, name, name, url,
				description, now);
		return bs;
	}

	private String getJenkinsUrl(Repository repo, JobTemplate jt,
			long buildNumber) throws SQLException {
		RepositoryConfiguration rc = configurationPersistanceManager
				.getRepositoryConfigurationForRepository(repo);
		JenkinsServerConfiguration jsc = configurationPersistanceManager
				.getJenkinsServerConfiguration(rc.getJenkinsServerName());
        String key = jt.getBuildNameFor(repo, jsc);
		String url = jsc.getUrlForRepo(repo) + "/job/" + key + "/"
				+ Long.toString(buildNumber);
		return url;
	}

	private static String bsToString(BuildStatus bs) {
		StringBuffer sb = new StringBuffer();
		sb.append("[BuildStatus ");
		sb.append("Name:'" + bs.getKey() + "' ");
		sb.append("Type:'" + bs.getName() + "' ");
		sb.append("State:'" + bs.getState().toString() + "']");
		return sb.toString();
	}
}
