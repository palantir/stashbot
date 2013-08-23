//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.admin;

import java.io.IOException;
import java.io.Writer;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatus.State;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.internal.build.InternalBuildStatus;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.urlbuilder.TriggerBuildUrlBuilder;

public class BuildSuccessReportingServlet extends HttpServlet {

    /**
     * Handle information about build success / failure for a given hash and/or pull request
     * 
     * URL is of the form BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID] <br/>
     * <br/>
     * REPO_ID is the stash internal ID of the repository<br/>
     * TYPE is "verification" or "release" STATE is "successful", "failed", or "inprogress"<br/>
     * BUILD_NUMBER is the jenkins build number BUILD_HEAD is the sha1 hash that is being built<br/>
     * MERGE_HEAD/PULLREQUEST_ID is the (optional) sha1 hash that was merged into, along with the pull request ID<br/>
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(BuildSuccessReportingServlet.class.toString());

    private final ConfigurationPersistenceManager configurationPersistanceManager;
    private final RepositoryService repositoryService;
    private final BuildStatusService buildStatusService;
    private final PullRequestService pullRequestService;
    private final TriggerBuildUrlBuilder ub;

    // private final PullRequestCommentService pullRequestCommentService;

    public BuildSuccessReportingServlet(ConfigurationPersistenceManager configurationPersistenceManager,
        RepositoryService repositoryService, BuildStatusService buildStatusService,
        PullRequestService pullRequestService, TriggerBuildUrlBuilder ub) {
        this.configurationPersistanceManager = configurationPersistenceManager;
        this.repositoryService = repositoryService;
        this.buildStatusService = buildStatusService;
        this.pullRequestService = pullRequestService;
        this.ub = ub;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        try {
            // Look at JenkinsManager class if you change this:
            // final two arguments could be empty...
            final String URL_FORMAT = "BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]";
            final String pathInfo = req.getPathInfo();

            final String[] parts = pathInfo.split("/");

            if (parts.length != 6 && parts.length != 8) {
                throw new IllegalArgumentException("The format of the URL is " + URL_FORMAT);
            }
            final int repoId;
            final Repository repo;
            try {
                repoId = Integer.valueOf(parts[1]);
                repo = repositoryService.getById(repoId);
                if (repo == null) {
                    throw new IllegalArgumentException("Unable to get a repository for id " + repoId);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The format of the URL is " + URL_FORMAT, e);
            }

            final JenkinsBuildTypes type = JenkinsBuildTypes.fromString(parts[2].toUpperCase());
            if (type == null) {
                throw new IllegalArgumentException("Unable to get a valid JenkinsBuildType from " + parts[2]);
            }

            final State state = BuildStatus.State.fromString(parts[3]);
            if (state == null) {
                throw new IllegalArgumentException("The state must be 'successful', 'failed', or 'inprogress'");
            }

            final long buildNumber;
            try {
                buildNumber = Long.parseLong(parts[4]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse build number", e);
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
                    pullRequest = pullRequestService.findById(repo.getId(), pullRequestId);
                    if (pullRequest == null) {
                        throw new IllegalArgumentException("Unable to find pull request for repo id "
                            + repo.getId().toString() + " pr id " + Long.toString(pullRequestId));
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Unable to parse pull request id " + parts[7], e);
                }
                retUrl = ub.getJenkinsTriggerUrl(repo, type, buildHead, pullRequestId, mergeHead);
            } else {
                mergeHead = null;
                pullRequestId = 0;
                pullRequest = null;
                retUrl = ub.getJenkinsTriggerUrl(repo, type, buildHead, null, null);
            }

            if (mergeHead == null) {
                BuildStatus bs;
                bs = getSuccessStatus(repo, type, state, buildNumber, buildHead);
                log.debug("Registering build status for buildHead " + buildHead + " " + bsToString(bs));
                buildStatusService.add(buildHead, bs);
                printOutput(req, res);
                return;
            }

            // mergeHead is not null *and* pullRequest is not null if we reach here.
            final StringBuffer sb = new StringBuffer();
            final String url = getJenkinsUrl(repo, type, buildNumber);

            sb.append("Jenkins Build now has status ");
            sb.append("==" + state.toString() + "==");
            sb.append(" for hash " + buildHead);
            sb.append(" merged into head " + mergeHead);
            sb.append(" <a href=\"" + url + "\">Link</a>");
            sb.append(" (<a href=\"" + retUrl + "\">Retrigger</a>)");

            log.debug("Registering comment on pr for buildHead " + buildHead + " mergeHead " + mergeHead);
            // Still make comment so users can see links to build
            pullRequestService.addComment(repo.getId(), pullRequest.getId(), sb.toString());
            // but also update metadata

            if (state.equals(State.SUCCESSFUL)) {
                configurationPersistanceManager.setPullRequestMetadata(pullRequest, true, null);
            } else {
                configurationPersistanceManager.setPullRequestMetadata(pullRequest, false, null);
            }

            printOutput(req, res);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get configuration", e);
        }
    }

    private void printOutput(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.reset();
        res.setStatus(200);
        res.setContentType("text/plain;charset=UTF-8");
        Writer w = res.getWriter();
        w.append("Status Updated");
        w.close();
    }

    private BuildStatus getSuccessStatus(Repository repo, JenkinsBuildTypes type, State state, long buildNumber,
        String buildHead) throws SQLException {
        Date now = new Date(java.lang.System.currentTimeMillis());

        DateFormat df = DateFormat.getDateInstance();
        // key will be the jenkins name
        String key = type.getBuildNameFor(repo);
        String name = key + " (build " + Long.toString(buildNumber) + ")";
        String description = "Build " + Long.toString(buildNumber) + " " + state.toString() + " at " + df.format(now);
        String url = getJenkinsUrl(repo, type, buildNumber);
        BuildStatus bs = new InternalBuildStatus(state, name, name, url, description, now);
        return bs;
    }

    private String getJenkinsUrl(Repository repo, JenkinsBuildTypes type, long buildNumber) throws SQLException {
        RepositoryConfiguration rc = configurationPersistanceManager.getRepositoryConfigurationForRepository(repo);
        JenkinsServerConfiguration jsc =
            configurationPersistanceManager.getJenkinsServerConfiguration(rc.getJenkinsServerName());
        String key = type.getBuildNameFor(repo);
        String url = jsc.getUrl() + "/job/" + key + "/" + Long.toString(buildNumber);
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
