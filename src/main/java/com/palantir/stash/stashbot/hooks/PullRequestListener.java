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

import org.slf4j.Logger;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.comment.Comment;
import com.atlassian.stash.event.pull.PullRequestCommentEvent;
import com.atlassian.stash.event.pull.PullRequestEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;

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

    private final ConfigurationPersistenceManager cpm;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public PullRequestListener(ConfigurationPersistenceManager cpm,
        JenkinsManager jenkinsManager, StashbotLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
        this.log = lf.getLoggerForThis(this);
    }

    @EventListener
    public void listen(PullRequestEvent event) {
        try {
            // First, update pull request metadata
            final PullRequest pr = event.getPullRequest();

            // More correct to use the "to ref" to find the repo - this is the
            // destination repo.
            final Repository repo = pr.getToRef().getRepository();
            final RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString()
                    + " ignored, CI not enabled for target repo "
                    + repo.toString());
                return;
            }

            // Update override metadata if applicable
            if (event instanceof PullRequestCommentEvent) {
                Comment c = ((PullRequestCommentEvent) event).getComment();
                if (c.getText().contains(OVERRIDE_STRING)) {
                    log.debug("Pull Request override set to true for PR "
                        + pr.toString());
                    cpm.setPullRequestMetadata(pr, null, null, true);
                }
            }

            // Ensure target branch is a verified branch
            if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
                log.debug("Pull Request " + pr.toString() + " ignored, branch "
                    + pr.getToRef().getId() + " doesn't match verify regex");
                return;
            }

            // Before we can get the merged sha1, we have to force it to be
            // generated if it hasn't been generated already. these refs are
            // generated lazily.
            // prs.canMerge(pr.getToRef().getRepository().getId(), pr.getId());

            // If we have built this combination of PR, mergeHash then we're
            // done
            PullRequestMetadata prm = cpm.getPullRequestMetadata(pr);
            if (prm.getBuildStarted()) {
                log.debug("Verification build already triggered for PR "
                    + pr.toString() + ", fromSha " + prm.getFromSha()
                    + " toSha " + prm.getToSha());
                return;
            }

            // At this point, we know a build hasn't been triggered yet, so
            // trigger it
            log.debug("Triggering verification build for PR " + pr.toString()
                + ", fromSha " + prm.getFromSha() + " toSha "
                + prm.getToSha());

            // jenkinsManager.triggerBuild(repo, JobType.VERIFY_PR, fromSha,
            // toSha, pr.getId().toString());
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
