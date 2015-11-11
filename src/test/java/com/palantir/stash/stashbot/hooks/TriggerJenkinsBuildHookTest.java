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
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.mocks.MockGitCommandBuilderFactory;
import com.palantir.stash.stashbot.outputhandler.CommandOutputHandlerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class TriggerJenkinsBuildHookTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String HEAD_BR = "master";
    private static final String FROM_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final String HEAD_MINUS_ONE = "15e5e7272bec0e0c1093327b0e8e02deefa6d1e5";
    private static final int REPO_ID = 1;
    private static final Integer MVC = 10;

    @Mock
    private ConfigurationPersistenceService cpm;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    private TriggerJenkinsBuildHook tjbh;

    @Mock
    private Repository repo;

    @Mock
    private HookResponse hr;
    @Mock
    private RefChange change;

    // Stuff from MockGitCommandFactory class
    MockGitCommandBuilderFactory mgc;
    private GitCommandBuilderFactory gcbf;
    private CommandOutputHandlerFactory cohf;

    private ArrayList<RefChange> changes;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    @Before
    public void setUp() throws SQLException {

        MockitoAnnotations.initMocks(this);

        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(cpm.getJenkinsServerConfiguration(Mockito.anyString())).thenReturn(jsc);
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT)).thenReturn(true);
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.PUBLISH)).thenReturn(true);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(".*master.*");
        Mockito.when(rc.getPublishBranchRegex()).thenReturn(".*release.*");
        Mockito.when(jsc.getMaxVerifyChain()).thenReturn(MVC);

        Mockito.when(change.getFromHash()).thenReturn(FROM_HEAD);
        Mockito.when(change.getToHash()).thenReturn(HEAD);
        Mockito.when(change.getRefId()).thenReturn(HEAD_BR);
        Mockito.when(change.getType()).thenReturn(RefChangeType.UPDATE);

        changes = new ArrayList<RefChange>();
        changes.add(change);

        // MGC stuff
        mgc = new MockGitCommandBuilderFactory();
        mgc.getChangesets().add(HEAD);
        mgc.getBranchMap().put(HEAD, ImmutableList.of("  otherbranch"));

        gcbf = mgc.getGitCommandBuilderFactory();
        cohf = new CommandOutputHandlerFactory();

        tjbh = new TriggerJenkinsBuildHook(cpm, jenkinsManager, gcbf, cohf, lf);
    }

    @Test
    public void testTriggersBuildOnPush() {
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD, "");
    }

    @Test
    public void testDoesntTriggerBuildOnPushWhenDisabled() {
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT)).thenReturn(false);
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD, "");
    }

    @Test
    public void testNoBuildOnDisabled() {
        Mockito.when(rc.getCiEnabled()).thenReturn(false);
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(Mockito.any(Repository.class),
            Mockito.any(JobType.class), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testNoBuildOnDelete() {
        Mockito.when(change.getType()).thenReturn(RefChangeType.DELETE);
        mgc.getChangesets().clear(); // empty changesets means no new changes

        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(Mockito.any(Repository.class),
            Mockito.any(JobType.class), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testNoBuildOnRegexNotMatch() {
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn("blahblahnomatch");
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(Mockito.eq(repo), Mockito.any(JobType.class),
            Mockito.eq(HEAD), Mockito.eq(HEAD_BR));
    }

    @Test
    public void testPublishingBuild() {
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn("blahblahnomatch");
        Mockito.when(rc.getPublishBranchRegex()).thenReturn("master");
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.PUBLISH, HEAD, HEAD_BR);
    }

    @Test
    public void testPublishingBuildWhenDisabled() {
        Mockito.when(cpm.getJobTypeStatusMapping(rc, JobType.PUBLISH)).thenReturn(false);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn("blahblahnomatch");
        Mockito.when(rc.getPublishBranchRegex()).thenReturn("master");
        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JobType.PUBLISH, HEAD, HEAD_BR);
    }

    @Test
    public void testVerifyBuildsMultipleChanges() {
        mgc.getChangesets().clear();
        mgc.getChangesets().add(HEAD_MINUS_ONE);
        mgc.getChangesets().add(HEAD);

        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD_MINUS_ONE, "");
        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD, "");
    }

    /* XXX TODO: this test needs to be rewritten to ensure git is invoked in the correct way, 
     * because this codepath happens in the revwalk git does rather than "in java land" now.
     */
    @Ignore
    @Test
    public void testVerifyIgnoresChangeAlreadyInPreviousBranch() {
        // the revlist call here is -- HEAD ^HEAD_MINUS_ONE
        // so we'll accomplish that by adding both to the changesets list but blacklisting HEAD_MINUS_ONE so the only new change is HEAD.
        mgc.getChangesets().clear();
        mgc.getChangesets().add(HEAD_MINUS_ONE);
        mgc.getChangesets().add(HEAD);
        mgc.getBlacklistedChangesets().add(HEAD_MINUS_ONE);
        // HEAD_MINUS_ONE is already in branch master2, so don't verify it
        mgc.getBranchMap().put(HEAD_MINUS_ONE, ImmutableList.of("  master2"));

        tjbh.onReceive(repo, changes, hr);

        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(Mockito.eq(repo), Mockito.any(JobType.class),
            Mockito.eq(HEAD_MINUS_ONE), Mockito.eq(""));
        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD, "");
    }

    @Test
    public void testVerifyNewBranch() {
        mgc.getChangesets().clear();
        mgc.getChangesets().add(HEAD_MINUS_ONE);
        mgc.getChangesets().add(HEAD);
        Mockito.when(change.getType()).thenReturn(RefChangeType.ADD);
        Mockito.when(change.getFromHash()).thenReturn("0000000000000000000000000000000000000000");

        tjbh.onReceive(repo, changes, hr);

        // TODO: verify the git rev-list is invoked with proper args?
        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD_MINUS_ONE, "");
        Mockito.verify(jenkinsManager).triggerBuild(repo, JobType.VERIFY_COMMIT, HEAD, "");
    }
}
