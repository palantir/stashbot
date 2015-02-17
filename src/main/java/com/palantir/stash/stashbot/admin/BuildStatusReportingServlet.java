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
package com.palantir.stash.stashbot.admin;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestMergeVeto;
import com.atlassian.stash.pull.PullRequestMergeability;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.SecurityService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.util.PullRequestFetcherOperation;
import com.palantir.stash.stashbot.util.RepoIdFetcherOperation;

public class BuildStatusReportingServlet extends HttpServlet {

    /**
     * Surface information about build success / failure for a given pull request
     * 
     * URL is of the form BASE_URL/REPO_ID/PULLREQUEST_ID] <br/>
     * <br/>
     * REPO_ID is the stash internal ID of the repository<br/>
     * PULLREQUEST_ID is the pull request ID<br/>
     * 
     */
    private static final long serialVersionUID = 1L;
    private final Logger log;

    private final RepositoryService rs;
    private final PullRequestService prs;
    private final SecurityService ss;

    public BuildStatusReportingServlet(RepositoryService rs, PullRequestService prs, SecurityService ss,
        PluginLoggerFactory lf) {
        this.rs = rs;
        this.prs = prs;
        this.log = lf.getLoggerForThis(this);
        this.ss = ss;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {
        try {
            // Look at JenkinsManager class if you change this:
            // final two arguments could be empty...
            final String URL_FORMAT =
                "BASE_URL/REPO_ID/PULLREQUEST_ID]";
            final String pathInfo = req.getPathInfo();

            final String[] parts = pathInfo.split("/");

            //printOutput("parts(" + parts.length + "): '" + parts[0] + "', '" + parts[1] + "', '" + parts[2] + "'", req, res);
            if (parts.length != 3) {
                throw new IllegalArgumentException("The format of the URL is "
                    + URL_FORMAT);
            }

            final int repoId;
            try {
                repoId = Integer.valueOf(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The format of the URL is "
                    + URL_FORMAT, e);
            }

            // This is necessary if we want unauthenticated users to be able to call this.  *sigh*
            RepoIdFetcherOperation getRepoId = new RepoIdFetcherOperation(rs, repoId);
            ss.withPermission(Permission.REPO_READ, "BUILD SUCCESS REPORT").call(getRepoId);
            final Repository repo = getRepoId.getRepo();

            if (repo == null) {
                throw new IllegalArgumentException(
                    "Unable to get a repository for id " + repoId);
            }

            final long pullRequestId;
            final PullRequest pullRequest;

            try {
                pullRequestId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Unable to parse pull request id " + parts[7], e);
            }
            PullRequestFetcherOperation prfo =
                new PullRequestFetcherOperation(prs, repoId, pullRequestId);
            ss.withPermission(Permission.REPO_READ, "BUILD SUCCESS REPORT").call(prfo);
            pullRequest = prfo.getPullRequest();
            if (pullRequest == null) {
                throw new IllegalArgumentException(
                    "Unable to find pull request for repo id "
                        + repo.getId().toString() + " pr id "
                        + Long.toString(pullRequestId));
            }

            PullRequestMergeability canMerge = prs.canMerge(repoId, pullRequestId);

            JSONObject output = new JSONObject();
            output.put("repoId", repoId);
            output.put("prId", pullRequestId);
            output.put("canMerge", canMerge.canMerge());
            if (!canMerge.canMerge()) {
                JSONArray vetoes = new JSONArray();
                for (PullRequestMergeVeto prmv : canMerge.getVetos()) {
                    JSONObject prmvjs = new JSONObject();
                    prmvjs.put("summary", prmv.getSummaryMessage());
                    prmvjs.put("details", prmv.getDetailedMessage());
                    vetoes.put(prmvjs);
                }
                output.put("vetoes", vetoes);
            }

            log.debug("Serving build status: " + output.toString());
            printOutput(output, req, res);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get configuration", e);
        } catch (Exception e) {
            res.reset();
            res.setStatus(500);
            res.setContentType("application/json");
            Writer w = res.getWriter();
            try {
                w.append(new JSONObject().put("error", e.getMessage()).toString());
            } catch (JSONException e1) {
                throw new RuntimeException("Errorception!", e1);
            }
            w.close();
        }
    }

    private void printOutput(JSONObject output, HttpServletRequest req, HttpServletResponse res)
        throws IOException {
        res.reset();
        res.setStatus(200);
        res.setContentType("application/json;charset=UTF-8");
        Writer w = res.getWriter();
        try {
            w.append(output.toString(4));
        } catch (JSONException e) {
            w.append(output.toString());
        }
        w.close();
    }

}
