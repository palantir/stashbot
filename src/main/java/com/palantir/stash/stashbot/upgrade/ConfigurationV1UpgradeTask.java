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
package com.palantir.stash.stashbot.upgrade;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.external.ActiveObjectsUpgradeTask;
import com.atlassian.activeobjects.external.ModelVersion;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;

public class ConfigurationV1UpgradeTask implements ActiveObjectsUpgradeTask {

    public ConfigurationV1UpgradeTask() {
    }

    @Override
    public ModelVersion getModelVersion() {
        return ModelVersion.valueOf("1");
    }

    /* This is safe to do because the ao.migrate() API uses varargs with a templated type
     * and there's no way around that, but it's just classes and how the API works, so I
     * think it's safe.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void upgrade(ModelVersion currentVersion, ActiveObjects ao) {
        if (!currentVersion.isSame(ModelVersion.valueOf("0"))) {
            throw new IllegalStateException("ConfigurationV1UpgradeTask can only upgrade from version 0");
        }
        // Migrate to the temporary one so that we get default values
        ao.migrate(TemporaryPullRequestMetadata.class);
        // Migrate to the final one, without default values
        ao.migrate(PullRequestMetadata.class);

    }

    // This is only used to initialize the repo Id's to -1 during the upgrade.
    @Table("PRMetadata001")
    @Preload
    public interface TemporaryPullRequestMetadata extends Entity {

        @NotNull
        @Default("-1")
        // Note the default value!  We don't want a default value in the final table, so we need a temp table
            public
            Integer getRepoId();

        public void setRepoId(Integer id);

        @NotNull
        public Long getPullRequestId();

        public void setPullRequestId(Long id);

        @NotNull
        public String getFromSha();

        public void setFromSha(String fromSha);

        @NotNull
        public String getToSha();

        public void setToSha(String toSha);

        @NotNull
        @Default("false")
        public Boolean getBuildStarted();

        public void setBuildStarted(Boolean buildStarted);

        @NotNull
        @Default("false")
        public Boolean getSuccess();

        public void setSuccess(Boolean success);

        @NotNull
        @Default("false")
        public Boolean getOverride();

        public void setOverride(Boolean override);

    }

}
