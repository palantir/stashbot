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
import net.java.ao.schema.Default;
import net.java.ao.schema.Table;

@Table("JobMapping001")
public interface JobMapping extends Entity {

    // data access
    public RepositoryConfiguration getRepositoryConfiguration();

    public void setRepositoryConfiguration(RepositoryConfiguration rc);

    public JobTemplate getJobTemplate();

    public void setJobTemplate(JobTemplate jjt);

    @Default("true")
    public Boolean isVisible();

    public void setVisible(Boolean visible);

    @Default("false")
    public Boolean isEnabled();

    public void setEnabled(Boolean enabled);

}
