package com.palantir.stash.stashbot.urlbuilder;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.nav.NavBuilder.Repo;
import com.atlassian.stash.nav.NavBuilder.RepoClone;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.jobtemplate.JobType;

public class StashbotUrlBuilderTest {

    private static final String BUILD_HEAD = "0a943a29376f2336b78312d99e65da17048951db";
    private static final String TO_SHA = "beefbeef376f2336b78312d99e65da17048951db";
    private static final String ABS_URL = "http://example.com:1234/";
    private static final String ABS_URL_WITH_CREDS = "http://someuser:somepw@example.com:1234/";
    private static final Long PULL_REQUEST_ID = 1234L;
    private static final Integer REPO_ID = 5678;

    @Mock
    private NavBuilder nb;

    @Mock
    private Repository repo;
    @Mock
    private JenkinsServerConfiguration jsc;
    @Mock
    private PullRequest pr;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private PullRequestRef fromRef;

    private StashbotUrlBuilder sub;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(nb.buildAbsolute()).thenReturn(ABS_URL);
        Mockito.when(jsc.getStashUsername()).thenReturn("someuser");
        Mockito.when(jsc.getStashPassword()).thenReturn("somepw");
        Mockito.when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);
        Mockito.when(toRef.getLatestChangeset()).thenReturn(TO_SHA);
        Mockito.when(fromRef.getLatestChangeset()).thenReturn(BUILD_HEAD);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        sub = new StashbotUrlBuilder(nb);

    }

    @Test
    public void testJenkinsTriggerUrlVerify() throws Exception {
        String url = sub.getJenkinsTriggerUrl(repo, JobType.VERIFY_COMMIT,
            BUILD_HEAD, pr);
        Assert.assertEquals(ABS_URL
            + "/plugins/servlet/stashbot/build-trigger/" + REPO_ID
            + "/verification/" + BUILD_HEAD + "/" + TO_SHA + "/"
            + PULL_REQUEST_ID.toString(), url);
    }

    @Test
    public void testBuildReportingUrl() {
        String url = sub.buildReportingUrl(repo, JobType.VERIFY_COMMIT, jsc,
            "successful");
        Assert
            .assertEquals(
                ABS_URL
                    + "/plugins/servlet/stashbot/build-reporting/$repoId/verification/successful/$BUILD_NUMBER/$buildHead/$mergeHead/$pullRequestId",
                url);
    }

    @Test
    public void testCloneUrl() {
        final Repo repoBuilder = Mockito.mock(Repo.class);
        final RepoClone repoClone = Mockito.mock(RepoClone.class);
        Mockito.when(nb.repo(repo)).thenAnswer(new Answer<Repo>() {

            @Override
            public Repo answer(InvocationOnMock invocation) throws Throwable {
                return repoBuilder;
            }
        });
        Mockito.when(repoBuilder.clone("git")).thenAnswer(new Answer<RepoClone>() {

            @Override
            public RepoClone answer(InvocationOnMock invocation) throws Throwable {
                return repoClone;
            }
        });
        Mockito.when(repoClone.buildAbsoluteWithoutUsername()).thenReturn(ABS_URL);
        // String url = nb.repo(repo).clone("git").buildAbsolute();

        String url = sub.buildCloneUrl(repo, jsc);

        Mockito.verify(nb).repo(repo);
        Mockito.verify(repoBuilder).clone("git");
        Mockito.verify(repoClone).buildAbsoluteWithoutUsername();
        Assert.assertEquals(ABS_URL_WITH_CREDS, url);
    }
}
