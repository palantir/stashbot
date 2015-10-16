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
package com.palantir.stash.stashbot.persistence;

import net.java.ao.Entity;
import net.java.ao.Implementation;
import net.java.ao.Preload;
import net.java.ao.schema.Ignore;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateImpl;
import com.palantir.stash.stashbot.jobtemplate.JobType;

@Table("JobTemplate001")
@Preload
@Implementation(JobTemplateImpl.class)
public interface JobTemplate extends Entity {

    @NotNull
    @Unique
    public String getName();

    public void setName(String name);

    @NotNull
    public String getTemplateFile();

    public void setTemplateFile(String file);

    // Job Type - used in part to specify semantics

    public JobType getJobType();

    public void setJobType(JobType jobType);

    // Implemented logic outside of CRUD
    @Ignore
    public String getBuildNameFor(Repository repo, JenkinsServerConfiguration jsc);
}
