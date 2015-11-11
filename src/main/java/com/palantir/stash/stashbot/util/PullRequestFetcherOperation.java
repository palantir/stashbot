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
package com.palantir.stash.stashbot.util;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.util.Operation;

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
