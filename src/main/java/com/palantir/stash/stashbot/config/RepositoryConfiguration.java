//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.config;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Table("RepoConfig001")
@Preload
public interface RepositoryConfiguration extends Entity {

    @NotNull
    @Unique
    public Integer getRepoId();

    public void setRepoId(Integer id);

    @NotNull
    @Default("false")
    public Boolean getCiEnabled();

    public void setCiEnabled(Boolean url);

    @NotNull
    @Default("empty")
    public String getPublishBranchRegex();

    public void setPublishBranchRegex(String publishBranchRegex);

    @NotNull
    @Default("/bin/true")
    public String getPublishBuildCommand();

    public void setPublishBuildCommand(String publishBuildCommand);

    @NotNull
    @Default("empty")
    public String getVerifyBranchRegex();

    public void setVerifyBranchRegex(String verifyBranchRegex);

    @NotNull
    @Default("/bin/true")
    public String getVerifyBuildCommand();

    public void setVerifyBuildCommand(String verifyBuildCommand);

    @NotNull
    @Default("/bin/true")
    public String getPrebuildCommand();

    public void setPrebuildCommand(String prebuildCommand);

    @NotNull
    @Default("default")
    public String getJenkinsServerName();

    public void setJenkinsServerName(String jenkinsServerName);

    /**
     * Maximum number of verify builds to trigger when pushed all at once. This limit makes it so that if you push a
     * chain of 100 new commits all at once, instead of saturating your build hardware, only the N most recent commits
     * are built. Set to "0" to use the limit associated with the jenkins server, which is the default. If this value is
     * larger than the jenkins server limit, that limit will be used instead.
     */
    @NotNull
    @Default("0")
    public Integer getMaxVerifyChain();

    public void setMaxVerifyChain(Integer max);
}