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

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
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

    // Labels
    @NotNull
    @Default("false")
    public Boolean getVerifyPinned();

    public void setVerifyPinned(Boolean isPinned);

    @NotNull
    @Default("N/A")
    public String getVerifyLabel();

    public void setVerifyLabel(String label);

    @NotNull
    @Default("false")
    public Boolean getPublishPinned();

    public void setPublishPinned(Boolean isPinned);

    @NotNull
    @Default("N/A")
    public String getPublishLabel();

    public void setPublishLabel(String label);

    @NotNull
    @Default("false")
    public Boolean getJunitEnabled();

    public void setJunitEnabled(Boolean enabled);

    @NotNull
    @Default("N/A")
    public String getJunitPath();

    public void setJunitPath(String path);

    @NotNull
    @Default("false")
    public Boolean getArtifactsEnabled();

    public void setArtifactsEnabled(Boolean enabled);

    @NotNull
    @Default("N/A")
    public String getArtifactsPath();

    public void setArtifactsPath(String path);

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

    @NotNull
    @Default("true")
    public Boolean getRebuildOnTargetUpdate();

    public void setRebuildOnTargetUpdate(Boolean rebuild);

    @NotNull
    @Default("false")
    public Boolean getEmailNotificationsEnabled();

    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled);

    @NotNull
    @Default("empty")
    public String getEmailRecipients();

    public void setEmailRecipients(String emailRecipients);

    @NotNull
    @Default("false")
    public Boolean getEmailForEveryUnstableBuild();

    public void setEmailForEveryUnstableBuild(Boolean emailForEveryUnstableBuild);

    @NotNull
    @Default("false")
    public Boolean getEmailSendToIndividuals();

    public void setEmailSendToIndividuals(Boolean emailSendToIndividuals);

    @NotNull
    @Default("false")
    public Boolean getEmailPerModuleEmail();

    public void setEmailPerModuleEmail(Boolean emailPerModuleEmail);

    @NotNull
    @Default("false")
    public Boolean getStrictVerifyMode();

    public void setStrictVerifyMode(Boolean strictVerifyMode);

    @NotNull
    @Default("false")
    @Accessor("TIMESTAMPS_ENABLED")
    public Boolean getTimestampJobOutputEnabled();

    @Mutator("TIMESTAMPS_ENABLED")
    public void setTimestampJobOutputEnabled(Boolean timestampJobOutputEnabled);

    @NotNull
    @Default("false")
    @Accessor("BUILD_TIMEOUT_ENABLED")
    public Boolean getBuildTimeoutEnabled();

    @Mutator("BUILD_TIMEOUT_ENABLED")
    public void setBuildTimeoutEnabled(Boolean buildTimeoutEnabled);

    @NotNull
    @Default("180")
    @Accessor("BUILD_TIMEOUT")
    public Integer getBuildTimeout();

    @Mutator("BUILD_TIMEOUT")
    public void setBuildTimeout(Integer buildTimeout);

    @NotNull
    @Default("false")
    @Accessor("ANSICOLOR_ENABLED")
    public Boolean getAnsiColorJobOutputEnabled();

    @Mutator("ANSICOLOR_ENABLED")
    public void setAnsiColorJobOutputEnabled(Boolean ansiColorJobOutputEnabled);

    @NotNull
    @Default("false")
    public Boolean getPreserveJenkinsJobConfig();

    public void setPreserveJenkinsJobConfig(Boolean preserveJenkinsJobConfig);

}
