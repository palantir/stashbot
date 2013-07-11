package com.palantir.stash.stashbothelper.managers;

import java.net.URI;
import java.net.URISyntaxException;

import com.offbytwo.jenkins.JenkinsServer;
import com.palantir.stash.stashbothelper.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbothelper.config.RepositoryConfiguration;

/**
 * This class exists to encapsulate the jenkins client library and make mocking/testing easier.
 * 
 * TODO: cache jenkins server object?
 * 
 * @author cmyers
 * 
 */
public class JenkinsClientManager {

    public JenkinsServer getJenkinsServer(JenkinsServerConfiguration jsc, RepositoryConfiguration rc)
        throws URISyntaxException {
        return new JenkinsServer(new URI(jsc.getUrl()), jsc.getUsername(), jsc.getPassword());
    }
}
