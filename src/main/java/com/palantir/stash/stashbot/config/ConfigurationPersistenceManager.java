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

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.event.StashbotMetadataUpdatedEvent;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationPersistenceManager {

    private final ActiveObjects ao;
    private final Logger log;
    private final EventPublisher publisher;

    private static final String DEFAULT_JENKINS_SERVER_CONFIG_KEY = "default";

    public ConfigurationPersistenceManager(ActiveObjects ao, PluginLoggerFactory lf, EventPublisher publisher) {
        this.ao = ao;
        this.log = lf.getLoggerForThis(this);
        this.publisher = publisher;
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

    public void setJenkinsServerConfigurationFromRequest(HttpServletRequest req) throws SQLException,
        NumberFormatException {

        String name = req.getParameter("name");
        String url = req.getParameter("url");
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        AuthenticationMode am = AuthenticationMode.fromMode(req.getParameter("authenticationMode")); // TODO
        String stashUsername = req.getParameter("stashUsername");
        String stashPassword = req.getParameter("stashPassword");
        Integer maxVerifyChain = Integer.parseInt(req.getParameter("maxVerifyChain"));

        setJenkinsServerConfiguration(name, url, username, password, am, stashUsername, stashPassword, maxVerifyChain);
    }

    /**
     * @deprecated Use
     *             {@link ConfigurationPersistenceManager#setJenkinsServerConfiguration(String, String, String, String, AuthenticationMode, String, String, Integer)}
     *             instead
     */
    @Deprecated
    public void setJenkinsServerConfiguration(String name, String url,
        String username, String password, String stashUsername, String stashPassword, Integer maxVerifyChain)
        throws SQLException {
        setJenkinsServerConfiguration(name, url, username, password, AuthenticationMode.USERNAME_AND_PASSWORD,
            stashUsername, stashPassword,
            maxVerifyChain);
    }

    public void setJenkinsServerConfiguration(String name, String url,
        String username, String password, AuthenticationMode authenticationMode, String stashUsername,
        String stashPassword, Integer maxVerifyChain)
        throws SQLException {
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
        configs[0].setAuthenticationMode(authenticationMode);
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
        String publishBuildCommand, String prebuildCommand, boolean rebuildOnUpdate)
        throws SQLException, IllegalArgumentException {
        setRepositoryConfigurationForRepository(repo, isCiEnabled,
            verifyBranchRegex, verifyBuildCommand, false,
            "N/A", publishBranchRegex, publishBuildCommand, false, "N/A", prebuildCommand, null, rebuildOnUpdate,
            false, "N/A", null, new EmailSettings());
    }

    public void setRepositoryConfigurationForRepositoryFromRequest(Repository repo, HttpServletRequest req)
        throws SQLException, NumberFormatException {

        Boolean ciEnabled = getBoolean(req, "ciEnabled");
        String publishBranchRegex = req.getParameter("publishBranchRegex");
        String publishBuildCommand = req.getParameter("publishBuildCommand");
        Boolean isPublishPinned = getBoolean(req, "isPublishPinned");
        String publishLabel = req.getParameter("publishLabel");
        String verifyBranchRegex = req.getParameter("verifyBranchRegex");
        String verifyBuildCommand = req.getParameter("verifyBuildCommand");
        Boolean isVerifyPinned = getBoolean(req, "isVerifyPinned");
        String verifyLabel = req.getParameter("verifyLabel");
        String prebuildCommand = req.getParameter("prebuildCommand");
        String jenkinsServerName = req.getParameter("jenkinsServerName");
        String maxVerifyChainStr = req.getParameter("maxVerifyChain");
        Integer maxVerifyChain = null;
        if (maxVerifyChainStr != null && !maxVerifyChainStr.isEmpty()) {
            maxVerifyChain = Integer.parseInt(maxVerifyChainStr);
        }

        Boolean junitEnabled = getBoolean(req, "isJunit");
        String junitPath = req.getParameter("junitPath");
        Boolean rebuildOnUpdate = getBoolean(req, "rebuildOnUpdate");

        EmailSettings emailSettings = getEmailSettings(req);

        setRepositoryConfigurationForRepository(repo, ciEnabled, verifyBranchRegex, verifyBuildCommand, isVerifyPinned,
            verifyLabel, publishBranchRegex, publishBuildCommand, isPublishPinned, publishLabel, prebuildCommand,
            jenkinsServerName, rebuildOnUpdate, junitEnabled, junitPath, maxVerifyChain, emailSettings);
    }

    private EmailSettings getEmailSettings(HttpServletRequest req) {
        Boolean emailNotificationsEnabled = getBoolean(req, "emailNotificationsEnabled");
        String emailRecipients = req.getParameter("emailRecipients");
        Boolean emailDontNotifyEveryUnstableBuild = getBoolean(req, "emailDontNotifyEveryUnstableBuild");
        Boolean emailSendToIndividuals = getBoolean(req, "emailSendToIndividuals");
        Boolean emailPerModuleEmail = getBoolean(req, "emailPerModuleEmail");
        return new EmailSettings(emailNotificationsEnabled, emailRecipients, emailDontNotifyEveryUnstableBuild,
            emailSendToIndividuals, emailPerModuleEmail);
    }

    private boolean getBoolean(HttpServletRequest req, String parameter) {
        return (req.getParameter(parameter) == null) ? false : true;
    }

    public void setRepositoryConfigurationForRepository(Repository repo,
        boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, boolean isVerifyPinned,
        String verifyLabel, String publishBranchRegex,
        String publishBuildCommand, boolean isPublishPinned, String publishLabel, String prebuildCommand,
        String jenkinsServerName, boolean rebuildOnUpdate, boolean isJunitEnabled, String junitPath,
        Integer maxVerifyChain, EmailSettings emailSettings)
        throws SQLException, IllegalArgumentException {
        if (jenkinsServerName == null) {
            jenkinsServerName = DEFAULT_JENKINS_SERVER_CONFIG_KEY;
        }
        validateNameExists(jenkinsServerName);
        RepositoryConfiguration[] repos = ao.find(
            RepositoryConfiguration.class,
            Query.select().where("REPO_ID = ?", repo.getId()));
        if (repos.length == 0) {
            log.info("Creating repository configuration for id: "
                + repo.getId().toString());
            RepositoryConfiguration rc = ao.create(
                RepositoryConfiguration.class,
                new DBParam("REPO_ID", repo.getId()), new DBParam(
                "CI_ENABLED", isCiEnabled), new DBParam(
                "VERIFY_BRANCH_REGEX", verifyBranchRegex),
                new DBParam("VERIFY_BUILD_COMMAND", verifyBuildCommand),
                new DBParam("VERIFY_PINNED", isVerifyPinned),
                new DBParam("VERIFY_LABEL", verifyLabel),
                new DBParam("PUBLISH_BRANCH_REGEX", publishBranchRegex),
                new DBParam("PUBLISH_BUILD_COMMAND", publishBuildCommand),
                new DBParam("PUBLISH_PINNED", isPublishPinned),
                new DBParam("PUBLISH_LABEL", publishLabel),
                new DBParam("PREBUILD_COMMAND", prebuildCommand),
                new DBParam("JENKINS_SERVER_NAME", jenkinsServerName),
                new DBParam("JUNIT_ENABLED", isJunitEnabled),
                new DBParam("JUNIT_PATH", junitPath),
                new DBParam("REBUILD_ON_TARGET_UPDATE", rebuildOnUpdate),
                new DBParam("EMAIL_NOTIFICATIONS_ENABLED", emailSettings.getEmailNotificationsEnabled()),
                new DBParam("EMAIL_FOR_EVERY_UNSTABLE_BUILD", emailSettings.getEmailForEveryUnstableBuild()),
                new DBParam("EMAIL_PER_MODULE_EMAIL", emailSettings.getEmailPerModuleEmail()),
                new DBParam("EMAIL_RECIPIENTS", emailSettings.getEmailRecipients()),
                new DBParam("EMAIL_SEND_TO_INDIVIDUALS", emailSettings.getEmailSendToIndividuals())
            );
            if (maxVerifyChain != null) {
                rc.setMaxVerifyChain(maxVerifyChain);
            }
            rc.save();
            return;
        }
        RepositoryConfiguration foundRepo = repos[0];
        foundRepo.setCiEnabled(isCiEnabled);
        foundRepo.setVerifyBranchRegex(verifyBranchRegex);
        foundRepo.setVerifyBuildCommand(verifyBuildCommand);
        foundRepo.setVerifyPinned(isVerifyPinned);
        foundRepo.setVerifyLabel(verifyLabel);
        foundRepo.setPublishBranchRegex(publishBranchRegex);
        foundRepo.setPublishBuildCommand(publishBuildCommand);
        foundRepo.setPublishPinned(isPublishPinned);
        foundRepo.setPublishLabel(publishLabel);
        foundRepo.setPrebuildCommand(prebuildCommand);
        foundRepo.setJenkinsServerName(jenkinsServerName);
        foundRepo.setJunitEnabled(isJunitEnabled);
        foundRepo.setJunitPath(junitPath);
        foundRepo.setRebuildOnTargetUpdate(rebuildOnUpdate);
        if (maxVerifyChain != null) {
            foundRepo.setMaxVerifyChain(maxVerifyChain);
        }
        foundRepo.setEmailNotificationsEnabled(emailSettings.getEmailNotificationsEnabled());
        foundRepo.setEmailForEveryUnstableBuild(emailSettings.getEmailForEveryUnstableBuild());
        foundRepo.setEmailPerModuleEmail(emailSettings.getEmailPerModuleEmail());
        foundRepo.setEmailRecipients(emailSettings.getEmailRecipients());
        foundRepo.setEmailSendToIndividuals(emailSettings.getEmailSendToIndividuals());
        foundRepo.save();
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
        return getPullRequestMetadata(pr.getToRef().getRepository().getId(), pr.getId(),
            pr.getFromRef().getLatestChangeset().toString(),
            pr.getToRef().getLatestChangeset().toString());
    }

    public PullRequestMetadata getPullRequestMetadata(int repoId, Long prId, String fromSha, String toSha) {
        // We have to check repoId being equal to -1 so that this works with old data.
        PullRequestMetadata[] prms = ao.find(PullRequestMetadata.class,
            "(REPO_ID = ? OR REPO_ID = -1) AND PULL_REQUEST_ID = ? and TO_SHA = ? and FROM_SHA = ?", repoId, prId,
            toSha, fromSha);
        if (prms.length == 0) {
            // new/updated PR, create a new object
            log.info("Creating PR Metadata for pull request: repo id:" + repoId
                + "pr id: " + prId + ", fromSha: " + fromSha + ", toSha: " + toSha);
            PullRequestMetadata prm =
                ao.create(
                    PullRequestMetadata.class,
                    new DBParam("REPO_ID", repoId),
                    new DBParam("PULL_REQUEST_ID", prId),
                    new DBParam("TO_SHA", toSha),
                    new DBParam("FROM_SHA", fromSha));
            prm.save();
            return prm;

        }
        return prms[0];
    }

    public ImmutableList<PullRequestMetadata> getPullRequestMetadataWithoutToRef(PullRequest pr) {
        Long id = pr.getId();
        String fromSha = pr.getFromRef().getLatestChangeset().toString();
        String toSha = pr.getToRef().getLatestChangeset().toString();

        PullRequestMetadata[] prms = ao.find(PullRequestMetadata.class,
            "PULL_REQUEST_ID = ? and FROM_SHA = ?", id, fromSha);
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
            return ImmutableList.of(prm);

        }
        return ImmutableList.copyOf(prms);
    }

    // Automatically sets the fromHash and toHash from the PullRequest object
    public void setPullRequestMetadata(PullRequest pr, Boolean buildStarted,
        Boolean success, Boolean override) {
        setPullRequestMetadata(pr, pr.getFromRef().getLatestChangeset(),
            pr.getToRef().getLatestChangeset(), buildStarted, success, override);
    }

    // Allows fromHash and toHash to be set by the caller, in case we are referring to older commits
    public void setPullRequestMetadata(PullRequest pr, String fromHash, String toHash, Boolean buildStarted,
        Boolean success, Boolean override) {
        PullRequestMetadata prm = getPullRequestMetadata(pr.getToRef().getRepository().getId(), pr.getId(), fromHash,
            toHash);
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
        publisher.publish(new StashbotMetadataUpdatedEvent(this, pr));
    }
}
