// Copyright 2015 Palantir Technologies
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

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import net.java.ao.DBParam;
import net.java.ao.Query;

import org.slf4j.Logger;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.EventPublisher;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.palantir.stash.stashbot.event.StashbotMetadataUpdatedEvent;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.AuthenticationCredential;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.persistence.JobTypeStatusMapping;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class ConfigurationPersistenceImpl implements ConfigurationPersistenceService {

    private final ActiveObjects ao;
    private final Logger log;
    private final EventPublisher publisher;

    private static final String DEFAULT_JENKINS_SERVER_CONFIG_KEY = "default";

    public ConfigurationPersistenceImpl(ActiveObjects ao, PluginLoggerFactory lf, EventPublisher publisher) {
        this.ao = ao;
        this.log = lf.getLoggerForThis(this);
        this.publisher = publisher;
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#deleteJenkinsServerConfiguration(java.lang.String)
     */
    @Override
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

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getJenkinsServerConfiguration(java.lang.String)
     */
    @Override
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

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setJenkinsServerConfigurationFromRequest(javax.servlet.http.HttpServletRequest)
     */
    @Override
    public void setJenkinsServerConfigurationFromRequest(HttpServletRequest req) throws SQLException,
        NumberFormatException {

        String name = req.getParameter("name");
        String url = req.getParameter("url");
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        AuthenticationMode am = AuthenticationMode.fromMode(req.getParameter("authenticationMode"));
        String stashUsername = req.getParameter("stashUsername");
        String stashPassword = req.getParameter("stashPassword");
        Integer maxVerifyChain = Integer.parseInt(req.getParameter("maxVerifyChain"));
        String lockStr = req.getParameter("locked");
        Boolean isLocked = (lockStr != null && lockStr.equals("on"));

        // folder stuff
        String foldersEnabledStr = req.getParameter("foldersEnabled");
        Boolean foldersEnabled = (foldersEnabledStr != null && foldersEnabledStr.equals("on"));
        String subfoldersEnabledStr = req.getParameter("subfoldersEnabled");
        Boolean subfoldersEnabled = (subfoldersEnabledStr != null && subfoldersEnabledStr.equals("on"));
        String folderPrefix = req.getParameter("folderPrefix");
        if (folderPrefix == null || folderPrefix.isEmpty()) {
            folderPrefix = "/";
        }

        setJenkinsServerConfiguration(name, url, username, password, am, stashUsername, stashPassword, maxVerifyChain,
            isLocked, foldersEnabled, subfoldersEnabled, folderPrefix);
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setJenkinsServerConfiguration(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Integer)
     */
    @Override
    @Deprecated
    public void setJenkinsServerConfiguration(String name, String url,
        String username, String password, String stashUsername, String stashPassword, Integer maxVerifyChain)
        throws SQLException {
        setJenkinsServerConfiguration(name, url, username, password, AuthenticationMode.USERNAME_AND_PASSWORD,
            stashUsername, stashPassword, maxVerifyChain, false, false, false, null);
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setJenkinsServerConfiguration(java.lang.String, java.lang.String, java.lang.String, java.lang.String, com.palantir.stash.stashbot.config.JenkinsServerConfiguration.AuthenticationMode, java.lang.String, java.lang.String, java.lang.Integer, java.lang.Boolean)
     */
    @Override
    public void setJenkinsServerConfiguration(String name, String url,
        String username, String password, AuthenticationMode authenticationMode, String stashUsername,
        String stashPassword, Integer maxVerifyChain, Boolean isLocked, Boolean foldersEnabled,
        Boolean subfoldersEnabled, String folderPrefix) throws SQLException {
        if (name == null) {
            name = DEFAULT_JENKINS_SERVER_CONFIG_KEY;
        }
        validateName(name);
        JenkinsServerConfiguration[] configs = ao.find(
            JenkinsServerConfiguration.class,
            Query.select().where("NAME = ?", name));

        if (configs.length == 0) {
            log.info("Creating jenkins configuration: " + name);
            JenkinsServerConfiguration newJSC = ao.create(JenkinsServerConfiguration.class,
                new DBParam("NAME", name),
                new DBParam("URL", url),
                new DBParam("USERNAME", username),
                new DBParam("PASSWORD", password),
                new DBParam("STASH_USERNAME", stashUsername),
                new DBParam("STASH_PASSWORD", stashPassword),
                new DBParam("MAX_VERIFY_CHAIN", maxVerifyChain),
                new DBParam("LOCKED", isLocked),
                new DBParam("FOLDER_SUPPORT", foldersEnabled),
                new DBParam("USE_SUBFOLDERS", subfoldersEnabled)
                );
            // use custom impl to set folder prefix
            newJSC.setFolderPrefix(folderPrefix);
            newJSC.save();
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
        configs[0].setLocked(isLocked);
        configs[0].setFolderSupportEnabled(foldersEnabled);
        configs[0].setUseSubFolders(subfoldersEnabled);
        configs[0].setFolderPrefix(folderPrefix);
        configs[0].save();
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getRepositoryConfigurationForRepository(com.atlassian.bitbucket.repository.Repository)
     */
    @Override
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
            // default the 3 base job types to disabled
            setJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT, false);
            setJobTypeStatusMapping(rc, JobType.VERIFY_PR, false);
            setJobTypeStatusMapping(rc, JobType.PUBLISH, false);
            return rc;
        }
        return repos[0];
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setRepositoryConfigurationForRepository(com.atlassian.bitbucket.repository.Repository, boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean)
     */
    @Override
    public void setRepositoryConfigurationForRepository(Repository repo,
        boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, String publishBranchRegex,
        String publishBuildCommand, String prebuildCommand, boolean rebuildOnUpdate)
        throws SQLException, IllegalArgumentException {
        setRepositoryConfigurationForRepository(repo, isCiEnabled,
            verifyBranchRegex, verifyBuildCommand, false,
            "N/A", publishBranchRegex, publishBuildCommand, false, "N/A", prebuildCommand, null, rebuildOnUpdate,
            false, "N/A", rebuildOnUpdate, null, null, new EmailSettings(), false, false, false, false,
            new BuildTimeoutSettings());
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setRepositoryConfigurationForRepositoryFromRequest(com.atlassian.bitbucket.repository.Repository, javax.servlet.http.HttpServletRequest)
     */
    @Override
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
        Boolean strictVerifyMode = getBoolean(req, "isStrictVerifyMode");
        Boolean preserveJenkinsJobConfig = getBoolean(req, "isPreserveJenkinsJobConfig");

        Boolean junitEnabled = getBoolean(req, "isJunit");
        String junitPath = req.getParameter("junitPath");

        Boolean artifactsEnabled = getBoolean(req, "artifactsEnabled");
        String artifactsPath = req.getParameter("artifactsPath");

        Boolean rebuildOnUpdate = getBoolean(req, "rebuildOnUpdate");

        EmailSettings emailSettings = getEmailSettings(req);

        Boolean timestampJobOutputEnabled = getBoolean(req, "isTimestampJobOutputEnabled");
        Boolean ansiColorJobOutputEnabled = getBoolean(req, "isAnsiColorJobOutputEnabled");
        BuildTimeoutSettings buildTimeoutSettings = getBuildTimeoutSettings(req);

        setRepositoryConfigurationForRepository(repo, ciEnabled, verifyBranchRegex, verifyBuildCommand, isVerifyPinned,
            verifyLabel, publishBranchRegex, publishBuildCommand, isPublishPinned, publishLabel, prebuildCommand,
            jenkinsServerName, rebuildOnUpdate, junitEnabled, junitPath, artifactsEnabled, artifactsPath,
            maxVerifyChain, emailSettings, strictVerifyMode, preserveJenkinsJobConfig,
            timestampJobOutputEnabled, ansiColorJobOutputEnabled, buildTimeoutSettings);
        RepositoryConfiguration rc = getRepositoryConfigurationForRepository(repo);
        setJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT, getBoolean(req, "verificationEnabled"));
        setJobTypeStatusMapping(rc, JobType.VERIFY_PR, getBoolean(req, "verifyPREnabled"));
        setJobTypeStatusMapping(rc, JobType.PUBLISH, getBoolean(req, "publishEnabled"));
    }

    @Override
    public void setJobTypeStatusMapping(RepositoryConfiguration rc, JobType jt, Boolean isEnabled) {
        JobTypeStatusMapping[] mappings =
            ao.find(JobTypeStatusMapping.class, "REPO_CONFIG_ID = ? and JOB_TYPE_RAW = ?", rc.getID(), jt.name());
        if (mappings.length == 0) {
            ao.create(JobTypeStatusMapping.class,
                new DBParam("REPO_CONFIG_ID", rc.getID()),
                new DBParam("JOB_TYPE_RAW", jt.name()),
                new DBParam("IS_ENABLED", isEnabled)).save();
            return;
        }
        mappings[0].setIsEnabled(isEnabled);
        mappings[0].save();
    }

    @Override
    public Boolean getJobTypeStatusMapping(RepositoryConfiguration rc, JobType jt) {
        JobTypeStatusMapping[] mappings =
            ao.find(JobTypeStatusMapping.class, "REPO_CONFIG_ID = ? and JOB_TYPE_RAW = ?", rc.getID(), jt.name());
        if (mappings.length == 0) {
            return false;
        }
        return mappings[0].getIsEnabled();
    }

    private EmailSettings getEmailSettings(HttpServletRequest req) {
        Boolean emailNotificationsEnabled = getBoolean(req, "isEmailNotificationsEnabled");
        String emailRecipients = req.getParameter("emailRecipients");
        Boolean emailDontNotifyEveryUnstableBuild = getBoolean(req, "isEmailForEveryUnstableBuild");
        Boolean emailSendToIndividuals = getBoolean(req, "isEmailSendToIndividuals");
        Boolean emailPerModuleEmail = getBoolean(req, "isEmailPerModuleEmail");
        return new EmailSettings(emailNotificationsEnabled, emailRecipients, emailDontNotifyEveryUnstableBuild,
            emailSendToIndividuals, emailPerModuleEmail);
    }

    private BuildTimeoutSettings getBuildTimeoutSettings(HttpServletRequest req) {
        Boolean buildTimeoutEnabled = getBoolean(req, "isBuildTimeoutEnabled");
        Integer buildTimeout = Integer.parseInt(req.getParameter("buildTimeout"));
        return new BuildTimeoutSettings(buildTimeoutEnabled, buildTimeout);
    }

    private boolean getBoolean(HttpServletRequest req, String parameter) {
        return (req.getParameter(parameter) == null) ? false : true;
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setRepositoryConfigurationForRepository(com.atlassian.bitbucket.repository.Repository, boolean, java.lang.String, java.lang.String, boolean, java.lang.String, java.lang.String, java.lang.String, boolean, java.lang.String, java.lang.String, java.lang.String, boolean, boolean, java.lang.String, boolean, java.lang.String, java.lang.Integer, com.palantir.stash.stashbot.config.EmailSettings, boolean, java.lang.Boolean)
     */
    @Override
    public void
        setRepositoryConfigurationForRepository(Repository repo,
            boolean isCiEnabled, String verifyBranchRegex,
            String verifyBuildCommand, boolean isVerifyPinned,
            String verifyLabel, String publishBranchRegex,
            String publishBuildCommand, boolean isPublishPinned, String publishLabel, String prebuildCommand,
            String jenkinsServerName, boolean rebuildOnUpdate, boolean isJunitEnabled, String junitPath,
            boolean artifactsEnabled, String artifactsPath, Integer maxVerifyChain, EmailSettings emailSettings,
            boolean strictVerifyMode, Boolean preserveJenkinsJobConfig,
            boolean timestampJobOutputEnabled, boolean ansiColorJobOutputEnabled,
            BuildTimeoutSettings buildTimeoutSettings)
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
                + repo.getId());
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
                new DBParam("ARTIFACTS_ENABLED", artifactsEnabled),
                new DBParam("ARTIFACTS_PATH", artifactsPath),
                new DBParam("REBUILD_ON_TARGET_UPDATE", rebuildOnUpdate),
                new DBParam("EMAIL_NOTIFICATIONS_ENABLED", emailSettings.getEmailNotificationsEnabled()),
                new DBParam("EMAIL_FOR_EVERY_UNSTABLE_BUILD", emailSettings.getEmailForEveryUnstableBuild()),
                new DBParam("EMAIL_PER_MODULE_EMAIL", emailSettings.getEmailPerModuleEmail()),
                new DBParam("EMAIL_RECIPIENTS", emailSettings.getEmailRecipients()),
                new DBParam("EMAIL_SEND_TO_INDIVIDUALS", emailSettings.getEmailSendToIndividuals()),
                new DBParam("STRICT_VERIFY_MODE", strictVerifyMode),
                new DBParam("PRESERVE_JENKINS_JOB_CONFIG", preserveJenkinsJobConfig),
                new DBParam("TIMESTAMPS_ENABLED", timestampJobOutputEnabled),
                new DBParam("ANSICOLOR_ENABLED", ansiColorJobOutputEnabled),
                new DBParam("BUILD_TIMEOUT_ENABLED", buildTimeoutSettings.getBuildTimeoutEnabled()),
                new DBParam("BUILD_TIMEOUT", buildTimeoutSettings.getBuildTimeout())
                );
            if (maxVerifyChain != null) {
                rc.setMaxVerifyChain(maxVerifyChain);
            }
            rc.save();
            // default the 3 base job types to enabled
            setJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT, true);
            setJobTypeStatusMapping(rc, JobType.VERIFY_PR, true);
            setJobTypeStatusMapping(rc, JobType.PUBLISH, true);
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
        foundRepo.setArtifactsEnabled(artifactsEnabled);
        foundRepo.setArtifactsPath(artifactsPath);
        foundRepo.setRebuildOnTargetUpdate(rebuildOnUpdate);
        if (maxVerifyChain != null) {
            foundRepo.setMaxVerifyChain(maxVerifyChain);
        }
        foundRepo.setEmailNotificationsEnabled(emailSettings.getEmailNotificationsEnabled());
        foundRepo.setEmailForEveryUnstableBuild(emailSettings.getEmailForEveryUnstableBuild());
        foundRepo.setEmailPerModuleEmail(emailSettings.getEmailPerModuleEmail());
        foundRepo.setEmailRecipients(emailSettings.getEmailRecipients());
        foundRepo.setEmailSendToIndividuals(emailSettings.getEmailSendToIndividuals());
        foundRepo.setStrictVerifyMode(strictVerifyMode);
        foundRepo.setPreserveJenkinsJobConfig(preserveJenkinsJobConfig);
        foundRepo.setTimestampJobOutputEnabled(timestampJobOutputEnabled);
        foundRepo.setAnsiColorJobOutputEnabled(ansiColorJobOutputEnabled);
        foundRepo.setBuildTimeoutEnabled(buildTimeoutSettings.getBuildTimeoutEnabled());
        foundRepo.setBuildTimeout(buildTimeoutSettings.getBuildTimeout());
        foundRepo.save();
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getAllJenkinsServerConfigurations()
     */
    @Override
    public ImmutableCollection<JenkinsServerConfiguration> getAllJenkinsServerConfigurations()
        throws SQLException {
        JenkinsServerConfiguration[] allConfigs = ao
            .find(JenkinsServerConfiguration.class);
        if (allConfigs.length == 0) {
            return ImmutableList.of(getJenkinsServerConfiguration(null));
        }
        return ImmutableList.copyOf(allConfigs);
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getAllJenkinsServerNames()
     */
    @Override
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

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#validateName(java.lang.String)
     */
    @Override
    public void validateName(String name) throws IllegalArgumentException {
        if (!name.matches("[a-zA-Z0-9-]+")) {
            throw new IllegalArgumentException("Name must match [a-zA-Z0-9]+");
        }
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#validateNameExists(java.lang.String)
     */
    @Override
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
            + pr.getFromRef().getLatestCommit() + ", to:"
            + pr.getToRef().getLatestCommit() + "]";
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getPullRequestMetadata(com.atlassian.bitbucket.pull.PullRequest)
     */
    @Override
    public PullRequestMetadata getPullRequestMetadata(PullRequest pr) {
        return getPullRequestMetadata(pr.getToRef().getRepository().getId(), pr.getId(),
            pr.getFromRef().getLatestCommit().toString(),
            pr.getToRef().getLatestCommit().toString());
    }

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getPullRequestMetadata(int, java.lang.Long, java.lang.String, java.lang.String)
     */
    @Override
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

    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#getPullRequestMetadataWithoutToRef(com.atlassian.bitbucket.pull.PullRequest)
     */
    @Override
    public ImmutableList<PullRequestMetadata> getPullRequestMetadataWithoutToRef(PullRequest pr) {
        Long id = pr.getId();
        String fromSha = pr.getFromRef().getLatestCommit().toString();
        String toSha = pr.getToRef().getLatestCommit().toString();

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
    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setPullRequestMetadata(com.atlassian.bitbucket.pull.PullRequest, java.lang.Boolean, java.lang.Boolean, java.lang.Boolean)
     */
    @Override
    public void setPullRequestMetadata(PullRequest pr, Boolean buildStarted,
        Boolean success, Boolean override) {
        setPullRequestMetadata(pr, pr.getFromRef().getLatestCommit(),
            pr.getToRef().getLatestCommit(), buildStarted, success, override);
    }

    // Allows fromHash and toHash to be set by the caller, in case we are referring to older commits
    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setPullRequestMetadata(com.atlassian.bitbucket.pull.PullRequest, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.Boolean, java.lang.Boolean)
     */
    @Override
    public void setPullRequestMetadata(PullRequest pr, String fromHash, String toHash, Boolean buildStarted,
        Boolean success, Boolean override) {
        setPullRequestMetadata(pr, fromHash, toHash, buildStarted, success, override, null);
    }

    // Allows fromHash and toHash to be set by the caller, in case we are referring to older commits
    /* (non-Javadoc)
     * @see com.palantir.stash.stashbot.config.ConfigurationPersistenceService#setPullRequestMetadata(com.atlassian.bitbucket.pull.PullRequest, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.Boolean, java.lang.Boolean, java.lang.Boolean)
     */
    @Override
    public void setPullRequestMetadata(PullRequest pr, String fromHash, String toHash, Boolean buildStarted,
        Boolean success, Boolean override, Boolean failed) {
        PullRequestMetadata prm =
            getPullRequestMetadata(pr.getToRef().getRepository().getId(), pr.getId(), fromHash, toHash);
        if (buildStarted != null) {
            prm.setBuildStarted(buildStarted);
        }
        if (success != null) {
            prm.setSuccess(success);
        }
        if (override != null) {
            prm.setOverride(override);
        }
        if (failed != null) {
            prm.setFailed(failed);
        }

        prm.save();
        publisher.publish(new StashbotMetadataUpdatedEvent(this, pr));
    }

    private AuthenticationCredential getDefaultAuthenticationCredential() {
        AuthenticationCredential[] acs = ao.find(AuthenticationCredential.class, "NAME = ?", "default");
        if (acs.length == 0) {
            log.info("Generating SSH key for default:");
            JSch jsch = new JSch();
            KeyPair kp;
            try {
                kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
            } catch (JSchException e) {
                log.error("Unable to generate ssh key", e);
                // seriously, I have no idea what to do here.
                throw new RuntimeException(e);
            }
            ByteArrayOutputStream privKey = new ByteArrayOutputStream();
            ByteArrayOutputStream pubKey = new ByteArrayOutputStream();
            kp.writePrivateKey(privKey);
            kp.writePublicKey(pubKey, "default");

            log.info("Public Key:\n\n" + pubKey.toString());
            log.info("Private Key:\n\n" + privKey.toString());

            AuthenticationCredential ac = ao.create(AuthenticationCredential.class,
                new DBParam("NAME", "default"),
                new DBParam("PUBLIC_KEY", pubKey.toString()),
                new DBParam("PRIVATE_KEY", privKey.toString()));
            ac.save();
            return ac;
        }
        return acs[0];
    }

    @Override
    public String getDefaultPublicSshKey() {
        return getDefaultAuthenticationCredential().getPublicKey();
    }

    @Override
    public String getDefaultPrivateSshKey() {
        return getDefaultAuthenticationCredential().getPrivateKey();
    }
}
