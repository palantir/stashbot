package com.palantir.stash.stashbot.managers;

import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.util.Assert;

import com.offbytwo.jenkins.JenkinsServer;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.JenkinsClientManager;

public class JenkinsClientManagerTest {

    private static final String JENKINS_URL = "http://www.example.com:8080/jenkins";
    private static final String JENKINS_USERNAME = "jenkins_user";
    private static final String JENKINS_PW = "jenkins_pw";
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    private JenkinsClientManager jcm;

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        Mockito.when(jsc.getUrl()).thenReturn(JENKINS_URL);
        Mockito.when(jsc.getUsername()).thenReturn(JENKINS_USERNAME);
        Mockito.when(jsc.getPassword()).thenReturn(JENKINS_PW);
        jcm = new JenkinsClientManager();
    }

    @Test
    public void testJCM() throws URISyntaxException {
        JenkinsServer js = jcm.getJenkinsServer(jsc, rc);
        Assert.notNull(js);
    }
}
