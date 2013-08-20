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
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.comment.Comment;
import com.atlassian.stash.event.pull.PullRequestCommentEvent;
import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.event.pull.PullRequestUpdatedEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class PullRequestListenerTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String HEAD_BR = "refs/heads/feature";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final String MERGE_BR = "refs/heads/master";
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    private static final String OLD_MERGE_HEAD = "8e57a8b77501710fe1e30a3500102c0968763107";

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private RepositoryConfiguration rc;

    private PullRequestListener prl;

    @Mock
    private PullRequestOpenedEvent proEvent;
    @Mock
    private PullRequestUpdatedEvent prUpdatedEvent;
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

    private static final String COMMENT_TEXT = "comment text";
    private static final String OVERRIDE_COMMENT_TEXT = "Comment added with ==OVERRIDE==";

    @Before
    public void setUp() throws SQLException {

        MockitoAnnotations.initMocks(this);

        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(pr.getId()).thenReturn(new Long(PULL_REQUEST_ID));
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);

        Mockito.when(fromRef.getRepository()).thenReturn(repo);
        Mockito.when(fromRef.getId()).thenReturn(HEAD_BR);
        Mockito.when(fromRef.getLatestChangeset()).thenReturn(HEAD);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getId()).thenReturn(MERGE_BR);
        Mockito.when(toRef.getLatestChangeset()).thenReturn(MERGE_HEAD);

        Mockito.when(prm.getPullRequestId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(prm.getFromSha()).thenReturn(HEAD);
        Mockito.when(prm.getToSha()).thenReturn(MERGE_HEAD);
        Mockito.when(prm.getSuccess()).thenReturn(false);
        Mockito.when(prm.getOverride()).thenReturn(false);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        Mockito.when(proEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prUpdatedEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prCommentEvent.getPullRequest()).thenReturn(pr);
        Mockito.when(prCommentEvent.getComment()).thenReturn(comment);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(".*master.*");

        prl = new PullRequestListener(cpm, jenkinsManager);

    }

    @Test
    public void testTriggersBuildOnPullRequest() {
        prl.listen(proEvent);
        Mockito.verify(jenkinsManager).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD, MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }

    @Test
    public void testCIDisabled() {
        Mockito.when(rc.getCiEnabled()).thenReturn(false);
        prl.listen(proEvent);
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD,
            MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }

    @Test
    public void testNormalComment() {
        Mockito.when(comment.getText()).thenReturn(COMMENT_TEXT);
        prl.listen(prCommentEvent);
        Mockito.verify(cpm, Mockito.never()).setPullRequestMetadata(Mockito.any(PullRequest.class),
            Mockito.anyBoolean(), Mockito.anyBoolean());
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD,
            MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }

    @Test
    public void testOverrideComment() {
        Mockito.when(comment.getText()).thenReturn(OVERRIDE_COMMENT_TEXT);
        prl.listen(prCommentEvent);
        Mockito.verify(cpm).setPullRequestMetadata(Mockito.eq(pr), Mockito.eq((Boolean) null), Mockito.eq(true));
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD,
            MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }

    @Test
    public void testEditPullRequestNoUpdate() {
        Mockito.when(prm.getFromSha()).thenReturn(HEAD);
        Mockito.when(prm.getToSha()).thenReturn(MERGE_HEAD);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        prl.listen(prUpdatedEvent);
        // Ensure metadata is not changed
        Mockito.verify(cpm, Mockito.never()).setPullRequestMetadata(Mockito.any(PullRequest.class),
            Mockito.anyBoolean(),
            Mockito.anyBoolean());
    }

    public void testEditPullRequestUpdatesState() {
        Mockito.when(prm.getFromSha()).thenReturn(HEAD);
        Mockito.when(prm.getToSha()).thenReturn(OLD_MERGE_HEAD);
        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        prl.listen(prUpdatedEvent);
        // Ensure metadata IS changed because from sha is different now
        // should set override and success to false
        // Also, should trigger a build
        Mockito.verify(cpm).setPullRequestMetadata(Mockito.eq(pr), Mockito.eq(false),
            Mockito.eq(false));
        Mockito.verify(jenkinsManager).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD, MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }
}
