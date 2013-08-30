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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class BuildTriggerServlet extends HttpServlet {

    /**
     * This servlet provides rest API to retrigger builds easily.
     * 
     * URL is of the form BASE_URL/REPO_ID/TYPE/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID] <br/>
     * <br/>
     * REPO_ID is the stash internal ID of the repository<br/>
     * TYPE is "verification" or "release"<br/>
     * MERGE_HEAD/PULLREQUEST_ID is the (optional) sha1 hash that was merged into, along with the pull request ID<br/>
     * 
     */
    private static final long serialVersionUID = 1L;
    // 1 => repoId, 2 => type, 3 => build_head, 4 => mergeHead, 5 => pullRequestId
    private static final String URL_FORMAT = "BASE_URL/REPO_ID/TYPE/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]";

    private final RepositoryService repositoryService;
    private final PullRequestService pullRequestService;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public BuildTriggerServlet(RepositoryService repositoryService, PullRequestService pullRequestService,
        JenkinsManager jenkinsManager, StashbotLoggerFactory lf) {
        this.repositoryService = repositoryService;
        this.pullRequestService = pullRequestService;
        this.jenkinsManager = jenkinsManager;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        final String pathInfo = req.getPathInfo();
        final String[] parts = pathInfo.split("/");

        if (parts.length != 4 && parts.length != 6) {
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

        // TODO: ensure this hash actually exists?
        final String buildHead = parts[3];
        final String mergeHead;
        final String pullRequestId;
        final PullRequest pullRequest;

        if (parts.length == 6 && !parts[4].isEmpty() && !parts[5].isEmpty()) {
            mergeHead = parts[4];
            try {
                pullRequestId = parts[5];
                pullRequest = pullRequestService.findById(repo.getId(), Long.parseLong(pullRequestId));
                if (pullRequest == null) {
                    throw new IllegalArgumentException("Unable to find pull request for repo id "
                        + repo.getId().toString() + " pr id " + pullRequestId);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse pull request id " + parts[5], e);
            }
        } else {
            mergeHead = null;
            pullRequestId = null;
            pullRequest = null;
        }

        if (mergeHead == null) {
            log.debug("Triggering build for buildHead " + buildHead);
            jenkinsManager.triggerBuild(repo, type, buildHead);
            printOutput(req, res);
            return;
        }

        // mergeHead is not null *and* pullRequest is not null if we reach here.
        jenkinsManager.triggerBuild(repo, type, buildHead, mergeHead, pullRequestId);
        printOutput(req, res);
        return;
    }

    private void printOutput(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.reset();
        res.setStatus(200);
        res.setContentType("text/plain;charset=UTF-8");
        Writer w = res.getWriter();
        w.append("Build Triggered");
        w.close();
    }
}
