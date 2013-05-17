package com.palantir.stash.stashbothelper.config;

import java.sql.SQLException;

import net.java.ao.DBParam;
import net.java.ao.Query;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.stash.repository.Repository;

public class ConfigurationPersistenceManager {

    private final ActiveObjects ao;

    private static final String JENKINS_SERVER_CONFIG_KEY = "default";

    public ConfigurationPersistenceManager(ActiveObjects ao) {
        this.ao = ao;
    }

    public JenkinsServerConfiguration getJenkinsServerConfiguration() throws SQLException {
        JenkinsServerConfiguration[] configs =
            ao.find(JenkinsServerConfiguration.class,
                Query.select().where("NAME = ?", JENKINS_SERVER_CONFIG_KEY));
        if (configs.length == 0) {
            // just use the defaults
            return ao.create(JenkinsServerConfiguration.class,
                new DBParam("NAME", JENKINS_SERVER_CONFIG_KEY));
        }

        return configs[0];
    }

    public void setJenkinsServerConfiguration(String url, String username, String password) throws SQLException {
        JenkinsServerConfiguration[] configs =
            ao.find(JenkinsServerConfiguration.class,
                Query.select().where("name = ?", JENKINS_SERVER_CONFIG_KEY));

        if (configs.length == 0) {
            ao.create(JenkinsServerConfiguration.class,
                new DBParam("NAME", JENKINS_SERVER_CONFIG_KEY),
                new DBParam("URL", url),
                new DBParam("USERNAME", username),
                new DBParam("PASSWORD", password));
            return;
        }
        // already exists, so update it
        configs[0].setUrl(url);
        configs[0].setUsername(username);
        configs[0].setPassword(password);
        configs[0].save();
    }

    public RepositoryConfiguration getRepositoryConfigurationForRepository(Repository repo) throws SQLException {
        RepositoryConfiguration[] repos = ao.find(RepositoryConfiguration.class,
            Query.select().where("REPO_ID = ?", repo.getId()));
        if (repos.length == 0) {
            // just use the defaults
            RepositoryConfiguration rc = ao.create(RepositoryConfiguration.class,
                new DBParam("REPO_ID", repo.getId()));
            rc.save();
            return rc;
        }
        return repos[0];
    }

    public void setRepositoryConfigurationForRepository(Repository repo, boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, String publishBranchRegex, String publishBuildCommand) throws SQLException {
        RepositoryConfiguration[] repos = ao.find(RepositoryConfiguration.class,
            Query.select().where("repo_id = ?", repo.getId()));
        if (repos.length == 0) {
            RepositoryConfiguration rc = ao.create(RepositoryConfiguration.class,
                new DBParam("REPO_ID", repo.getId()),
                new DBParam("CI_ENABLED", isCiEnabled),
                new DBParam("VERIFY_BRANCH_REGEX", verifyBranchRegex),
                new DBParam("VERIFY_BUILD_COMMAND", verifyBuildCommand),
                new DBParam("PUBLISH_BRANCH_REGEX", publishBranchRegex),
                new DBParam("PUBLISH_BUILD_COMMAND", publishBuildCommand)
                );
            rc.save();
            return;
        }
        repos[0].setCiEnabled(isCiEnabled);
        repos[0].setVerifyBranchRegex(verifyBranchRegex);
        repos[0].setVerifyBuildCommand(verifyBuildCommand);
        repos[0].setPublishBranchRegex(publishBranchRegex);
        repos[0].setPublishBuildCommand(publishBuildCommand);
        repos[0].save();
    }
}
