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
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.atlassian.stash.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitScmCommandBuilder;
import com.atlassian.stash.scm.git.revlist.GitRevListBuilder;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.outputhandler.CommandOutputHandlerFactory;

// TODO: listen for push event instead of implementing hook so we don't have to activate it
// SEE:
// https://developer.atlassian.com/stash/docs/latest/reference/plugin-module-types/post-receive-hook-plugin-module.html
public class TriggerJenkinsBuildHook implements AsyncPostReceiveRepositoryHook {

    private final ConfigurationPersistenceManager cpm;
    private final JenkinsManager jenkinsManager;
    private final GitCommandBuilderFactory gcbf;
    private final CommandOutputHandlerFactory cohf;
    private final Logger log;

    public TriggerJenkinsBuildHook(ConfigurationPersistenceManager cpm, JenkinsManager jenkinsManager,
        GitCommandBuilderFactory gcbf, CommandOutputHandlerFactory cohf, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
        this.gcbf = gcbf;
        this.cohf = cohf;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void postReceive(@Nonnull RepositoryHookContext rhc, @Nonnull Collection<RefChange> changes) {
        Repository repo = rhc.getRepository();
        RepositoryConfiguration rc;
        try {
            rc = cpm.getRepositoryConfigurationForRepository(repo);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get repositoryConfiguration for repo " + repo.toString());
        }

        if (!rc.getCiEnabled()) {
            log.debug("CI disabled for repo " + repo.getName());
            return;
        }

        Set<String> publishBuilds = new HashSet<String>();

        // First trigger all publish builds
        for (RefChange refChange : changes) {
            if (!refChange.getRefId().matches(rc.getPublishBranchRegex())) {
                continue;
            }

            // deletes have a tohash of "0000000000000000000000000000000000000000"
            // but it seems more reliable to use RefChangeType
            if (refChange.getType().equals(RefChangeType.DELETE)) {
                log.debug("Detected delete, not triggering a build for this change");
                continue;
            }

            // if matches publication regex, no verify build needed for that hash
            // Only perform publish builds of the "to ref", not commits between
            // I.E. if you have A-B-C and you push -D-E-F, a verify build of D and E might be triggered, but F would be
            // published and not verified, if the ref matches both build and verify.
            log.info("Triggering PUBLISH build for " + repo.toString() + " hash " + refChange.getToHash());
            // trigger a publication build
            jenkinsManager.triggerBuild(repo, JobType.PUBLISH, refChange.getToHash());
            publishBuilds.add(refChange.getToHash());
        }

        Set<String> verifiedBuilds = new HashSet<String>();

        // Now look for commits to verify
        for (RefChange refChange : changes) {
            if (!refChange.getRefId().matches(rc.getVerifyBranchRegex())) {
                continue;
            }

            // deletes have a tohash of "0000000000000000000000000000000000000000"
            // but it seems more reliable to use RefChangeType
            if (refChange.getType().equals(RefChangeType.DELETE)) {
                log.debug("Detected delete, not triggering a build for this change");
                continue;
            }

            // We want to trigger a build for each new commit that was pushed
            GitScmCommandBuilder gscb = gcbf.builder(repo);
            GitRevListBuilder grlb = gscb.revList();
            if (refChange.getType().equals(RefChangeType.ADD)) {
                // then we want to build all refs in the history that aren't already in another branch, up to the limit.
                log.debug("Detected add, considering entire history for verification");
                grlb.revs(refChange.getToHash());
            } else {
                log.debug("Detected update, considering new commits for verification");
                grlb.revs("^" + refChange.getFromHash(), refChange.getToHash());
            }

            Integer maxVerifyChain = getMaxVerifyChain(rc);
            if (maxVerifyChain != 0) {
                log.debug("Limiting to " + maxVerifyChain.toString() + " commits for verification");
                grlb.limit(maxVerifyChain);
            }
            CommandOutputHandler<Object> rloh = cohf.getRevlistOutputHandler();
            grlb.build(rloh).call();

            // returns in old-to-new order, already limited by max-verify-build limiter
            @SuppressWarnings("unchecked")
            ImmutableList<String> changesets = (ImmutableList<String>) rloh.getOutput();

            // For each hash
            for (String cs : changesets) {

                if (publishBuilds.contains(cs)) {
                    log.info("NOT Triggering VERIFICATION build for commit " + cs
                        + " because it already triggered a publish build");
                    continue;
                }
                if (verifiedBuilds.contains(cs)) {
                    log.info("NOT Triggering VERIFICATION build for commit " + cs
                        + " because it already triggered a verify build");
                    continue;
                }
                if (isInAnotherVerifiedRef(repo, rc, cs, refChange.getRefId())) {
                    log.info("NOT Triggering VERIFICATION build for commit " + cs
                        + " because it is already contained in another branch");
                    continue;
                }
                log.info("Triggering VERIFICATION build for commit " + cs);
                // trigger a verification build (no merge)
                jenkinsManager.triggerBuild(repo, JobType.VERIFY_COMMIT, cs);
                verifiedBuilds.add(cs);
            }
        }
    }

    private boolean isInAnotherVerifiedRef(Repository repo, RepositoryConfiguration rc, String sha1, String currentRef) {
        GitScmCommandBuilder gcb = gcbf.builder(repo).command("branch").argument("--contains").argument(sha1);
        CommandOutputHandler<Object> gboh = cohf.getBranchContainsOutputHandler();
        gcb.build(gboh).call();
        @SuppressWarnings("unchecked")
        ImmutableList<String> branches = (ImmutableList<String>) gboh.getOutput();
        for (String branch : branches) {
            if (!branch.equals(currentRef)) {
                // see if branch is currently configured to verify
                if (branch.matches(rc.getVerifyBranchRegex())) {
                    log.info("Found commit " + sha1 + " is contained in branch " + branch
                        + " which matches verify regex so not triggering build");
                    return true;
                }
            }
        }
        return false;
    }

    private Integer getMaxVerifyChain(RepositoryConfiguration rc) {
        JenkinsServerConfiguration jsc;
        try {
            jsc = cpm.getJenkinsServerConfiguration(rc.getJenkinsServerName());
        } catch (SQLException e) {
            log.error("Error getting jenkins server configuration for repo id " + rc.getRepoId().toString(), e);
            return rc.getMaxVerifyChain();
        }
        Integer serverMaxChain = jsc.getMaxVerifyChain();
        Integer repoMaxChain = rc.getMaxVerifyChain();

        if (serverMaxChain == 0) {
            // no server limits, just use repo limit
            return repoMaxChain;
        }
        if (repoMaxChain == 0) {
            // repo unlimited, server limited, use server limit
            return serverMaxChain;
        }
        // neither is unlimited, return lower limit
        if (serverMaxChain < repoMaxChain) {
            return serverMaxChain;
        }
        return repoMaxChain;
    }
}
