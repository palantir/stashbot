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

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;

public class PullRequestBuildSuccessMergeCheckTest {

    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;
    private static final String TO_SHA = "refs/heads/master";
    private static final String VERIFY_REGEX = ".*master";

    @Mock
    private ConfigurationPersistenceManager cpm;

    @Mock
    private PullRequest pr;
    @Mock
    private MergeRequest mr;
    @Mock
    private Repository repo;
    @Mock
    private PullRequestRef fromRef;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private RepositoryConfiguration rc;

    private PullRequestBuildSuccessMergeCheck prmc;
    @Mock
    private PullRequestMetadata prm;

    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);

        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(VERIFY_REGEX);

        Mockito.when(mr.getPullRequest()).thenReturn(pr);

        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);

        Mockito.when(fromRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getId()).thenReturn(TO_SHA);

        Mockito.when(cpm.getPullRequestMetadata(pr)).thenReturn(prm);

        prmc = new PullRequestBuildSuccessMergeCheck(cpm);
    }

    @Test
    public void testSuccessMergeCheckTest() {
        Mockito.when(prm.getSuccess()).thenReturn(true);
        Mockito.when(prm.getOverride()).thenReturn(false);

        prmc.check(mr);

        Mockito.verify(mr, Mockito.never()).veto(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testOverrideMergeCheckTest() {
        Mockito.when(prm.getSuccess()).thenReturn(false);
        Mockito.when(prm.getOverride()).thenReturn(true);

        prmc.check(mr);

        Mockito.verify(mr, Mockito.never()).veto(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testFailsMergeCheckTest() {
        Mockito.when(prm.getSuccess()).thenReturn(false);
        Mockito.when(prm.getOverride()).thenReturn(false);

        prmc.check(mr);

        Mockito.verify(mr).veto(Mockito.anyString(), Mockito.anyString());
    }
}