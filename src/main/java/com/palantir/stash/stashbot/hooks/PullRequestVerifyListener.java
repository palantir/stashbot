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

import org.apache.log4j.Logger;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

/**
 * This class listens for new pull requests and triggers a verify build if the target repository has CI enabled and the
 * target ref matches the verify regex.
 * 
 * @author cmyers
 * 
 */
public class PullRequestVerifyListener {

    private static final Logger log = Logger.getLogger(PullRequestVerifyListener.class.toString());

    private final ConfigurationPersistenceManager cpm;
    private final JenkinsManager jenkinsManager;

    public PullRequestVerifyListener(ConfigurationPersistenceManager cpm, JenkinsManager jenkinsManager) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
    }

    @EventListener
    public void listen(PullRequestOpenedEvent event) {
        try {
            final PullRequest pr = event.getPullRequest();
            // Presently, stash only supports inter-repo pull requests, so there is only one repo involved. If they ever
            // add
            // cross-repo pull requests, it is uncertain which this would return.
            final Repository repo = pr.getFromRef().getRepository();

            final RepositoryConfiguration rc = cpm.getRepositoryConfigurationForRepository(repo);
            if (!rc.getCiEnabled()) {
                log.debug("Pull Request " + pr.toString() + " ignored, CI not enabled for repo " + repo.toString());
                return;
            }

            if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
                log.debug("Pull Request " + pr.toString() + " ignored, branch " + pr.getToRef().getId()
                    + " doesn't match verify regex");
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
