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
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

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
    // 1 => repoId, 2 => type, 3 => build_head, 4 => mergeHead, 5 =>
    // pullRequestId
    private static final String URL_FORMAT = "BASE_URL/REPO_ID/TYPE/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]";

    private final RepositoryService repositoryService;
    private final PullRequestService pullRequestService;
    private final JobTemplateManager jtm;
    private final ConfigurationPersistenceService cpm;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public BuildTriggerServlet(RepositoryService repositoryService,
        PullRequestService pullRequestService, JobTemplateManager jtm,
        ConfigurationPersistenceService cpm, JenkinsManager jenkinsManager,
        PluginLoggerFactory lf) {
        this.repositoryService = repositoryService;
        this.pullRequestService = pullRequestService;
        this.jenkinsManager = jenkinsManager;
        this.jtm = jtm;
        this.cpm = cpm;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {

        final String pathInfo = req.getPathInfo();
        final String reason = req.getParameter("reason");
        final String[] parts = pathInfo.split("/");

        if (parts.length != 4 && parts.length != 6) {
            throw new IllegalArgumentException("The format of the URL is "
                + URL_FORMAT);
        }
        final int repoId;
        final Repository repo;
        final RepositoryConfiguration rc;
        final JobTemplate jt;
        try {
            repoId = Integer.valueOf(parts[1]);
            repo = repositoryService.getById(repoId);
            if (repo == null) {
                throw new IllegalArgumentException(
                    "Unable to get a repository for id " + repoId);
            }
            rc = cpm.getRepositoryConfigurationForRepository(repo);
            jt = jtm.fromString(rc, parts[2].toLowerCase());
        } catch (SQLException e) {
            throw new IllegalArgumentException("SQLException occured", e);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The format of the URL is "
                + URL_FORMAT, e);
        }

        if (jt == null) {
            throw new IllegalArgumentException(
                "Unable to get a valid JobTemplate from " + parts[2]
                    + " for repository " + repo.toString());
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
                pullRequest = pullRequestService.getById(repo.getId(),
                    Long.parseLong(pullRequestId));
                if (pullRequest == null) {
                    throw new IllegalArgumentException(
                        "Unable to find pull request for repo id "
                            + repo.getId() + " pr id "
                            + pullRequestId);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Unable to parse pull request id " + parts[5], e);
            }
        } else {
            mergeHead = null;
            pullRequestId = null;
            pullRequest = null;
        }

        if (mergeHead == null) {
            log.debug("Triggering build for buildHead " + buildHead);
            try {
                // When triggered this way, we don't know the buildRef, so leave it blank
                jenkinsManager.triggerBuild(repo, jt.getJobType(), buildHead, reason);
                printOutput(req, res);
                return;
            } catch (Exception e) {
                printErrorOutput(req, res, e);
                return;
            }
        }

        // pullRequest is not null if we reach here.
        try {
            jenkinsManager.triggerBuild(repo, jt.getJobType(), pullRequest);
        } catch (Exception e) {
            printErrorOutput(req, res, e);
            return;
        }
        printOutput(req, res);
        return;
    }

    private void printOutput(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
        res.reset();
        res.setStatus(200);
        res.setContentType("text/plain;charset=UTF-8");
        Writer w = res.getWriter();
        w.append("Build Triggered");
        w.close();
    }

    private void printErrorOutput(HttpServletRequest req,
        HttpServletResponse res, Exception e) throws IOException {
        res.reset();
        res.setStatus(500);
        res.setContentType("text/plain;charset=UTF-8");
        Writer w = res.getWriter();
        w.append("Exception thrown during trigger: " + e.getMessage() + "\n");
        w.append("Caused by: " + e.getCause() + "\n");
        w.append("\n\nStacktrace:\n");
        for (StackTraceElement elm : e.getStackTrace()) {
            w.append(elm.toString() + "\n");
        }
        w.close();
    }
}
