package com.palantir.stash.stashbot.managers;

import java.io.IOException;
import java.net.URISyntaxException;
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
import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.nav.NavBuilder.Repo;
import com.atlassian.stash.nav.NavBuilder.RepoClone;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jenkins.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jenkins.JenkinsJobXmlFormatter.JenkinsBuildParam;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;
import com.palantir.stash.stashbot.managers.JenkinsClientManager;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class JenkinsManagerTest {

    // NOTE: this is the key used in the atlassian-plugin.xml
    private static final String TRIGGER_JENKINS_BUILD_HOOK_KEY =
        "com.palantir.stash.stashbot:triggerJenkinsBuildHook";

    private static final String XML_STRING = "<some xml here/>";
    private static final String ABSOLUTE_PATH = "http://www.example.com/stash";

    @Mock
    private NavBuilder navBuilder;
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

    private JenkinsManager jenkinsManager;

    @Mock
    private JenkinsServer jenkinsServer;
    @Mock
    private Repository repo;
    @Mock
    private Project proj;

    // nav builder intermediaries - god damn this is annoying to mock
    @Mock
    private Repo nbRepo;
    @Mock
    private RepoClone nbRepoClone;

    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws URISyntaxException, SQLException {

        MockitoAnnotations.initMocks(this);

        Mockito.when(
            jenkinsClientManager.getJenkinsServer(Mockito.any(JenkinsServerConfiguration.class),
                Mockito.any(RepositoryConfiguration.class))).thenReturn(jenkinsServer);

        Mockito.when(xmlFormatter.getJobXml(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(List.class))).thenReturn(
            XML_STRING);

        Mockito.when(navBuilder.repo(Mockito.any(Repository.class))).thenReturn(nbRepo);
        Mockito.when(navBuilder.buildAbsolute()).thenReturn(ABSOLUTE_PATH);
        Mockito.when(nbRepo.clone(Mockito.anyString())).thenReturn(nbRepoClone);
        Mockito.when(nbRepoClone.buildAbsoluteWithoutUsername()).thenReturn(ABSOLUTE_PATH);

        Mockito.when(cpm.getJenkinsServerConfiguration()).thenReturn(jsc);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(jsc.getStashUsername()).thenReturn("stash_username");
        Mockito.when(jsc.getStashPassword()).thenReturn("stash_password");

        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("project_key");

        jenkinsManager =
            new JenkinsManager(navBuilder, repositoryService, rhs, cpm, xmlFormatter, jenkinsClientManager);
    }

    @Test
    public void testCreateJob() throws IOException {

        JenkinsBuildTypes buildType = JenkinsBuildTypes.VERIFICATION;
        String urlWithCreds = ABSOLUTE_PATH.replace("://", "://stash_username:stash_password@");
        String repoName = buildType.getBuildNameFor(repo);

        jenkinsManager.createJob(repo, buildType);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<List<JenkinsBuildParam>> forClass = (Class<List<JenkinsBuildParam>>) (Class) List.class;
        ArgumentCaptor<List<JenkinsBuildParam>> paramCaptor = ArgumentCaptor.forClass(forClass);

        Mockito.verify(xmlFormatter).getJobXml(Mockito.eq(urlWithCreds), Mockito.anyString(), Mockito.anyString(),
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), paramCaptor.capture());

        Mockito.verify(jenkinsServer).createJob(Mockito.eq(repoName), xmlCaptor.capture());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());

        List<JenkinsBuildParam> params = paramCaptor.getValue();

        Map<String, JenkinsBuildParam> paramMap = new HashMap<String, JenkinsBuildParam>();
        for (JenkinsBuildParam jbp : params) {
            paramMap.put(jbp.getName(), jbp);
        }
        Assert.assertTrue(paramMap.containsKey("buildHead"));
        Assert.assertTrue(paramMap.containsKey("mergeHead"));
        Assert.assertTrue(paramMap.containsKey("type"));
        Assert.assertTrue(paramMap.containsKey("repoId"));
        Assert.assertTrue(paramMap.containsKey("pullRequestId"));
    }

    @Test
    public void testUpdateJob() throws IOException {

        JenkinsBuildTypes buildType = JenkinsBuildTypes.VERIFICATION;
        String urlWithCreds = ABSOLUTE_PATH.replace("://", "://stash_username:stash_password@");
        String jobName = buildType.getBuildNameFor(repo);

        Job existingJob = Mockito.mock(Job.class);
        Map<String, Job> jobMap = new HashMap<String, Job>();
        jobMap.put(jobName, existingJob);
        Mockito.when(jenkinsServer.getJobs()).thenReturn(jobMap);

        jenkinsManager.updateJob(repo, buildType);

        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<List<JenkinsBuildParam>> forClass = (Class<List<JenkinsBuildParam>>) (Class) List.class;
        ArgumentCaptor<List<JenkinsBuildParam>> paramCaptor = ArgumentCaptor.forClass(forClass);

        Mockito.verify(xmlFormatter).getJobXml(Mockito.eq(urlWithCreds), Mockito.anyString(), Mockito.anyString(),
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), paramCaptor.capture());

        Mockito.verify(jenkinsServer).updateJob(Mockito.eq(jobName), xmlCaptor.capture());

        Assert.assertEquals(XML_STRING, xmlCaptor.getValue());

        List<JenkinsBuildParam> params = paramCaptor.getValue();

        Map<String, JenkinsBuildParam> paramMap = new HashMap<String, JenkinsBuildParam>();
        for (JenkinsBuildParam jbp : params) {
            paramMap.put(jbp.getName(), jbp);
        }
        Assert.assertTrue(paramMap.containsKey("buildHead"));
        Assert.assertTrue(paramMap.containsKey("mergeHead"));
        Assert.assertTrue(paramMap.containsKey("type"));
        Assert.assertTrue(paramMap.containsKey("repoId"));
        Assert.assertTrue(paramMap.containsKey("pullRequestId"));
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
