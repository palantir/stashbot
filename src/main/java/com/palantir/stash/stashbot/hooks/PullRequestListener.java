//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;

import org.slf4j.Logger;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.comment.Comment;
import com.atlassian.stash.event.pull.PullRequestCommentEvent;
import com.atlassian.stash.event.pull.PullRequestEvent;
import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

/**
 * This class listens for new pull requests and triggers a verify build if the target repository has CI enabled and the
 * target ref matches the verify regex.
 * 
 * @author cmyers
 * 
 */
public class PullRequestListener {

    private static final String OVERRIDE_STRING = "==OVERRIDE==";

    private final ConfigurationPersistenceManager cpm;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public PullRequestListener(ConfigurationPersistenceManager cpm, JenkinsManager jenkinsManager,
        StashbotLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
        this.log = lf.getLoggerForThis(this);
    }

    @EventListener
    public void listen(PullRequestEvent event) {
        try {
            // First, update pull request metadata
            final PullRequest pr = event.getPullRequest();
            final PullRequestMetadata prm = cpm.getPullRequestMetadata(pr);

            // More correct to use the "to ref" to find the repo - this is the destination repo.
            final Repository repo = pr.getToRef().getRepository();
            final RepositoryConfiguration rc = cpm.getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString() + " ignored, CI not enabled for target repo "
                    + repo.toString());
                return;
            }

            // Ensure target branch is a verified branch
            if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
                log.debug("Pull Request " + pr.toString() + " ignored, branch " + pr.getToRef().getId()
                    + " doesn't match verify regex");
                return;
            }

            // If the event is creating a pull request, we need to trigger a build
            boolean needsBuild = false;
            if (event instanceof PullRequestOpenedEvent) {
                log.debug("New Pull Request Opened");
                needsBuild = true;
            }

            if (event instanceof PullRequestCommentEvent) {
                Comment c = ((PullRequestCommentEvent) event).getComment();
                if (c.getText().contains(OVERRIDE_STRING)) {
                    log.debug("Pull Request override set to true");
                    cpm.setPullRequestMetadata(pr, null, true);
                }
            }

            // If either hash is updated, needs build
            if (!pr.getFromRef().getLatestChangeset().equals(prm.getFromSha())) {
                log.debug("Pull Request From SHA updated");
                needsBuild = true;
                cpm.setPullRequestMetadata(pr, false, false);
            }
            if (!pr.getToRef().getLatestChangeset().equals(prm.getToSha())) {
                log.debug("Pull Request To SHA updated");
                needsBuild = true;
                cpm.setPullRequestMetadata(pr, false, false);
            }

            if (!needsBuild) {
                return;
            }

            // trigger build
            String fromSha = pr.getFromRef().getLatestChangeset();
            String toSha = pr.getToRef().getLatestChangeset();

            log.debug("Triggering verification build for PR " + pr.toString() + ", building sha " + fromSha
                + " merged with target sha " + toSha);

            jenkinsManager.triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, fromSha, toSha, pr.getId().toString());
        } catch (SQLException e) {
            log.error("Error getting repository configuration", e);
        }
    }
}
