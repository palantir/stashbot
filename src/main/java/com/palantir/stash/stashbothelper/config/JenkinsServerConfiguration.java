package com.palantir.stash.stashbothelper.config;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Table("JSConfig001")
@Preload
public interface JenkinsServerConfiguration extends Entity {

    @NotNull
    @Unique
    public String getName();

    public void setName(String username);

    @NotNull
    @Default("empty")
    public String getUrl();

    public void setUrl(String url);

    @NotNull
    @Default("empty")
    public String getUsername();

    public void setUsername(String username);

    @NotNull
    @Default("empty")
    public String getPassword();

    public void setPassword(String password);

    @NotNull
    @Default("empty")
    public String getStashUsername();

    public void setStashUsername(String stashUsername);

    @NotNull
    @Default("empty")
    public String getStashPassword();

    public void setStashPassword(String stashPassword);
}
