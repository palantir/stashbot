package com.palantir.stash.stashbot.webpanel;

import java.sql.SQLException;
import java.util.Map;

import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;

public class IsCiEnabledForRepoCondition implements Condition {

    private final ConfigurationPersistenceManager cpm;

    public IsCiEnabledForRepoCondition(ConfigurationPersistenceManager cpm) {
        this.cpm = cpm;
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
            // LOG?
        }

        if (rc != null && rc.getCiEnabled()) {
            return true;
        }
        return false;
    }
}
