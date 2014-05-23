package com.palantir.stash.stashbot.util;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.util.Operation;

public class PullRequestFetcherOperation implements Operation<Void, Exception> {

    private final PullRequestService prs;
    private final Integer repoId;
    private final Long prId;

    private PullRequest pr;
    private Boolean wasCalled;

    public PullRequestFetcherOperation(PullRequestService prs, Integer repoId, Long prId) {
        this.prs = prs;
        this.repoId = repoId;
        this.prId = prId;
        wasCalled = false;
    }

    @Override
    public Void perform() throws Exception {
        pr = prs.getById(repoId, prId);
        wasCalled = true;
        return null;
    }

    public PullRequest getPullRequest() {
        if (!wasCalled) {
            throw new IllegalStateException("Tried to get item before calling perform()");
        }
        return pr;
    }
}