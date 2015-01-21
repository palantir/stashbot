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
package com.palantir.stash.stashbot.managers;

import java.io.IOException;
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

import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.SecurityService;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserService;
import com.google.common.collect.Maps;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.mocks.MockJobTemplateFactory;
import com.palantir.stash.stashbot.mocks.MockSecurityServiceBuilder;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsManagerTest {

    private static final String XML_STRING = "<some xml here/>";

    @Mock
    private RepositoryService repositoryService;
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
    @Mock
    private UserService us;
    @Mock
    private UserManager um;

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
    private UserProfile up;
    @Mock
    private StashUser su;

    private SecurityService ss;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    private MockJobTemplateFactory jtf;
    private MockSecurityServiceBuilder mssb;

    @Before
    public void setUp() throws Throwable {

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

        Mockito.when(um.getRemoteUser()).thenReturn(up);
        Mockito.when(up.getUsername()).thenReturn("someuser");
        Mockito.when(us.getUserByName(Mockito.anyString())).thenReturn(su);

        mssb = new MockSecurityServiceBuilder();

        ss = mssb.getSecurityService();

        jenkinsManager = new JenkinsManager(repositoryService, cpm, jtm,
            xmlFormatter, jenkinsClientManager, sub, lf, ss, us, um);
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
    public void testTriggerBuildShort() throws Exception {
        String HASH = "38356e8abe0e96538dd1007278ecc02c3bf3d2cb";
        String REF = "refs/heads/master";

        JobTemplate jt = jtm.getDefaultVerifyJob();

        String jobName = jt.getBuildNameFor(repo);
        Job existingJob = Mockito.mock(Job.class);
        Mockito.when(existingJob.getName()).thenReturn(jobName);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        Mockito.when(jtm.getJobTemplate(JobType.VERIFY_COMMIT, rc)).thenReturn(
            jt);

        jenkinsManager.triggerBuild(repo, JobType.VERIFY_COMMIT, HASH, REF);
        jenkinsManager.destroy();

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, String>> forClass = (Class) Map.class;
        ArgumentCaptor<Map<String, String>> paramCaptor = ArgumentCaptor
            .forClass(forClass);

        Mockito.verify(existingJob).build(paramCaptor.capture());

        Map<String, String> paramMap = paramCaptor.getValue();
        Assert.assertTrue(paramMap.containsKey("buildHead"));
        Assert.assertTrue(paramMap.containsKey("buildRef"));
        Assert.assertTrue(paramMap.containsKey("repoId"));
        Assert.assertFalse(paramMap.containsKey("pullRequestId"));
        Assert.assertFalse(paramMap.containsKey("mergeHead"));
    }

    @Test
    public void testUpdateRepoCIEnabled() throws IOException {

        Mockito.when(rc.getCiEnabled()).thenReturn(true);

        jenkinsManager.updateRepo(repo);

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

        Mockito.verify(jenkinsServer, Mockito.never()).createJob(
            Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testPreserveJenkinsJobConfigDisabled() throws IOException {

        JobTemplate jt = jtm.getDefaultVerifyJob();
        HashMap<String, Job> jobs = Maps.newHashMap();
        jobs.put(jt.getBuildNameFor(repo), new Job()); // update job logic requires the job be there already

        Mockito.when(rc.getPreserveJenkinsJobConfig()).thenReturn(false);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobs);

        jenkinsManager.updateJob(repo, jt);

        Mockito.verify(jenkinsServer).updateJob(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testPreserveJenkinsJobConfigEnabled() throws IOException {

        JobTemplate jt = jtm.getDefaultVerifyJob();
        HashMap<String, Job> jobs = Maps.newHashMap();
        jobs.put(jt.getBuildNameFor(repo), new Job()); // update job logic requires the job be there already

        Mockito.when(rc.getPreserveJenkinsJobConfig()).thenReturn(true);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobs);

        jenkinsManager.updateJob(repo, jt);

        Mockito.verify(jenkinsServer, Mockito.never()).updateJob(Mockito.anyString(), Mockito.anyString());
    }
}
