package com.palantir.stash.stashbot.webpanel;

import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;

import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

public class IsCiEnabledForRepoCondition implements Condition {

    private final ConfigurationPersistenceManager cpm;
    private final Logger log;

    public IsCiEnabledForRepoCondition(ConfigurationPersistenceManager cpm, PluginLoggerFactory lf) {
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
