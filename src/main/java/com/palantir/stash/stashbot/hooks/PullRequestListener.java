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

import org.slf4j.Logger;

import com.atlassian.bitbucket.comment.Comment;
import com.atlassian.bitbucket.event.pull.PullRequestCommentEvent;
import com.atlassian.bitbucket.event.pull.PullRequestMergedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.EventListener;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

/**
 * This class listens for new pull requests or pull request updates and triggers
 * a verify build if the target repository has CI enabled and the target ref
 * matches the verify regex (or triggers an updated build if the to/from sha has
 * changed).
 * 
 * @author cmyers
 * 
 */
public class PullRequestListener {

    private static final String OVERRIDE_STRING = "==OVERRIDE==";

    private final ConfigurationPersistenceService cpm;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public PullRequestListener(ConfigurationPersistenceService cpm,
        JenkinsManager jenkinsManager, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
        this.log = lf.getLoggerForThis(this);
    }

    @EventListener
    public void listenForPRCreates(PullRequestOpenedEvent event) {
        updatePr(event.getPullRequest());
    }

    @EventListener
    public void listenForComments(PullRequestCommentEvent event) {
        try {
            final PullRequest pr = event.getPullRequest();
            final Repository repo = pr.getToRef().getRepository();
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString()
                    + " ignored, CI not enabled for target repo "
                    + repo.toString());
                return;
            }

            Comment c = event.getComment();
            if (c.getText().contains(OVERRIDE_STRING)) {
                log.debug("Pull Request override set to true for PR "
                    + pr.toString());
                cpm.setPullRequestMetadata(pr, null, null, true);
            }
        } catch (SQLException e) {
            log.error("Error getting repository configuration", e);
        }
    }

    // This event signifies that the PR has already been merged, we don't need to worry about VERIFY_PR anymore, only VERIFY_COMMIT or PUBLISH.
    @EventListener
    public void listenForMerged(PullRequestMergedEvent event) {
        try {
            final PullRequest pr = event.getPullRequest();
            final Repository repo = pr.getToRef().getRepository();
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString()
                    + " ignored, CI not enabled for target repo "
                    + repo.toString());
                return;
            }
            // just trigger a build of the new commit since the other hook doesn't catch merged PRs.
            String mergeSha1 = event.getCommit().getId();
            String targetBranch = pr.getToRef().getId();
            boolean publishEnabled = cpm.getJobTypeStatusMapping(rc, JobType.PUBLISH);
            boolean verifyEnabled = cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_COMMIT);
            if (publishEnabled && targetBranch.matches(rc.getPublishBranchRegex())) {
                log.info("Stashbot Trigger: Triggering PUBLISH build for commit "
                    + mergeSha1 + " after merge of branch " + targetBranch);
                jenkinsManager.triggerBuild(repo, JobType.PUBLISH, mergeSha1, targetBranch);
            } else if (verifyEnabled && targetBranch.matches(rc.getVerifyBranchRegex())) {
                // TODO: Build any commits which are new, for now just build latest commit
                // Do this by doing a revwalk just like in TriggerJenkinsBuildHook, excluding the build we just published.
                log.info("Stashbot Trigger: Triggering VERIFICATION build for commit "
                    + mergeSha1 + " after merge of branch " + targetBranch);
                jenkinsManager.triggerBuild(repo, JobType.VERIFY_COMMIT, mergeSha1, targetBranch);
            }
            return;
        } catch (SQLException e) {
            log.error("Error getting repository configuration", e);
        }
    }

    @EventListener
    public void listenForRescope(PullRequestRescopedEvent event) {
        updatePr(event.getPullRequest());
    }

    public void updatePr(PullRequest pr) {
        try {
            final Repository repo = pr.getToRef().getRepository();
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString()
                    + " ignored, CI not enabled for target repo "
                    + repo.toString());
                return;
            }
            if (!cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_PR)) {
                log.debug("Pull Request " + pr.toString()
                    + " ignored, PR builds not enabled for target repo "
                    + repo.toString());
                return;
            }

            // Ensure target branch is a verified branch
            if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
                log.debug("Pull Request " + pr.toString() + " ignored, branch "
                    + pr.getToRef().getId() + " doesn't match verify regex");
                return;
            }

            PullRequestMetadata prm = cpm.getPullRequestMetadata(pr);
            if (rc.getRebuildOnTargetUpdate()) {
                // If we have built this combination of PR, mergeHash then we're
                // done
                if (prm.getBuildStarted()) {
                    log.debug("Verification build already triggered for PR "
                        + pr.toString() + ", fromSha " + prm.getFromSha()
                        + " toSha " + prm.getToSha());
                    return;
                }
            } else {
                // If we are only triggering when from ref updates, not too, then we need to search for PRM based upon that data instead.  this method does that.
                // We have to look through all "similar" PRMs to see if any are only different by toSha.
                Collection<PullRequestMetadata> prms = cpm.getPullRequestMetadataWithoutToRef(pr);
                for (PullRequestMetadata cur : prms) {
                    if (!cur.getBuildStarted()) {
                        // build not started, so don't consider this PRM
                        continue;
                    }
                    if (cur.getFromSha().equals(pr.getFromRef().getLatestCommit())) {
                        // we found a PRM for which buildstarted = true and fromSha matches, so return
                        return;
                    }
                }
                // At this point, there is no PRM where buildstarted = true and fromSha matches the current sha1
            }

            // At this point, we know a build hasn't been triggered yet, so
            // trigger it
            log.info("Stashbot Trigger: Triggering VERIFY_PR build for PR " + pr.toString()
                + ", fromSha " + prm.getFromSha() + " toSha "
                + prm.getToSha());

            jenkinsManager.triggerBuild(repo, JobType.VERIFY_PR, pr);

            // note that we have successfully started the build
            // Since we don't hit this code in the case of exception, you can
            // "retry" a build simply by causing a PR
            // event like by adding a comment.
            cpm.setPullRequestMetadata(pr, true, null, null);
        } catch (SQLException e) {
            log.error("Error getting repository configuration", e);
        }
    }
}
