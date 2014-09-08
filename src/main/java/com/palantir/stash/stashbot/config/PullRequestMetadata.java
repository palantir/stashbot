// Copyright 2013 Palantir Technologies
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
package com.palantir.stash.stashbot.config;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

@Table("PRMetadata001")
@Preload
public interface PullRequestMetadata extends Entity {

    @NotNull
    public Integer getRepoId();

    public void setRepoId(Integer id);

    @NotNull
    public Long getPullRequestId();

    public void setPullRequestId(Long id);

    @NotNull
    public String getFromSha();

    public void setFromSha(String fromSha);

    @NotNull
    public String getToSha();

    public void setToSha(String toSha);

    @NotNull
    @Default("false")
    public Boolean getBuildStarted();

    public void setBuildStarted(Boolean buildStarted);

    @NotNull
    @Default("false")
    public Boolean getSuccess();

    public void setSuccess(Boolean success);

    @NotNull
    @Default("false")
    public Boolean getOverride();

    public void setOverride(Boolean override);

}
