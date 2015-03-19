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
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration.AuthenticationMode;

@Transactional
public interface ConfigurationPersistenceService {

    public abstract void deleteJenkinsServerConfiguration(String name);

    public abstract JenkinsServerConfiguration getJenkinsServerConfiguration(String name)
        throws SQLException;

    public abstract void setJenkinsServerConfigurationFromRequest(HttpServletRequest req) throws SQLException,
        NumberFormatException;

    /**
     * @deprecated Use
     *             {@link ConfigurationPersistenceImpl#setJenkinsServerConfiguration(String, String, String, String, AuthenticationMode, String, String, Integer)}
     *             instead
     */
    @Deprecated
    public abstract void setJenkinsServerConfiguration(String name, String url,
        String username, String password, String stashUsername, String stashPassword, Integer maxVerifyChain)
        throws SQLException;

    public abstract void setJenkinsServerConfiguration(String name, String url,
        String username, String password, AuthenticationMode authenticationMode, String stashUsername,
        String stashPassword, Integer maxVerifyChain, Boolean isLocked)
        throws SQLException;

    public abstract RepositoryConfiguration getRepositoryConfigurationForRepository(
        Repository repo) throws SQLException;

    public abstract void setRepositoryConfigurationForRepository(Repository repo,
        boolean isCiEnabled, String verifyBranchRegex,
        String verifyBuildCommand, String publishBranchRegex,
        String publishBuildCommand, String prebuildCommand, boolean rebuildOnUpdate)
        throws SQLException, IllegalArgumentException;

    public abstract void setRepositoryConfigurationForRepositoryFromRequest(Repository repo, HttpServletRequest req)
        throws SQLException, NumberFormatException;

    public abstract void
        setRepositoryConfigurationForRepository(Repository repo,
            boolean isCiEnabled, String verifyBranchRegex,
            String verifyBuildCommand, boolean isVerifyPinned,
            String verifyLabel, String publishBranchRegex,
            String publishBuildCommand, boolean isPublishPinned, String publishLabel, String prebuildCommand,
            String jenkinsServerName, boolean rebuildOnUpdate, boolean isJunitEnabled, String junitPath,
            boolean artifactsEnabled, String artifactsPath, Integer maxVerifyChain, EmailSettings emailSettings,
            boolean strictVerifyMode, Boolean preserveJenkinsJobConfig)
            throws SQLException, IllegalArgumentException;

    public abstract ImmutableCollection<JenkinsServerConfiguration> getAllJenkinsServerConfigurations()
        throws SQLException;

    public abstract ImmutableCollection<String> getAllJenkinsServerNames()
        throws SQLException;

    public abstract void validateName(String name) throws IllegalArgumentException;

    public abstract void validateNameExists(String name) throws IllegalArgumentException;

    public abstract PullRequestMetadata getPullRequestMetadata(PullRequest pr);

    public abstract PullRequestMetadata getPullRequestMetadata(int repoId, Long prId, String fromSha, String toSha);

    public abstract ImmutableList<PullRequestMetadata> getPullRequestMetadataWithoutToRef(PullRequest pr);

    // Automatically sets the fromHash and toHash from the PullRequest object
    public abstract void setPullRequestMetadata(PullRequest pr, Boolean buildStarted,
        Boolean success, Boolean override);

    // Allows fromHash and toHash to be set by the caller, in case we are referring to older commits
    public abstract void setPullRequestMetadata(PullRequest pr, String fromHash, String toHash, Boolean buildStarted,
        Boolean success, Boolean override);

    // Allows fromHash and toHash to be set by the caller, in case we are referring to older commits
    public abstract void setPullRequestMetadata(PullRequest pr, String fromHash, String toHash, Boolean buildStarted,
        Boolean success, Boolean override, Boolean failed);

}