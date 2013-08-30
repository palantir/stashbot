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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatus.State;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.urlbuilder.TriggerBuildUrlBuilder;

public class BuildSuccessReportingServletTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final State SUCCESSFUL = State.SUCCESSFUL;
    private static final State INPROGRESS = State.INPROGRESS;
    private static final State FAILED = State.FAILED;
    private static final long BUILD_NUMBER = 12345L;
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private BuildStatusService bss;
    @Mock
    private PullRequestService prs;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private Repository repo;
    @Mock
    private Project proj;
    @Mock
    private PullRequest pr;
    @Mock
    private PullRequestMetadata prm;
    @Mock
    private TriggerBuildUrlBuilder ub;

    private StringWriter mockWriter;

    private BuildSuccessReportingServlet bsrs;

    private static final String ABSOLUTE_URL = "http://example.com/blah/foo";

    private StashbotLoggerFactory lf = new StashbotLoggerFactory();

    @Before
    public void setUp() throws IOException, SQLException {
        MockitoAnnotations.initMocks(this);

        Mockito.when(cpm.getJenkinsServerConfiguration(Mockito.anyString())).thenReturn(jsc);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(Mockito.any(Repository.class))).thenReturn(rc);
        Mockito.when(jsc.getUrl()).thenReturn(ABSOLUTE_URL);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);
        Mockito.when(repositoryService.getById(REPO_ID)).thenReturn(repo);
        Mockito.when(prs.findById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("projectKey");
        Mockito.when(
            ub.getJenkinsTriggerUrl(Mockito.any(Repository.class), Mockito.any(JenkinsBuildTypes.class),
                Mockito.anyString(), Mockito.anyLong(), Mockito.anyString())).thenReturn(ABSOLUTE_URL);

        mockWriter = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(mockWriter));

        bsrs = new BuildSuccessReportingServlet(cpm, repositoryService, bss, prs, ub, lf);
    }

    @Test
    public void testReportingSuccess() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, SUCCESSFUL, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), SUCCESSFUL);
        Assert.assertTrue(bs.getKey().contains(JenkinsBuildTypes.VERIFICATION.toString()));
        Assert.assertTrue(bs.getName().contains(JenkinsBuildTypes.VERIFICATION.toString()));
    }

    @Test
    public void testReportingInprogress() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, INPROGRESS, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), INPROGRESS);
        Assert.assertTrue(bs.getKey().contains(JenkinsBuildTypes.VERIFICATION.toString()));
        Assert.assertTrue(bs.getName().contains(JenkinsBuildTypes.VERIFICATION.toString()));
    }

    @Test
    public void testReportingFailure() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, FAILED, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), FAILED);
        Assert.assertTrue(bs.getKey().contains(JenkinsBuildTypes.VERIFICATION.toString()));
        Assert.assertTrue(bs.getName().contains(JenkinsBuildTypes.VERIFICATION.toString()));
    }

    @Test
    public void testMergeBuildReportingSuccess() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, SUCCESSFUL, BUILD_NUMBER, HEAD, MERGE_HEAD,
                PULL_REQUEST_ID));

        bsrs.doGet(req, res);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(prs).addComment(Mockito.eq(REPO_ID), Mockito.eq(PULL_REQUEST_ID), stringCaptor.capture());
        Mockito.verify(res).setStatus(200);
        Mockito.verify(cpm).setPullRequestMetadata(pr, true, null);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        String commentText = stringCaptor.getValue();
        Assert.assertTrue(commentText.contains("==SUCCESSFUL=="));
    }

    // path info: "/BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]"
    private String buildPathInfo(int repoId, JenkinsBuildTypes type, State state, long buildNumber, String head,
        String mergeHead, Long pullRequestId) {
        return "/"
            + Integer.toString(repoId) + "/"
            + type.toString() + "/"
            + state.toString() + "/"
            + Long.toString(buildNumber) + "/"
            + head + "/"
            + (mergeHead != null ? mergeHead : "") + "/"
            + (pullRequestId != null ? pullRequestId.toString() : "");
    }
}
