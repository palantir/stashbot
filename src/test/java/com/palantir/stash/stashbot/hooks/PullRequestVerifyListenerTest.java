package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.hooks.PullRequestVerifyListener;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class PullRequestVerifyListenerTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String HEAD_BR = "refs/heads/feature";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final String MERGE_BR = "refs/heads/master";
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private RepositoryConfiguration rc;

    private PullRequestVerifyListener prvl;

    @Mock
    private PullRequestOpenedEvent proEvent;
    @Mock
    private PullRequest pr;
    @Mock
    private Repository repo;
    @Mock
    private PullRequestRef fromRef;
    @Mock
    private PullRequestRef toRef;

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

        Mockito.when(proEvent.getPullRequest()).thenReturn(pr);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(".*master.*");

        prvl = new PullRequestVerifyListener(cpm, jenkinsManager);

    }

    @Test
    public void testTriggersBuildOnPullRequest() {
        prvl.listen(proEvent);
        Mockito.verify(jenkinsManager).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD, MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }

    @Test
    public void testCIDisabled() {
        Mockito.when(rc.getCiEnabled()).thenReturn(false);
        prvl.listen(proEvent);
        Mockito.verify(jenkinsManager, Mockito.never()).triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, HEAD,
            MERGE_HEAD,
            Long.toString(PULL_REQUEST_ID));
    }
}
