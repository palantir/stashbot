package com.palantir.stash.stashbot.util;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.util.Operation;

public class BuildStatusAddOperation implements Operation<Void, Exception> {

    private final BuildStatusService bss;
    private final BuildStatus bs;
    private final String buildHead;

    public BuildStatusAddOperation(BuildStatusService bss, String buildHead, BuildStatus bs) {
        this.bss = bss;
        this.buildHead = buildHead;
        this.bs = bs;
    }

    @Override
    public Void perform() throws Exception {
        bss.add(buildHead, bs);
        return null;
    }
}