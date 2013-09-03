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
import java.util.Collection;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.atlassian.stash.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsManager;

// TODO: listen for push event instead of implementing hook so we don't have to activate it
// SEE: https://developer.atlassian.com/stash/docs/latest/reference/plugin-module-types/post-receive-hook-plugin-module.html
public class TriggerJenkinsBuildHook implements AsyncPostReceiveRepositoryHook {

    private final ConfigurationPersistenceManager cpm;
    private final JenkinsManager jenkinsManager;
    private final Logger log;

    public TriggerJenkinsBuildHook(ConfigurationPersistenceManager cpm, JenkinsManager jenkinsManager,
        StashbotLoggerFactory lf) {
        this.cpm = cpm;
        this.jenkinsManager = jenkinsManager;
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

        for (RefChange refChange : changes) {
            String refName = refChange.getRefId();

            // deletes have a tohash of "0000000000000000000000000000000000000000"
            // but it seems more reliable to use RefChangeType
            if (refChange.getType().equals(RefChangeType.DELETE)) {
                log.debug("Detected delete, not triggering a build for this change");
                continue;
            }

            // if matches publication regex...
            if (refName.matches(rc.getPublishBranchRegex())) {
                log.info("Triggering PUBLISH build for " + repo.toString());
                // trigger a publication build
                jenkinsManager.triggerBuild(repo, JenkinsBuildTypes.PUBLISH, refChange.getToHash());
                continue;
            }
            if (refName.matches(rc.getVerifyBranchRegex())) {
                log.info("Triggering VERIFICATION build for " + repo.toString());
                // trigger a verification build (no merge)
                jenkinsManager.triggerBuild(repo, JenkinsBuildTypes.VERIFICATION, refChange.getToHash());
                continue;
            }
        }
    }
}
