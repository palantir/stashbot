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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.java.ao.DBParam;
import net.java.ao.Query;

import org.slf4j.Logger;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;

public class ConfigurationPersistenceManager {

    private final ActiveObjects ao;
    private final Logger log;

    private static final String DEFAULT_JENKINS_SERVER_CONFIG_KEY = "default";

    public ConfigurationPersistenceManager(ActiveObjects ao,
        StashbotLoggerFactory lf) {
        this.ao = ao;
        this.log = lf.getLoggerForThis(this);
    }

    public void deleteJenkinsServerConfiguration(String name) {
        JenkinsServerConfiguration[] configs = ao.find(
            JenkinsServerConfiguration.class,
            Query.select().where("NAME = ?", name));
        if (configs.length == 0) {
            return;
        }
        for (JenkinsServerConfiguration jsc : configs) {
            ao.delete(jsc);
        }
    }

    public JenkinsServerConfiguration getJenkinsServerConfiguration(String name)
        throws SQLException {
        if (name == null) {
            name = DEFAULT_JENKINS_SERVER_CONFIG_KEY;
        }
        JenkinsServerConfiguration[] configs = ao.find(
            JenkinsServerConfiguration.class,
            Query.select().where("NAME = ?", name));
        if (configs.length == 0) {
            // just use the defaults
            return ao.create(JenkinsServerConfiguration.class, new DBParam(
                "NAME", name));
        }

        String url = configs[0].getUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
            configs[0].setUrl(url);
            configs[0].save();
        }
        return configs[0];
    }

    public void setJenkinsServerConfiguration(String name, String url,
        String username, String password, String stashUsername,
        String stashPassword, Integer maxVerifyChain) throws SQLException {
        if (name == null) {
            name = DEFAULT_JENKINS_SERVER_CONFIG_KEY;
        }
        validateName(name);
        JenkinsServerConfiguration[] configs = ao.find(
            JenkinsServerConfiguration.class,
            Query.select().where("NAME = ?", name));

        if (configs.length == 0) {
            log.info("Creating jenkins configuration: " + name);
            ao.create(JenkinsServerConfiguration.class, new DBParam("NAME",
                name), new DBParam("URL", url), new DBParam("USERNAME",
                username), new DBParam("PASSWORD", password), new DBParam(
                "STASH_USERNAME", stashUsername), new DBParam(
                "STASH_PASSWORD", stashPassword), new DBParam(
                "MAX_VERIFY_CHAIN", maxVerifyChain));
            return;
        }
        // already exists, so update it
        configs[0].setName(name);
        configs[0].setUrl(url);
        configs[0].setUsername(username);
        configs[0].setPassword(password);
        configs[0].setStashUsername(stashUsername);
        configs[0].setStashPassword(stashPassword);
        configs[0].setMaxVerifyChain(maxVerifyChain);
        configs[0].save();
    }

    public RepositoryConfiguration getRepositoryConfigurationForRepository(
        Repository repo) throws SQLException {
        RepositoryConfiguration[] repos = ao.find(
            RepositoryConfiguration.class,
            Query.select().where("REPO_ID = ?", repo.getId()));
        if (repos.length == 0) {
            // just use the defaults
            RepositoryConfiguration rc = ao.create(
                RepositoryConfiguration.class,
                new DBParam("REPO_ID", repo.getId()));
            rc.save();
            return rc;
        }
        return repos[0];
    }

    public void setRepositoryConfigurationForRepository(Repository repo,
        boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, String publishBranchRegex,
        String publishBuildCommand, String prebuildCommand)
        throws SQLException, IllegalArgumentException {
        setRepositoryConfigurationForRepository(repo, isCiEnabled,
            verifyBranchRegex, verifyBuildCommand, publishBranchRegex,
            publishBuildCommand, prebuildCommand, null, null);
    }

    public void setRepositoryConfigurationForRepository(Repository repo,
        boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, String publishBranchRegex,
        String publishBuildCommand, String prebuildCommand,
        String jenkinsServerName, Integer maxVerifyChain)
        throws SQLException, IllegalArgumentException {
        if (jenkinsServerName == null) {
            jenkinsServerName = DEFAULT_JENKINS_SERVER_CONFIG_KEY;
        }
        validateNameExists(jenkinsServerName);
        RepositoryConfiguration[] repos = ao.find(
            RepositoryConfiguration.class,
            Query.select().where("repo_id = ?", repo.getId()));
        if (repos.length == 0) {
            log.info("Creating repository configuration for id: "
                + repo.getId().toString());
            RepositoryConfiguration rc = ao.create(
                RepositoryConfiguration.class,
                new DBParam("REPO_ID", repo.getId()), new DBParam(
                    "CI_ENABLED", isCiEnabled), new DBParam(
                    "VERIFY_BRANCH_REGEX", verifyBranchRegex),
                new DBParam("VERIFY_BUILD_COMMAND", verifyBuildCommand),
                new DBParam("PUBLISH_BRANCH_REGEX", publishBranchRegex),
                new DBParam("PUBLISH_BUILD_COMMAND", publishBuildCommand),
                new DBParam("PREBUILD_COMMAND", prebuildCommand),
                new DBParam("JENKINS_SERVER_NAME", jenkinsServerName));
            if (maxVerifyChain != null) {
                rc.setMaxVerifyChain(maxVerifyChain);
            }
            rc.save();
            return;
        }
        repos[0].setCiEnabled(isCiEnabled);
        repos[0].setVerifyBranchRegex(verifyBranchRegex);
        repos[0].setVerifyBuildCommand(verifyBuildCommand);
        repos[0].setPublishBranchRegex(publishBranchRegex);
        repos[0].setPublishBuildCommand(publishBuildCommand);
        repos[0].setPrebuildCommand(prebuildCommand);
        repos[0].setJenkinsServerName(jenkinsServerName);
        if (maxVerifyChain != null) {
            repos[0].setMaxVerifyChain(maxVerifyChain);
        }
        repos[0].save();
    }

    public ImmutableCollection<JenkinsServerConfiguration> getAllJenkinsServerConfigurations()
        throws SQLException {
        JenkinsServerConfiguration[] allConfigs = ao
            .find(JenkinsServerConfiguration.class);
        if (allConfigs.length == 0) {
            return ImmutableList.of(getJenkinsServerConfiguration(null));
        }
        return ImmutableList.copyOf(allConfigs);
    }

    public ImmutableCollection<String> getAllJenkinsServerNames()
        throws SQLException {
        List<String> names = new ArrayList<String>();

        JenkinsServerConfiguration[] allConfigs = ao
            .find(JenkinsServerConfiguration.class);
        for (JenkinsServerConfiguration jsc : allConfigs) {
            names.add(jsc.getName());
        }
        if (allConfigs.length == 0) {
            return ImmutableList.of(getJenkinsServerConfiguration(null)
                .getName());
        }
        return ImmutableList.copyOf(names);

    }

    public void validateName(String name) throws IllegalArgumentException {
        if (!name.matches("[a-zA-Z0-9]+")) {
            throw new IllegalArgumentException("Name must match [a-zA-Z0-9]+");
        }
    }

    public void validateNameExists(String name) throws IllegalArgumentException {
        if (name.equals(DEFAULT_JENKINS_SERVER_CONFIG_KEY)) {
            return;
        }
        JenkinsServerConfiguration[] allConfigs = ao
            .find(JenkinsServerConfiguration.class);
        for (JenkinsServerConfiguration jsc : allConfigs) {
            if (jsc.getName().equals(name)) {
                return;
            }
        }
        throw new IllegalArgumentException("Jenkins Server name " + name
            + " does not exist");
    }

    private String pullRequestToString(PullRequest pr) {
        return "[id:" + Long.toString(pr.getId()) + ", from:"
            + pr.getFromRef().getLatestChangeset() + ", to:"
            + pr.getToRef().getLatestChangeset() + "]";
    }

    public PullRequestMetadata getPullRequestMetadata(PullRequest pr) {
        Long id = pr.getId();
        String fromSha = pr.getFromRef().getLatestChangeset().toString();
        String toSha = pr.getToRef().getLatestChangeset().toString();

        PullRequestMetadata[] prms = ao.find(PullRequestMetadata.class,
            "PULL_REQUEST_ID = ? and TO_SHA = ? and FROM_SHA = ?", id,
            toSha, fromSha);
        if (prms.length == 0) {
            // new/updated PR, create a new object
            log.info("Creating PR Metadata for pull request: "
                + pullRequestToString(pr));
            PullRequestMetadata prm =
                ao.create(
                    PullRequestMetadata.class,
                    new DBParam("PULL_REQUEST_ID", id),
                    new DBParam("TO_SHA", toSha),
                    new DBParam("FROM_SHA", fromSha));
            prm.save();
            return prm;

        }
        return prms[0];
    }

    public void setPullRequestMetadata(PullRequest pr, Boolean buildStarted,
        Boolean success, Boolean override) {
        PullRequestMetadata prm = getPullRequestMetadata(pr);
        if (buildStarted != null) {
            prm.setBuildStarted(buildStarted);
        }
        if (success != null) {
            prm.setSuccess(success);
        }
        if (override != null) {
            prm.setOverride(override);
        }
        prm.save();
    }
}
