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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
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
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.mocks.MockJobTemplateFactory;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsManagerTest {

    // NOTE: this is the key used in the atlassian-plugin.xml
    private static final String TRIGGER_JENKINS_BUILD_HOOK_KEY = "com.palantir.stash.stashbot:triggerJenkinsBuildHook";

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
    @Mock
    private StashbotUrlBuilder sub;

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

    private final StashbotLoggerFactory lf = new StashbotLoggerFactory();

    private MockJobTemplateFactory jtf;

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        Mockito.when(
            jenkinsClientManager.getJenkinsServer(
                Mockito.any(JenkinsServerConfiguration.class),
                Mockito.any(RepositoryConfiguration.class)))
            .thenReturn(jenkinsServer);

        jtf = new MockJobTemplateFactory(jtm);
        jtf.generateDefaultsForRepo(repo, rc);

        Mockito.when(
            xmlFormatter.generateJobXml(Mockito.any(JobTemplate.class),
                Mockito.eq(repo))).thenReturn(XML_STRING);

        Mockito.when(cpm.getJenkinsServerConfiguration(Mockito.anyString()))
            .thenReturn(jsc);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo))
            .thenReturn(rc);
        Mockito.when(jsc.getStashUsername()).thenReturn("stash_username");
        Mockito.when(jsc.getStashPassword()).thenReturn("stash_password");
        Mockito.when(jsc.getPassword()).thenReturn("jenkins_password");

        Mockito.when(repo.getName()).thenReturn("somename");
        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("project_key");

        jenkinsManager = new JenkinsManager(repositoryService, rhs, cpm, jtm,
            xmlFormatter, jenkinsClientManager, sub, lf);
    }

    @Test
    public void testCreateJob() throws Exception {

        JobTemplate jt = jtm.getDefaultVerifyJob();

        jenkinsManager.createJob(repo, jt);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor
            .forClass(String.class);

        Mockito.verify(xmlFormatter).generateJobXml(jt, repo);
        Mockito.verify(jenkinsServer).createJob(Mockito.anyString(),
            xmlCaptor.capture());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());
    }

    @Test
    public void testUpdateJob() throws Exception {

        String jobName = "somename_verification";

        Job existingJob = Mockito.mock(Job.class);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        JobTemplate jt = jtm.getDefaultVerifyJob();

        jenkinsManager.updateJob(repo, jt);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor
            .forClass(String.class);

        Mockito.verify(xmlFormatter).generateJobXml(jt, repo);
        Mockito.verify(jenkinsServer).updateJob(Mockito.anyString(),
            xmlCaptor.capture());
        Mockito.verify(jenkinsServer, Mockito.never()).createJob(
            Mockito.anyString(), Mockito.anyString());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());
    }

    @Test
    public void testTriggerBuildShort() throws IOException, SQLException {
        String HASH = "38356e8abe0e96538dd1007278ecc02c3bf3d2cb";

        JobTemplate jt = jtm.getDefaultVerifyJob();

        String jobName = jt.getBuildNameFor(repo);
        Job existingJob = Mockito.mock(Job.class);
        Mockito.when(existingJob.getName()).thenReturn(jobName);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        Mockito.when(jtm.getJobTemplate(JobType.VERIFY_COMMIT, rc)).thenReturn(
            jt);

        jenkinsManager.triggerBuild(repo, JobType.VERIFY_COMMIT, HASH);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, String>> forClass = (Class) Map.class;
        ArgumentCaptor<Map<String, String>> paramCaptor = ArgumentCaptor
            .forClass(forClass);

        Mockito.verify(existingJob).build(paramCaptor.capture());

        Map<String, String> paramMap = paramCaptor.getValue();
        Assert.assertTrue(paramMap.containsKey("buildHead"));
        Assert.assertTrue(paramMap.containsKey("repoId"));
        Assert.assertFalse(paramMap.containsKey("pullRequestId"));
        Assert.assertFalse(paramMap.containsKey("mergeHead"));
    }

    @Test
    public void testUpdateRepoCIEnabled() throws IOException {

        Mockito.when(rc.getCiEnabled()).thenReturn(true);

        jenkinsManager.updateRepo(repo);

        Mockito.verify(rhs).enable(repo, TRIGGER_JENKINS_BUILD_HOOK_KEY);
        List<JobTemplate> templates = jtf.getMockTemplates();

        for (JobTemplate t : templates) {
            Mockito.verify(jenkinsServer).createJob(
                Mockito.eq(t.getBuildNameFor(repo)), Mockito.anyString());
        }
    }

    @Test
    public void testUpdateRepoCIDisabled() throws IOException {

        Mockito.when(rc.getCiEnabled()).thenReturn(false);

        jenkinsManager.updateRepo(repo);

        Mockito.verify(rhs, Mockito.never()).enable(
            Mockito.any(Repository.class), Mockito.anyString());
        Mockito.verify(jenkinsServer, Mockito.never()).createJob(
            Mockito.anyString(), Mockito.anyString());
    }

}
