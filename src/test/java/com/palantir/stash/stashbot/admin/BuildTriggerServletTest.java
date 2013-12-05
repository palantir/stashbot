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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class BuildTriggerServletTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private PullRequestService prs;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private JenkinsServerConfiguration jsc;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private Repository repo;
    @Mock
    private PullRequest pr;

    private StringWriter mockWriter;

    private BuildTriggerServlet bsrs;

    private StashbotLoggerFactory lf = new StashbotLoggerFactory();

    @Before
    public void setUp() throws IOException, SQLException {
        MockitoAnnotations.initMocks(this);

        Mockito.when(repositoryService.getById(REPO_ID)).thenReturn(repo);
        Mockito.when(prs.getById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        mockWriter = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(mockWriter));

        bsrs = new BuildTriggerServlet(repositoryService, prs, jenkinsManager, lf);
    }

    @Test
    public void testTriggerCommitVerify() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, HEAD, null, null));

        bsrs.doGet(req, res);

        Mockito.verify(res).setStatus(200);
        Mockito.verify(jenkinsManager).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Build Triggered"));
    }

    @Test
    public void testTriggerPullRequestVerify() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, HEAD, MERGE_HEAD, PULL_REQUEST_ID));

        bsrs.doGet(req, res);

        Mockito.verify(res).setStatus(200);
        Mockito.verify(jenkinsManager).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD, MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Build Triggered"));
    }

    // path info: "/BASE_URL/REPO_ID/TYPE/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]"
    private String buildPathInfo(int repoId, JenkinsBuildTypes type, String head, String mergeHead, Long pullRequestId) {
        return "/"
            + Integer.toString(repoId) + "/"
            + type.toString() + "/"
            + head + "/"
            + (mergeHead != null ? mergeHead : "") + "/"
            + (pullRequestId != null ? pullRequestId.toString() : "");
    }
}
