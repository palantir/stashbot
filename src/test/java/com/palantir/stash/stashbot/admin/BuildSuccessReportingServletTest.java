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
import java.io.PrintWriter;
import java.io.StringWriter;

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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.bitbucket.build.BuildState;
import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.build.BuildStatusSetRequest;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.EscalatedSecurityContext;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.user.UserService;
import com.atlassian.bitbucket.util.Operation;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.mocks.MockJobTemplateFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.servlet.BuildSuccessReportingServlet;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class BuildSuccessReportingServletTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final BuildState SUCCESSFUL = BuildState.SUCCESSFUL;
    private static final BuildState INPROGRESS = BuildState.INPROGRESS;
    private static final BuildState FAILED = BuildState.FAILED;
    private static final long BUILD_NUMBER = 12345L;
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private ConfigurationPersistenceImpl cpm;
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
    private SecurityService ss;
    @Mock
    private UserService us;

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
    private StashbotUrlBuilder ub;
    @Mock
    private JobTemplateManager jtm;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private EscalatedSecurityContext esc;

    private StringWriter mockWriter;

    private BuildSuccessReportingServlet bsrs;

    private static final String ABSOLUTE_URL = "http://example.com/blah/foo";

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    private MockJobTemplateFactory jtf;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);

        Mockito.when(cpm.getJenkinsServerConfiguration(Mockito.anyString()))
            .thenReturn(jsc);
        Mockito.when(
            cpm.getRepositoryConfigurationForRepository(Mockito
                .any(Repository.class))).thenReturn(rc);
        Mockito.when(jsc.getUrl()).thenReturn(ABSOLUTE_URL);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);
        Mockito.when(repositoryService.getById(REPO_ID)).thenReturn(repo);
        Mockito.when(prs.getById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(pr.getToRef()).thenReturn(toRef);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("projectKey");
        Mockito.when(
            ub.getJenkinsTriggerUrl(Mockito.any(Repository.class),
                Mockito.any(JobType.class), Mockito.anyString(),
                Mockito.any(PullRequest.class))).thenReturn(
            ABSOLUTE_URL);
        Mockito.when(ub.getJenkinsBuildUrl(Mockito.any(Repository.class), Mockito.any(JobTemplate.class), Mockito.anyLong())).thenReturn(ABSOLUTE_URL);
        
        Mockito.when(ss.impersonating(Mockito.any(ApplicationUser.class), Mockito.anyString())).thenReturn(esc);
        Mockito.when(ss.withPermission(Mockito.any(Permission.class), Mockito.anyString())).thenReturn(esc);

        jtf = new MockJobTemplateFactory(jtm);
        jtf.generateDefaultsForRepo(repo, rc);

        // Let's use the actual operation classes here for test coverage
        Answer<Void> frobber = new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Operation<Void, Exception> op = (Operation<Void, Exception>) invocation.getArguments()[0];

                op.perform();
                return null;
            }
        };
        Mockito.when(esc.call(Mockito.<Operation<Void, Exception>> any())).thenAnswer(frobber);

        mockWriter = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(mockWriter));

        bsrs = new BuildSuccessReportingServlet(cpm, repositoryService, bss,
            prs, ub, jtm, ss, us, lf);
    }

    @Test
    public void testReportingSuccess() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JobType.VERIFY_COMMIT, SUCCESSFUL,
                BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatusSetRequest> buildStatusCaptor = ArgumentCaptor
            .forClass(BuildStatusSetRequest.class);

        Mockito.verify(bss).set(buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatusSetRequest bssr = buildStatusCaptor.getValue();
        Assert.assertEquals(bssr.getState(), SUCCESSFUL);
        Assert.assertTrue(bssr.getKey()
            .contains(JobType.VERIFY_COMMIT.toString()));
        Assert.assertTrue(bssr.getName().contains(
            JobType.VERIFY_COMMIT.toString()));
    }

    @Test
    public void testReportingInprogress() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JobType.VERIFY_COMMIT, INPROGRESS,
                BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatusSetRequest> buildStatusCaptor = ArgumentCaptor
            .forClass(BuildStatusSetRequest.class);

        Mockito.verify(bss).set(buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatusSetRequest bssr = buildStatusCaptor.getValue();
        Assert.assertEquals(bssr.getState(), INPROGRESS);
        Assert.assertTrue(bssr.getKey()
            .contains(JobType.VERIFY_COMMIT.toString()));
        Assert.assertTrue(bssr.getName().contains(
            JobType.VERIFY_COMMIT.toString()));
    }

    @Test
    public void testReportingFailure() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JobType.VERIFY_COMMIT, FAILED,
                BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatusSetRequest> buildStatusCaptor = ArgumentCaptor
            .forClass(BuildStatusSetRequest.class);

        Mockito.verify(bss).set(buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatusSetRequest bssr = buildStatusCaptor.getValue();
        Assert.assertEquals(bssr.getState(), FAILED);
        Assert.assertTrue(bssr.getKey()
            .contains(JobType.VERIFY_COMMIT.toString()));
        Assert.assertTrue(bssr.getName().contains(
            JobType.VERIFY_COMMIT.toString()));
    }

    @Test
    public void testMergeBuildReportingSuccess() throws ServletException,
        IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JobType.VERIFY_COMMIT, SUCCESSFUL,
                BUILD_NUMBER, HEAD, MERGE_HEAD, PULL_REQUEST_ID));

        bsrs.doGet(req, res);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor
            .forClass(String.class);

        Mockito.verify(prs).addComment(Mockito.eq(REPO_ID),
            Mockito.eq(PULL_REQUEST_ID), stringCaptor.capture());
        Mockito.verify(res).setStatus(200);
        Mockito.verify(cpm).setPullRequestMetadata(pr, MERGE_HEAD, HEAD, null, true, null, false);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        String commentText = stringCaptor.getValue();
        Assert.assertTrue(commentText.contains("passed"));
    }

    // path info:
    // "/BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]"
    private String buildPathInfo(int repoId, JobType jt, BuildState state,
        long buildNumber, String head, String mergeHead, Long pullRequestId) {
        return "/" + Integer.toString(repoId) + "/" + jt.toString() + "/"
            + state.toString() + "/" + Long.toString(buildNumber) + "/"
            + head + "/"
            + (mergeHead != null ? mergeHead + "/" : "")
            + (pullRequestId != null ? pullRequestId.toString() : "");
    }
}
