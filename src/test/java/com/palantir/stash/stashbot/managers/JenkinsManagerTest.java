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
package com.palantir.stash.stashbot.managers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.hook.repository.RepositoryHookService;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.google.common.collect.ImmutableList;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;

public class JenkinsManagerTest {

    // NOTE: this is the key used in the atlassian-plugin.xml
    private static final String TRIGGER_JENKINS_BUILD_HOOK_KEY =
        "com.palantir.stash.stashbot:triggerJenkinsBuildHook";

    private static final String XML_STRING = "<some xml here/>";

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryHookService rhs;
    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private JenkinsJobXmlFormatter xmlFormatter;
    @Mock
    private JenkinsClientManager jenkinsClientManager;
    @Mock
    private JobTemplateManager jtm;

    private JenkinsManager jenkinsManager;

    @Mock
    private JenkinsServer jenkinsServer;
    @Mock
    private Repository repo;
    @Mock
    private Project proj;

    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;
    @Mock
    private JobTemplate jobTemplate;

    private StashbotLoggerFactory lf = new StashbotLoggerFactory();

    @Before
    public void setUp() throws URISyntaxException, SQLException {

        MockitoAnnotations.initMocks(this);

        Mockito.when(
            jenkinsClientManager.getJenkinsServer(Mockito.any(JenkinsServerConfiguration.class),
                Mockito.any(RepositoryConfiguration.class))).thenReturn(jenkinsServer);

        Mockito.when(xmlFormatter.generateJobXml(jobTemplate, repo)).thenReturn(XML_STRING);

        Mockito.when(jtm.getJenkinsJobsForRepository(rc)).thenReturn(ImmutableList.of(jobTemplate));
        Mockito.when(jtm.getDefaultPublishJob()).thenReturn(jobTemplate);
        Mockito.when(jtm.getDefaultVerifyJob()).thenReturn(jobTemplate);

        Mockito.when(cpm.getJenkinsServerConfiguration(Mockito.anyString())).thenReturn(jsc);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(jsc.getStashUsername()).thenReturn("stash_username");
        Mockito.when(jsc.getStashPassword()).thenReturn("stash_password");
        Mockito.when(jsc.getPassword()).thenReturn("jenkins_password");

        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("project_key");

        jenkinsManager =
            new JenkinsManager(repositoryService, rhs, cpm, jtm, xmlFormatter, jenkinsClientManager, lf);
    }

    @Test
    public void testCreateJob() throws Exception {

        JenkinsBuildTypes buildType = JenkinsBuildTypes.VERIFICATION;
        String repoName = buildType.getBuildNameFor(repo);

        jenkinsManager.createJob(repo, buildType);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(xmlFormatter).generateJobXml(jobTemplate, repo);
        Mockito.verify(jenkinsServer).createJob(Mockito.eq(repoName), xmlCaptor.capture());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());
    }

    @Test
    public void testUpdateJob() throws Exception {

        JenkinsBuildTypes buildType = JenkinsBuildTypes.VERIFICATION;
        String jobName = buildType.getBuildNameFor(repo);
        String repoName = buildType.getBuildNameFor(repo);

        Job existingJob = Mockito.mock(Job.class);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        jenkinsManager.updateJob(repo, buildType);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(xmlFormatter).generateJobXml(jobTemplate, repo);
        Mockito.verify(jenkinsServer).updateJob(Mockito.eq(repoName), xmlCaptor.capture());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());
    }

    @Test
    public void testTriggerBuildShort() throws IOException {
        String HASH = "38356e8abe0e96538dd1007278ecc02c3bf3d2cb";
        JenkinsBuildTypes buildType = JenkinsBuildTypes.VERIFICATION;
        String jobName = buildType.getBuildNameFor(repo);

        Job existingJob = Mockito.mock(Job.class);
        Mockito.when(existingJob.getName()).thenReturn(jobName);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        jenkinsManager.triggerBuild(repo, buildType, HASH);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, String>> forClass = (Class<Map<String, String>>) (Class) Map.class;
        ArgumentCaptor<Map<String, String>> paramCaptor = ArgumentCaptor.forClass(forClass);

        Mockito.verify(existingJob).build(paramCaptor.capture());

        Map<String, String> paramMap = paramCaptor.getValue();
        Assert.assertTrue(paramMap.containsKey("buildHead"));
        Assert.assertTrue(paramMap.containsKey("type"));
        Assert.assertTrue(paramMap.containsKey("repoId"));
        Assert.assertFalse(paramMap.containsKey("pullRequestId"));
        Assert.assertFalse(paramMap.containsKey("mergeHead"));
    }

    @Test
    public void testUpdateRepoCIEnabled() throws IOException {

        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        String verificationName = JenkinsBuildTypes.VERIFICATION.getBuildNameFor(repo);
        String publishName = JenkinsBuildTypes.PUBLISH.getBuildNameFor(repo);

        jenkinsManager.updateRepo(repo);

        Mockito.verify(rhs).enable(repo, TRIGGER_JENKINS_BUILD_HOOK_KEY);
        Mockito.verify(jenkinsServer).createJob(Mockito.eq(verificationName), Mockito.anyString());
        Mockito.verify(jenkinsServer).createJob(Mockito.eq(publishName), Mockito.anyString());
    }

    @Test
    public void testUpdateRepoCIDisabled() throws IOException {

        Mockito.when(rc.getCiEnabled()).thenReturn(false);

        jenkinsManager.updateRepo(repo);

        Mockito.verify(rhs, Mockito.never()).enable(Mockito.any(Repository.class), Mockito.anyString());
        Mockito.verify(jenkinsServer, Mockito.never()).createJob(Mockito.anyString(), Mockito.anyString());
    }

}
