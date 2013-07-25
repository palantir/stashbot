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
}