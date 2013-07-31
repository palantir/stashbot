package com.palantir.stash.stashbot.config;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Table("PAConfig001")
@Preload
public interface PluginAuthenticationConfiguration extends Entity {

    @NotNull
    @Unique
    public String getName();

    public void setName(String username);

    @NotNull
    @Default("stash-readonly-user")
    public String getUsername();

    public void setUsername(String stashUsername);

    @NotNull
    @Default("YOU SHOULD REALLY SET THIS TO SOMETHING ELSE")
    public String getPassword();

    public void setPassword(String stashPassword);

    @NotNull
    @Default("stash-readonly-user@example.com")
    public String getEmailAddress();

    public void setEmailAddress(String email);

}
