// Copyright 2015 Palantir Technologies
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
package com.palantir.stash.stashbot.persistence;

import com.palantir.stash.stashbot.jobtemplate.JobType;

public class JobTypeStatusMappingImpl {

    private final JobTypeStatusMapping jtsm;

    public JobTypeStatusMappingImpl(JobTypeStatusMapping jtsm) {
        this.jtsm = jtsm;
    }

    public JobType getJobType() {
        return JobType.valueOf(jtsm.getJobTypeRaw());
    }

    public void setJobType(JobType jt) {
        jtsm.setJobTypeRaw(jt.name());
    }

}
