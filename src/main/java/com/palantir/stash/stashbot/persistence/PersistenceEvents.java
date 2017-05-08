package com.palantir.stash.stashbot.persistence;

import org.slf4j.Logger;

import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

public class PersistenceEvents {

    private final ConfigurationPersistenceService cps;
    private final Logger log;

    public PersistenceEvents(ConfigurationPersistenceService cps, PluginLoggerFactory plf) {
        this.cps = cps;
        this.log = plf.getLoggerForThis(this);
    }

    /**
     * This gets run when the plugin is enabled to ensure that a default SSH key exists
     * 
     * The key has to be created outside of a read-only transaction.
     * 
     * @param pee
     */
    @EventListener
    public void onPluginEnabled(PluginEnabledEvent pee) {

        if (pee.getPlugin().getKey().equals("com.palantir.stash.stashbot")) {
            log.debug("Ensuring default ssh key exists");
            cps.getDefaultPublicSshKey();
        }

    }
}
