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
