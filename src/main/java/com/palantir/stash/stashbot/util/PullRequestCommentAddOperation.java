package com.palantir.stash.stashbot.util;

import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.util.Operation;

public class PullRequestCommentAddOperation implements Operation<Void, Exception> {

    private final PullRequestService prs;
    private final Integer repoId;
    private final Long prId;
    private final String commentText;

    public PullRequestCommentAddOperation(PullRequestService prs, Integer repoId, Long prId, String commentText) {
        this.prs = prs;
        this.repoId = repoId;
        this.prId = prId;
        this.commentText = commentText;
    }

    @Override
    public Void perform() throws Exception {
        prs.addComment(repoId, prId, commentText);
        return null;
    }
}