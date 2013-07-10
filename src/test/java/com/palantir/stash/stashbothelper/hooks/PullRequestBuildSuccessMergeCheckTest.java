package com.palantir.stash.stashbothelper.hooks;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.stash.comment.Comment;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestActivity;
import com.atlassian.stash.pull.PullRequestActivityVisitor;
import com.atlassian.stash.pull.PullRequestCommentActivity;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.palantir.stash.stashbothelper.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbothelper.config.RepositoryConfiguration;

public class PullRequestBuildSuccessMergeCheckTest {

    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;
    private static final String TO_SHA = "refs/heads/master";
    private static final String VERIFY_REGEX = ".*master";

    @Mock
    private PullRequestService prs;
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

    // Page stuff
    @SuppressWarnings("rawtypes")
    @Mock
    private Page activities;
    @Mock
    private PullRequestCommentActivity prca;
    @Mock
    private Comment comment;
    private List<PullRequestActivity> activityList;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);

        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(VERIFY_REGEX);
        Mockito.when(prs.findById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);

        Mockito.when(mr.getPullRequest()).thenReturn(pr);

        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getToRef()).thenReturn(toRef);

        Mockito.when(fromRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getId()).thenReturn(TO_SHA);

        activityList = new ArrayList<PullRequestActivity>();
        activityList.add(prca);
        Mockito.when(activities.getValues()).thenReturn(activityList);
        Mockito.when(activities.getIsLastPage()).thenReturn(true);
        Mockito.when(prca.getComment()).thenReturn(comment);

        Mockito.when(
            prs.getActivities(Mockito.eq(REPO_ID), Mockito.eq(PULL_REQUEST_ID), Mockito.any(PageRequest.class)))
            .thenReturn(activities);

        prmc = new PullRequestBuildSuccessMergeCheck(prs, cpm);

        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                PullRequestActivityVisitor prav = (PullRequestActivityVisitor) invocation.getArguments()[0];
                prav.visit(prca);
                return null;
            }

        }).when(prca).accept(Mockito.any(PullRequestActivityVisitor.class));
    }

    @Test
    public void testSuccessMergeCheckTest() {
        Mockito.when(comment.getText()).thenReturn("This text contains ==SUCCESSFUL== inside it.");

        prmc.check(mr);

        Mockito.verify(mr, Mockito.never()).veto(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testOverrideMergeCheckTest() {
        Mockito.when(comment.getText()).thenReturn("This text contains ==OVERRIDE== inside it.");

        prmc.check(mr);

        Mockito.verify(mr, Mockito.never()).veto(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testFailsMergeCheckTest() {
        Mockito.when(comment.getText()).thenReturn("This text contains nothing good inside it.");

        prmc.check(mr);

        Mockito.verify(mr).veto(Mockito.anyString(), Mockito.anyString());
    }
}