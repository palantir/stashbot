package com.palantir.stash.stashbot.managers;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionAdminService;
import com.atlassian.stash.user.SetPermissionRequest;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserAdminService;
import com.atlassian.stash.user.UserService;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;

public class PluginUserManager {

    private final String STASH_EMAIL = "nobody@example.com";

    private final UserAdminService uas;
    private final UserService us;
    private final PermissionAdminService pas;

    public PluginUserManager(UserAdminService uas, PermissionAdminService pas, UserService us) {
        this.uas = uas;
        this.pas = pas;
        this.us = us;
    }

    public void createStashbotUser(JenkinsServerConfiguration jsc) {
        StashUser user = us.getUserByName(jsc.getStashUsername());
        if (user != null) {
            return;
        }

        // username not found, create it
        uas.createUser(jsc.getStashUsername(), jsc.getStashPassword(), jsc.getStashUsername(), STASH_EMAIL);
        user = us.getUserByName(jsc.getStashUsername());
        if (user == null) {
            throw new RuntimeException("Unable to create user " + jsc.getUsername());
        }
    }

    public void addUserToRepoForReading(String username, Repository repo) {
        StashUser user = us.getUserByName(username);
        Permission repoRead = Permission.REPO_READ;
        SetPermissionRequest spr =
            new SetPermissionRequest.Builder().repositoryPermission(repoRead, repo).user(user).build();
        pas.setPermission(spr);
    }
}
