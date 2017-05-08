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

import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

import com.atlassian.activeobjects.tx.Transactional;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

@Transactional
public interface ConfigurationPersistenceService {

	public abstract void deleteJenkinsServerConfiguration(String name);

	public abstract JenkinsServerConfiguration getJenkinsServerConfiguration(
			String name) throws SQLException;

	public abstract void setJenkinsServerConfigurationFromRequest(
			HttpServletRequest req) throws SQLException, NumberFormatException;

	/**
	 * @deprecated Use
	 *             {@link ConfigurationPersistenceImpl#setJenkinsServerConfiguration(String, String, String, String, AuthenticationMode, String, String, Integer)}
	 *             instead
	 */
	@Deprecated
	public abstract void setJenkinsServerConfiguration(String name, String url,
			String username, String password, String stashUsername,
			String stashPassword, Integer maxVerifyChain) throws SQLException;

	@Deprecated
	public abstract void setJenkinsServerConfiguration(String name, String url,
			String username, String password,
			AuthenticationMode authenticationMode, String stashUsername,
			String stashPassword, Integer maxVerifyChain, Boolean isLocked)
					throws SQLException;

	public abstract void setJenkinsServerConfiguration(String name, String url,
			String username, String password,
			AuthenticationMode authenticationMode, String stashUsername,
			String stashPassword, Integer maxVerifyChain, Integer defaultTimeout,
			GlobalBuildCommandSettings globalBuildCommands,
        String prefixTemplate, String jobTemplate, Boolean isLocked) throws SQLException;

	public abstract RepositoryConfiguration getRepositoryConfigurationForRepository(
			Repository repo) throws SQLException;

	public abstract void setRepositoryConfigurationForRepository(
			Repository repo, boolean isCiEnabled, String verifyBranchRegex,
			String verifyBuildCommand, String publishBranchRegex,
			String publishBuildCommand, String prebuildCommand,
			boolean rebuildOnUpdate) throws SQLException,
			IllegalArgumentException;

	public abstract void setRepositoryConfigurationForRepositoryFromRequest(
			Repository repo, HttpServletRequest req) throws SQLException,
			NumberFormatException;

	public abstract void setRepositoryConfigurationForRepository(
			Repository repo, boolean isCiEnabled, String verifyBranchRegex,
			String verifyBuildCommand, boolean isVerifyPinned,
			String verifyLabel, String publishBranchRegex,
			String publishBuildCommand, boolean isPublishPinned,
			String publishLabel, String prebuildCommand,
			String jenkinsServerName, boolean rebuildOnUpdate,
			boolean isJunitEnabled, String junitPath, boolean artifactsEnabled,
			String artifactsPath, Integer maxVerifyChain,
			EmailSettings emailSettings, boolean strictVerifyMode,
			Boolean preserveJenkinsJobConfig, Integer buildTimeout,
			BuildResultExpirySettings expirySettings) throws SQLException,
			IllegalArgumentException;

	public abstract ImmutableCollection<JenkinsServerConfiguration> getAllJenkinsServerConfigurations()
			throws SQLException;

	public abstract ImmutableCollection<String> getAllJenkinsServerNames()
			throws SQLException;

	public abstract void validateDefaultTimeout (Integer defaultTimeout)
	        throws IllegalArgumentException;

	public abstract void validateBuildTimeout (Integer buildTimeout)
	        throws IllegalArgumentException;

	public abstract void validateName(String name)
			throws IllegalArgumentException;

	public abstract void validateNameExists(String name)
			throws IllegalArgumentException;

	public abstract PullRequestMetadata getPullRequestMetadata(PullRequest pr);

	public abstract PullRequestMetadata getPullRequestMetadata(int repoId,
			Long prId, String fromSha, String toSha);

	public abstract ImmutableList<PullRequestMetadata> getPullRequestMetadataWithoutToRef(
			PullRequest pr);

	// Automatically sets the fromHash and toHash from the PullRequest object
	public abstract void setPullRequestMetadata(PullRequest pr,
			Boolean buildStarted, Boolean success, Boolean override);

	// Allows fromHash and toHash to be set by the caller, in case we are
	// referring to older commits
	public abstract void setPullRequestMetadata(PullRequest pr,
			String fromHash, String toHash, Boolean buildStarted,
			Boolean success, Boolean override);

	// Allows fromHash and toHash to be set by the caller, in case we are
	// referring to older commits
	public abstract void setPullRequestMetadata(PullRequest pr,
			String fromHash, String toHash, Boolean buildStarted,
			Boolean success, Boolean override, Boolean failed);

	public abstract Boolean getJobTypeStatusMapping(RepositoryConfiguration rc,
			JobType jt);

	public abstract void setJobTypeStatusMapping(RepositoryConfiguration rc,
			JobType jt, Boolean isEnabled);

	/*
	 * A class to contain the various values related
	 * to build expiry rules in Jenkins
	 */
	public static class BuildResultExpirySettings {
	    public static final String MAX_DAYS = "365";
	    public static final String MAX_NUMBER = "1000";

	    public static final String DEFAULT_VERIFY_DAYS = "30";
	    public static final String DEFAULT_VERIFY_NUMBER = "100";
	    public static final String DEFAULT_PUBLISH_DAYS = "30";
	    public static final String DEFAULT_PUBLISH_NUMBER = "100";

	    private final Integer verifyDays;
	    private final Integer verifyNumber;
	    private final Integer publishDays;
	    private final Integer publishNumber;

	    public BuildResultExpirySettings() {
	        this(Integer.parseInt(DEFAULT_VERIFY_DAYS), Integer.parseInt(DEFAULT_VERIFY_NUMBER),
	                Integer.parseInt(DEFAULT_PUBLISH_DAYS), Integer.parseInt(DEFAULT_PUBLISH_NUMBER));
	    }

	    public BuildResultExpirySettings(Integer verifyDays, Integer verifyNumber,
	            Integer publishDays, Integer publishNumber) {
	        this.verifyDays = verifyDays;
	        this.verifyNumber = verifyNumber;
	        this.publishDays = publishDays;
	        this.publishNumber = publishNumber;
	    }

        public Integer getVerifyDays() {
            return verifyDays;
        }

        public Integer getVerifyNumber() {
            return verifyNumber;
        }

        public Integer getPublishDays() {
            return publishDays;
        }

        public Integer getPublishNumber() {
            return publishNumber;
        }
	}

	/*
	 * A class to contain all our build
	 * commands at the Global or Jenkins level.
	 */
	public static class GlobalBuildCommandSettings {
	    private final String prebuild;

	    public GlobalBuildCommandSettings() {
	        this("/bin/true");
	    }

	    public GlobalBuildCommandSettings(String prebuild) {
	        this.prebuild = prebuild;
	    }

	    public String getPrebuild() {
	        return this.prebuild;
	    }
	}


	public static class EmailSettings {

		private final Boolean emailNotificationsEnabled;
		private final String emailRecipients;
		private final Boolean emailForEveryUnstableBuild;
		private final Boolean emailSendToIndividuals;
		private final Boolean emailPerModuleEmail;

		public EmailSettings() {
			this(false, "", false, false, false);
		}

		public EmailSettings(Boolean emailNotificationsEnabled,
				String emailRecipients, Boolean emailForEveryUnstableBuild,
				Boolean emailSendToIndividuals, Boolean emailPerModuleEmail) {
			this.emailNotificationsEnabled = emailNotificationsEnabled;
			this.emailRecipients = emailRecipients;
			this.emailForEveryUnstableBuild = emailForEveryUnstableBuild;
			this.emailSendToIndividuals = emailSendToIndividuals;
			this.emailPerModuleEmail = emailPerModuleEmail;
		}

		public Boolean getEmailNotificationsEnabled() {
			return emailNotificationsEnabled;
		}

		public String getEmailRecipients() {
			return emailRecipients;
		}

		public Boolean getEmailForEveryUnstableBuild() {
			return emailForEveryUnstableBuild;
		}

		public Boolean getEmailSendToIndividuals() {
			return emailSendToIndividuals;
		}

		public Boolean getEmailPerModuleEmail() {
			return emailPerModuleEmail;
		}
	}

}