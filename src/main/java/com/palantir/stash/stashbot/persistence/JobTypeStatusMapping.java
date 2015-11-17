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

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Implementation;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.Ignore;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

import com.palantir.stash.stashbot.jobtemplate.JobType;

@Table("JTSMapping001")
@Preload
@Implementation(JobTypeStatusMappingImpl.class)
public interface JobTypeStatusMapping extends Entity {

    @Accessor("REPO_CONFIG_")
    @NotNull
    public RepositoryConfiguration getRepositoryConfiguration();

    @Mutator("REPO_CONFIG_")
    public void setRepositoryConfiguration(RepositoryConfiguration rc);

    @Accessor("IS_ENABLED")
    @NotNull
    // not actually honored on automatically vivified objects; see
    // com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl.RepositoryConfiguration
    @Default("false")
    public Boolean getIsEnabled();

    @Mutator("IS_ENABLED")
    public void setIsEnabled(Boolean isEnabled);

    @Accessor("JOB_TYPE_RAW")
    @NotNull
    public String getJobTypeRaw();

    @Mutator("JOB_TYPE_RAW")
    public void setJobTypeRaw(String jt);

    // Implemented by the IMPL class
    // It is completely unclear how enums work from the AO docs, but suffice to say...
    // storing new DBParam("JOB_TYPE", jt.ordinal()) throws an NPE.
    // storing new DBParam("JOB_TYPE", jt.name()) throws an NPE.
    // storing new DBParam("JOB_TYPE", jt) throws an NPE.
    // So I GIVE UP.  This works fine.
    @Ignore
    public JobType getJobType();

    @Ignore
    public void setJobType(JobType jt);

}
