package com.palantir.stash.stashbot.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import net.java.ao.DBParam;
import net.java.ao.EntityManager;
import net.java.ao.test.jdbc.Data;
import net.java.ao.test.jdbc.DatabaseUpdater;
import net.java.ao.test.jdbc.DynamicJdbcConfiguration;
import net.java.ao.test.jdbc.Jdbc;
import net.java.ao.test.junit.ActiveObjectsJUnitRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.ConfigurationTest.DataStuff;

@RunWith(ActiveObjectsJUnitRunner.class)
@Jdbc(DynamicJdbcConfiguration.class)
@Data(DataStuff.class)
public class ConfigurationTest {

    private EntityManager entityManager;
    private ActiveObjects ao;
    private ConfigurationPersistenceManager cpm;

    @Before
    public void setUp() throws Exception {
        // ensure our runner sets this for us
        Assert.assertNotNull(entityManager);

        ao = new TestActiveObjects(entityManager);

        cpm = new ConfigurationPersistenceManager(ao);
    }

    @Test
    public void storesRepoInfoTest() throws Exception {
        String url = "http://www.example.com:1234/jenkins";
        String username = "jenkins_test_user";
        String password = "jenkins_test_user_password";
        String stashUsername = "stash_test_user";
        String stashPassword = "stash_test_user_password";

        int sizeOfData = ao.count(JenkinsServerConfiguration.class);

        cpm.setDefaultJenkinsServerConfiguration(url, username, password, stashUsername, stashPassword);
        JenkinsServerConfiguration jsc = cpm.getDefaultJenkinsServerConfiguration();
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals(url, jsc.getUrl());
        Assert.assertEquals(username, jsc.getUsername());
        Assert.assertEquals(password, jsc.getPassword());

        Assert.assertEquals(sizeOfData + 1, ao.count(JenkinsServerConfiguration.class));
    }

    @Test
    public void getsDefaultjenkinsServerConfiguration() throws Exception {

        JenkinsServerConfiguration jsc = cpm.getDefaultJenkinsServerConfiguration();
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals("empty", jsc.getUrl());
        Assert.assertEquals("empty", jsc.getUsername());
        Assert.assertEquals("empty", jsc.getPassword());
    }

    @Test
    public void getsAllJenkinsServerConfigurationsEmpty() throws Exception {

        Collection<JenkinsServerConfiguration> jscs = cpm.getAllJenkinsServerConfigurations();
        Assert.assertEquals(jscs.size(), 1);
        JenkinsServerConfiguration jsc = jscs.iterator().next();
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals("empty", jsc.getUrl());
        Assert.assertEquals("empty", jsc.getUsername());
        Assert.assertEquals("empty", jsc.getPassword());
    }

    @Test
    public void getsAllJenkinsServerConfigurationsNotEmpty() throws Exception {

        cpm.setDefaultJenkinsServerConfiguration("url1", "yuser", "pw", "stashuser", "stashpw");
        cpm.setJenkinsServerConfiguration("foo", "url2", "yuser", "pw", "stashuser", "stashpw");

        Collection<JenkinsServerConfiguration> jscs = cpm.getAllJenkinsServerConfigurations();
        Assert.assertEquals(jscs.size(), 2);
        Map<String, JenkinsServerConfiguration> configs = new HashMap<String, JenkinsServerConfiguration>();

        for (JenkinsServerConfiguration jsc : jscs) {
            configs.put(jsc.getName(), jsc);
        }
        Assert.assertEquals("default", configs.get("default").getName());
        Assert.assertEquals("url1", configs.get("default").getUrl());
        Assert.assertEquals("foo", configs.get("foo").getName());
        Assert.assertEquals("url2", configs.get("foo").getUrl());
    }

    @Test
    public void storesRepoData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(1);
        Mockito.when(repo.getName()).thenReturn("repoName");

        int size = ao.count(RepositoryConfiguration.class);

        cpm.setRepositoryConfigurationForRepository(repo, true, "verifyBranchRegex", "verifyBuildCommand",
            "publishBranchRegex", "publishBuildCommand", "prebuildCommand", "default");

        RepositoryConfiguration rc = cpm.getRepositoryConfigurationForRepository(repo);

        Assert.assertEquals("publishBranchRegex", rc.getPublishBranchRegex());
        Assert.assertEquals("publishBuildCommand", rc.getPublishBuildCommand());
        Assert.assertEquals("verifyBranchRegex", rc.getVerifyBranchRegex());
        Assert.assertEquals("verifyBuildCommand", rc.getVerifyBuildCommand());
        Assert.assertEquals("prebuildCommand", rc.getPrebuildCommand());
        Assert.assertEquals("default", rc.getJenkinsServerName());
        Assert.assertTrue(rc.getCiEnabled());

        Assert.assertEquals(size + 1, ao.count(RepositoryConfiguration.class));
    }

    @Test
    public void failsWithBadData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(1);
        Mockito.when(repo.getName()).thenReturn("repoName");

        int size = ao.count(RepositoryConfiguration.class);

        try {
            cpm.setRepositoryConfigurationForRepository(repo, true, "verifyBranchRegex", "verifyBuildCommand",
                "publishBranchRegex", "publishBuildCommand", "prebuildCommand", "BADNAME");
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            // success
        }
    }

    @Test
    public void getsStoredRepoData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(10);
        RepositoryConfiguration rc = cpm.getRepositoryConfigurationForRepository(repo);

        Assert.assertEquals("publishBranchRegex", rc.getPublishBranchRegex());
        Assert.assertEquals("publishBuildCommand", rc.getPublishBuildCommand());
        Assert.assertEquals("verifyBranchRegex", rc.getVerifyBranchRegex());
        Assert.assertEquals("verifyBuildCommand", rc.getVerifyBuildCommand());
        Assert.assertTrue(rc.getCiEnabled());

    }

    public static class DataStuff implements DatabaseUpdater {

        @SuppressWarnings("unchecked")
        @Override
        public void update(EntityManager entityManager) throws Exception {
            entityManager.migrate(JenkinsServerConfiguration.class, RepositoryConfiguration.class);

            RepositoryConfiguration rc = entityManager.create(RepositoryConfiguration.class,
                new DBParam("REPO_ID", new Integer(10)));

            rc.setCiEnabled(true);
            rc.setPublishBranchRegex("publishBranchRegex");
            rc.setPublishBuildCommand("publishBuildCommand");
            rc.setVerifyBranchRegex("verifyBranchRegex");
            rc.setVerifyBuildCommand("verifyBuildCommand");
            rc.save();
        }

    }
}
