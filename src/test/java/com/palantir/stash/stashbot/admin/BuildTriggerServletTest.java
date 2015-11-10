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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.mocks.MockJobTemplateFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.servlet.BuildTriggerServlet;

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
    private ConfigurationPersistenceService cpm;
    @Mock
    private JobTemplateManager jtm;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private Repository repo;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private PullRequest pr;

    @Mock
    private JobTemplate jt1;
    @Mock
    private JobTemplate jt2;
    @Mock
    private JobTemplate jt3;

    private JobType verifyCommitType;
    private JobType verifyPRType;
    private JobType publishType;

    private StringWriter mockWriter;

    private BuildTriggerServlet bsrs;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    private MockJobTemplateFactory jtf;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        verifyCommitType = JobType.VERIFY_COMMIT;
        verifyPRType = JobType.VERIFY_PR;
        publishType = JobType.PUBLISH;

        Mockito.when(repositoryService.getById(REPO_ID)).thenReturn(repo);
        Mockito.when(prs.getById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);

        Mockito.when(jt1.getJobType()).thenReturn(verifyCommitType);
        Mockito.when(jt2.getJobType()).thenReturn(verifyPRType);
        Mockito.when(jt3.getJobType()).thenReturn(publishType);
        Mockito.when(jtm.getJenkinsJobsForRepository(rc)).thenReturn(ImmutableList.of(jt1, jt2, jt3));
        Mockito.when(jtm.fromString(rc, verifyCommitType.toString())).thenReturn(jt1);
        Mockito.when(jtm.fromString(rc, verifyPRType.toString())).thenReturn(jt2);
        Mockito.when(jtm.fromString(rc, publishType.toString())).thenReturn(jt3);

        mockWriter = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(mockWriter));

        jtf = new MockJobTemplateFactory(jtm);
        jtf.generateDefaultsForRepo(repo, rc);

        bsrs = new BuildTriggerServlet(repositoryService, prs, jtm, cpm, jenkinsManager, lf);
    }

    @Test
    public void testTriggerCommitVerify() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(buildPathInfo(REPO_ID, verifyCommitType, HEAD, null, null));

        bsrs.doGet(req, res);

        Mockito.verify(res).setStatus(200);
        Mockito.verify(jenkinsManager).triggerBuild(repo, verifyCommitType, HEAD, null);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Build Triggered"));
    }

    @Test
    public void testTriggerPullRequestVerify() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, verifyPRType, HEAD, MERGE_HEAD, PULL_REQUEST_ID));

        bsrs.doGet(req, res);

        Mockito.verify(res).setStatus(200);
        Mockito.verify(jenkinsManager).triggerBuild(repo, verifyPRType, pr);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Build Triggered"));
    }

    @Test
    public void testTriggerPublish() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(buildPathInfo(REPO_ID, publishType, HEAD, null, null));

        bsrs.doGet(req, res);

        Mockito.verify(res).setStatus(200);
        Mockito.verify(jenkinsManager).triggerBuild(repo, publishType, HEAD, null);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Build Triggered"));
    }

    // path info: "/BASE_URL/REPO_ID/TYPE/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]"
    private String
        buildPathInfo(int repoId, JobType jobType, String head, String mergeHead, Long pullRequestId) {
        return "/"
            + Integer.toString(repoId) + "/"
            + jobType.toString() + "/"
            + head + "/"
            + (mergeHead != null ? mergeHead : "") + "/"
            + (pullRequestId != null ? pullRequestId.toString() : "");
    }
}
