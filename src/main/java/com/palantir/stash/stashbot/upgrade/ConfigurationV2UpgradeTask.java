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
package com.palantir.stash.stashbot.upgrade;

import net.java.ao.DBParam;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.external.ActiveObjectsUpgradeTask;
import com.atlassian.activeobjects.external.ModelVersion;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JobTypeStatusMapping;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

/**
 * This upgrade task ensures that when one upgrades from not having individual build toggles to having them, they start
 * out all enabled to mirror previous functionality.
 * 
 * @author cmyers
 *
 */
public class ConfigurationV2UpgradeTask implements ActiveObjectsUpgradeTask {

    @Override
    public ModelVersion getModelVersion() {
        return ModelVersion.valueOf("2");
    }

    /* This is safe to do because the ao.migrate() API uses varargs with a templated type
     * and there's no way around that, but it's just classes and how the API works, so I
     * think it's safe.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void upgrade(ModelVersion currentVersion, ActiveObjects ao) {
        if (!currentVersion.isSame(ModelVersion.valueOf("1"))) {
            throw new IllegalStateException("ConfigurationV2UpgradeTask can only upgrade from version 1");
        }
        // Migrate the old table to base the info off it.
        ao.migrate(RepositoryConfiguration.class);
        // Migrate the new table so we can populate it
        ao.migrate(JobTypeStatusMapping.class);

        for (RepositoryConfiguration rc : ao.find(RepositoryConfiguration.class)) {
            for (JobType jt : ImmutableList.of(JobType.PUBLISH, JobType.VERIFY_COMMIT, JobType.VERIFY_PR)) {
                ao.create(JobTypeStatusMapping.class,
                    new DBParam("REPO_CONFIG_ID", rc.getID()),
                    new DBParam("IS_ENABLED", true),
                    new DBParam("JOB_TYPE_RAW", jt.name()));
            }
        }
    }
}
