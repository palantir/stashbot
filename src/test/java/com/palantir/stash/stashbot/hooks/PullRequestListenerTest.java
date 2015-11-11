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
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.bitbucket.comment.Comment;
import com.atlassian.bitbucket.event.pull.PullRequestCommentEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Repository;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class PullRequestListenerTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String HEAD_BR = "refs/heads/feature";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final String MERGE_BR = "refs/heads/master";
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private ConfigurationPersistenceImpl cpm;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private RepositoryConfiguration rc;

    private PullRequestListener prl;

    @Mock
    private PullRequestOpenedEvent proEvent;
    @Mock
    private PullRequestRescopedEvent prRescopedEvent;
    @Mock
    private PullRequestCommentEvent prCommentEvent;
    @Mock
    private PullRequest pr;
    @Mock
    private Repository repo;
    @Mock
    private PullRequestRef fromRef;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private Comment comment;
    @Mock
    private PullRequestMetadata prm;
    @Mock
    private PullRequestMetadata prm2;

    private static final String COMMENT_TEXT = "comment text";
    private static final String OVERRIDE_COMMENT_TEXT = "Comment added with ==OVERRIDE==";

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    @Mock
    private JobTemplate jobTemplate;

    @Before
    public void setUp() throws SQLException {

        MockitoAnnotations.initMocks(this);

        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(pr.getId()).thenReturn(new Long(PULL_REQUEST_ID));
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);

        Mockito.when(fromRef.getRepository()).thenReturn(repo);
        Mockito.when(fromRef.getId()).thenReturn(HEAD_BR);
        Mockito.when(fromRef.getLatestCommit()).thenReturn(HEAD);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getId()).thenReturn(MERGE_BR);
        Mockito.when(toRef.getLatestCommit()).thenReturn(MERGE_HEAD);

        Mockito.when(prm.getPullRequestId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(prm.getToSha()).thenReturn(MERGE_HEAD);
        Mockito.when(prm.getFromSha()).thenReturn(HEAD);
        Mockito.when(prm.getSuccess()).thenReturn(false);
        Mockito.when(prm.getOverride()).thenReturn(false);
        Mockito.when(prm.getBuildStarted()).thenReturn(false);
        Mockito.when(prm2.getPullRequestId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(prm2.getToSha()).thenReturn(MERGE_HEAD);
        Mockito.when(prm2.getFromSha()).thenReturn(HEAD);
        Mockito.when(prm2.getSuccess()).thenReturn(false);
        Mockito.when(prm2.getOverride()).thenReturn(false);
        Mockito.when(prm2.getBuildStarted()).thenReturn(false);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_PR)).thenReturn(true);

        Mockito.when(proEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prRescopedEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prCommentEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prCommentEvent.getComment()).thenReturn(comment);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo))
            .thenReturn(rc);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(".*master.*");
        Mockito.when(rc.getRebuildOnTargetUpdate()).thenReturn(true);

        prl = new PullRequestListener(cpm, jenkinsManager, lf);

    }

    @Test
    public void testTriggersBuildOnPullRequest() {
        prl.listenForPRCreates(proEvent);
        Mockito.verify(jenkinsManager)
            .triggerBuild(repo, JobType.VERIFY_PR, pr);
    }

    @Test
    public void testNoTriggersBuildOnPullRequestWithPRVerifyDisabled() {
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_PR)).thenReturn(false);
        prl.listenForPRCreates(proEvent);
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JobType.VERIFY_PR, pr);
    }

    @Test
    public void testCIDisabled() {
        Mockito.when(rc.getCiEnabled()).thenReturn(false);
        prl.listenForPRCreates(proEvent);
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.any(PullRequest.class));
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testNormalComment() {
        Mockito.when(comment.getText()).thenReturn(COMMENT_TEXT);
        Mockito.when(prm.getBuildStarted()).thenReturn(true);
        prl.listenForComments(prCommentEvent);
        Mockito.verify(cpm, Mockito.never()).setPullRequestMetadata(
            Mockito.any(PullRequest.class), (Boolean) Mockito.notNull(),
            Mockito.anyBoolean(), Mockito.anyBoolean());
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.any(PullRequest.class));
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testOverrideComment() {
        Mockito.when(comment.getText()).thenReturn(OVERRIDE_COMMENT_TEXT);
        Mockito.when(prm.getBuildStarted()).thenReturn(true);
        prl.listenForComments(prCommentEvent);
        Mockito.verify(cpm).setPullRequestMetadata(Mockito.eq(pr),
            Mockito.eq((Boolean) null), Mockito.eq((Boolean) null),
            Mockito.eq(true));
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.any(PullRequest.class));
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(
            Mockito.any(Repository.class), Mockito.any(JobType.class),
            Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testEditPullRequestNoUpdate() {
        Mockito.when(prm.getBuildStarted()).thenReturn(true);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        prl.listenForRescope(prRescopedEvent);
        // Ensure metadata is not changed
        Mockito.verify(cpm, Mockito.never()).setPullRequestMetadata(
            Mockito.any(PullRequest.class), Mockito.anyBoolean(),
            Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    public void testEditPullRequestUpdatesState() {
        Mockito.when(prm.getBuildStarted()).thenReturn(false);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        prl.listenForRescope(prRescopedEvent);
        // Ensure metadata IS changed because from sha is different now
        // should set override and success to false
        // Also, should trigger a build
        Mockito.verify(cpm).setPullRequestMetadata(Mockito.eq(pr),
            Mockito.eq(true), Mockito.eq(false), Mockito.eq(false));
        Mockito.verify(jenkinsManager)
            .triggerBuild(repo, JobType.VERIFY_PR, pr);
    }

    @Test
    public void testDOESNOTTriggerBuildOnPullRequestTargetUpdateWithUpdateDisabled() {
        Mockito.when(prm.getBuildStarted()).thenReturn(true);
        Mockito.when(prm2.getBuildStarted()).thenReturn(true);
        Mockito.when(prm2.getToSha()).thenReturn("different value");
        Mockito.when(toRef.getLatestCommit()).thenReturn("different value");
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);
        Mockito.when(cpm.getPullRequestMetadataWithoutToRef(pr)).thenReturn(ImmutableList.of(prm, prm2));
        Mockito.when(rc.getRebuildOnTargetUpdate()).thenReturn(false);

        prl.listenForRescope(prRescopedEvent);
        // ensure build is NOT triggered
        Mockito.verify(jenkinsManager, Mockito.never())
            .triggerBuild(repo, JobType.VERIFY_PR, pr);
    }

}
