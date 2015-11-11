// Copyright 2014 Palantir Technologies
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

import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.PostReceiveHook;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.GitScmCommandBuilder;
import com.atlassian.bitbucket.scm.git.command.revlist.GitRevListBuilder;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.outputhandler.CommandOutputHandlerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

/*
 * NOTE: this cannot be an async hook, nor a repositorypushevent listener, because frequently people will merge a pull
 * request and check the "delete this branch" checkbox, meaning the branch goes away during the merge, so anything
 * running later (or asynchronously) will see the commit as not existing in any other branches, and thus will try to
 * build it a second time.
 * Note that PostReceiveHook does NOT get run when a PR is merged, because stupid APIs are stupid.
 * That case is handled separately in the PullRequestListener class
 */
public class TriggerJenkinsBuildHook implements PostReceiveHook {

    private final ConfigurationPersistenceService cpm;
    private final JenkinsManager jenkinsManager;
    private final GitCommandBuilderFactory gcbf;
    private final CommandOutputHandlerFactory cohf;
    private final Logger log;

    public TriggerJenkinsBuildHook(ConfigurationPersistenceService cpm, JenkinsManager jenkinsManager,
        GitCommandBuilderFactory gcbf, CommandOutputHandlerFactory cohf, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
        this.gcbf = gcbf;
        this.cohf = cohf;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void onReceive(@Nonnull Repository repo, @Nonnull Collection<RefChange> changes,
        @Nonnull HookResponse response) {
        final RepositoryConfiguration rc;
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

        // First trigger all publish builds (if they are enabled)
        if (cpm.getJobTypeStatusMapping(rc, JobType.PUBLISH)) {
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
                log.info("Stashbot Trigger: Triggering PUBLISH build for commit " + refChange.getToHash());
                // trigger a publication build
                jenkinsManager.triggerBuild(repo, JobType.PUBLISH, refChange.getToHash(), refChange.getRefId());
                publishBuilds.add(refChange.getToHash());
            }
        }

        // Nothing to do if VERIFY_COMMIT not enabled
        if (!cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT)) {
            return;
        }
        // Calculate the sum of all new commits introduced by this change
        // This would be:
        // (existing refs matching regex, deleted refs, changed refs old values)..(added refs, changed refs new values)

        // We will need a list of branches first
        GitScmCommandBuilder gcb = gcbf.builder(repo).command("branch");
        CommandOutputHandler<Object> gboh = cohf.getBranchContainsOutputHandler();
        gcb.build(gboh).call();
        @SuppressWarnings("unchecked")
        ImmutableList<String> branches = (ImmutableList<String>) gboh.getOutput();

        HashSet<String> plusBranches = new HashSet<String>();
        HashSet<String> minusBranches = new HashSet<String>();

        // add verify-matching branches to the minusBranches set
        minusBranches.addAll(ImmutableList.copyOf(Iterables.filter(branches, new Predicate<String>() {

            @Override
            public boolean apply(String input) {
                if (input.matches(rc.getVerifyBranchRegex())) {
                    return true;
                }
                return false;
            }
        })));

        // now calculate the changed/added/deleted refs
        for (RefChange refChange : changes) {
            if (!refChange.getRefId().matches(rc.getVerifyBranchRegex())) {
                continue;
            }

            // Since we are a verify branch that changed, we need to not be in minusBranches anymore
            minusBranches.remove(refChange.getRefId());

            switch (refChange.getType()) {
            case DELETE:
                minusBranches.add(refChange.getFromHash());
                break;
            case ADD:
                plusBranches.add(refChange.getToHash());
                break;
            case UPDATE:
                minusBranches.add(refChange.getFromHash());
                plusBranches.add(refChange.getToHash());
                break;
            default:
                throw new IllegalStateException("Unknown change type " + refChange.getType().toString());
            }
        }

        // we can now calculate all the new commits introduced by this change in one revwalk.
        GitScmCommandBuilder gscb = gcbf.builder(repo);
        GitRevListBuilder grlb = gscb.revList();
        for (String mb : minusBranches) {
            grlb.revs("^" + mb);
        }
        for (String pb : plusBranches) {
            grlb.revs(pb);
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

        // For each new commit
        for (String cs : changesets) {

            if (publishBuilds.contains(cs)) {
                log.info("Stashbot Trigger: NOT triggering VERIFICATION build for commit " + cs
                    + " because it already triggered a PUBLISH build");
                continue;
            }
            log.info("Stashbot Trigger: Triggering VERIFICATION build for commit " + cs);
            // trigger a verification build (no merge)
            jenkinsManager.triggerBuild(repo, JobType.VERIFY_COMMIT, cs, "");
        }
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
