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
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.atlassian.bitbucket.nav.NavBuilder;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestMergeVeto;
import com.atlassian.bitbucket.pull.PullRequestMergeability;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

public class BuildStatusReportingServlet extends HttpServlet {

    /**
     * Surface information about build success / failure for a given pull request
     * 
     * URL is of the form BASE_URL/REPO_ID_OR_SLUG/PULLREQUEST_ID] <br/>
     * 
     * <br/>
     * REPO_ID is the stash internal ID of the repository<br/>
     * PULLREQUEST_ID is the pull request ID<br/>
     * 
     */
    private static final long serialVersionUID = 1L;
    private final Logger log;

    private final RepositoryService rs;
    private final ProjectService ps;
    private final PullRequestService prs;
    private final NavBuilder nb;

    public BuildStatusReportingServlet(RepositoryService rs, ProjectService ps, PullRequestService prs,
        NavBuilder nb, PluginLoggerFactory lf) {
        this.rs = rs;
        this.ps = ps;
        this.prs = prs;
        this.nb = nb;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {
        try {
            // Look at JenkinsManager class if you change this:
            // final two arguments could be empty...
            final String URL_FORMAT =
                "BASE_URL/REPO_ID_OR_SLUG/PULLREQUEST_ID]";
            final String pathInfo = req.getPathInfo();
            final String[] parts = pathInfo.split("/");

            // need at *least* 3 parts to be correct
            if (parts.length < 3) {
                throw new IllegalArgumentException("The format of the URL is "
                    + URL_FORMAT);
            }

            // Last part is always the PR
            String pullRequestPart = parts[parts.length - 1];

            // First part is always empty because string starts with '/', last is pr, the rest is the slug
            String slugOrId = StringUtils.join(Arrays.copyOfRange(parts, 1, parts.length - 1), "/");

            Repository repo;
            try {
                int repoId = Integer.valueOf(slugOrId);
                repo = rs.getById(repoId);
                if (repo == null) {
                    throw new IllegalArgumentException("Unable to find repository for repo id " + repoId);
                }
            } catch (NumberFormatException e) {
                // we have a slug, try to get a repo ID from that
                // slug should look like this: projects/PROJECT_KEY/repos/REPO_SLUG/pull-requests
                String[] newParts = slugOrId.split("/");

                if (newParts.length != 5) {
                    throw new IllegalArgumentException(
                        "The format of the REPO_ID_OR_SLUG is an ID, or projects/PROJECT_KEY/repos/REPO_SLUG/pull-requests");
                }
                Project p = ps.getByKey(newParts[1]);
                if (p == null) {
                    throw new IllegalArgumentException("Unable to find project for project key" + newParts[1]);
                }
                repo = rs.getBySlug(p.getKey(), newParts[3]);
                if (repo == null) {
                    throw new IllegalArgumentException("Unable to find repository for project key" + newParts[1]
                        + " and repo slug " + newParts[3]);
                }
            }

            final long pullRequestId;
            final PullRequest pullRequest;

            try {
                pullRequestId = Long.parseLong(pullRequestPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Unable to parse pull request id " + parts[7], e);
            }
            pullRequest = prs.getById(repo.getId(), pullRequestId);
            if (pullRequest == null) {
                throw new IllegalArgumentException(
                    "Unable to find pull request for repo id "
                        + repo.getId() + " pr id "
                        + pullRequestId);
            }

            PullRequestMergeability canMerge = prs.canMerge(repo.getId(), pullRequestId);

            JSONObject output = new JSONObject();
            output.put("repoId", repo.getId());
            output.put("prId", pullRequestId);
            output.put("url", nb.repo(repo).pullRequest(pullRequest.getId()).buildAbsolute());
            output.put("canMerge", canMerge.canMerge());
            if (!canMerge.canMerge()) {
                JSONArray vetoes = new JSONArray();
                for (PullRequestMergeVeto prmv : canMerge.getVetoes()) {
                    JSONObject prmvjs = new JSONObject();
                    prmvjs.put("summary", prmv.getSummaryMessage());
                    prmvjs.put("details", prmv.getDetailedMessage());
                    vetoes.put(prmvjs);
                }
                // You might expect a conflict would be included in the list of merge blockers.  You'd be mistaken.
                if (canMerge.isConflicted()) {
                    JSONObject prmvjs = new JSONObject();
                    prmvjs.put("summary", "This pull request is unmergeable due to conflicts.");
                    prmvjs.put("details", "You will need to resolve conflicts to be able to merge.");
                    vetoes.put(prmvjs);
                }
                output.put("vetoes", vetoes);
            }

            log.debug("Serving build status: " + output.toString());
            printOutput(output, req, res);
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
