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
package com.palantir.stash.stashbot.webpanel;

import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class IsCiEnabledForRepoCondition implements Condition {

    private final ConfigurationPersistenceService cpm;
    private final Logger log;

    public IsCiEnabledForRepoCondition(ConfigurationPersistenceService cpm, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {

        // request, principal, changeset, repository
        Repository repo = (Repository) context.get("repository");
        RepositoryConfiguration rc;
        if (repo == null) {
            return false;
        }
        try {
            rc = cpm.getRepositoryConfigurationForRepository(repo);
        } catch (SQLException e) {
            rc = null;
            log.error("Failed to get RepositoryConfiguration for repo: " + repo.toString(), e);
        }

        if (rc != null && rc.getCiEnabled()) {
            return true;
        }
        return false;
    }
}
