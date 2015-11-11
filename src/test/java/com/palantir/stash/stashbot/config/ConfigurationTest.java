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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.EventPublisher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService.BuildTimeoutSettings;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService.EmailSettings;
import com.palantir.stash.stashbot.config.ConfigurationTest.DataStuff;
import com.palantir.stash.stashbot.event.StashbotMetadataUpdatedEvent;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.AuthenticationCredential;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.persistence.JobTypeStatusMapping;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

@RunWith(ActiveObjectsJUnitRunner.class)
@Jdbc(DynamicJdbcConfiguration.class)
@Data(DataStuff.class)
public class ConfigurationTest {

    private static final Long PR_ID = 1234L;
    private static final Integer REPO_ID = 1235;
    private static final String FROM_SHA = "8e57a8b77501710fe1e30a3500102c0968763107";
    private static final String TO_SHA = "beefbeef7501710fe1e30a3500102c0968763107";

    private EntityManager entityManager;
    private ActiveObjects ao;
    private ConfigurationPersistenceService cpm;

    @Mock
    private PullRequestRef fromRef;
    @Mock
    private PullRequestRef toRef;
    @Mock
    private PullRequest pr;
    @Mock
    private Repository repo;
    @Mock
    private EventPublisher publisher;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Mockito.when(pr.getToRef()).thenReturn(toRef);
        Mockito.when(pr.getFromRef()).thenReturn(fromRef);
        Mockito.when(pr.getId()).thenReturn(PR_ID);
        Mockito.when(toRef.getRepository()).thenReturn(repo);
        Mockito.when(toRef.getLatestCommit()).thenReturn(TO_SHA);
        Mockito.when(fromRef.getLatestCommit()).thenReturn(FROM_SHA);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);

        // ensure our runner sets this for us
        Assert.assertNotNull(entityManager);

        ao = new TestActiveObjects(entityManager);

        cpm = new ConfigurationPersistenceImpl(ao, lf, publisher);
    }

    @Test
    public void storesRepoInfoTest() throws Exception {
        String url = "http://www.example.com:1234/jenkins";
        String username = "jenkins_test_user";
        String password = "jenkins_test_user_password";
        String stashUsername = "stash_test_user";
        String stashPassword = "stash_test_user_password";
        Integer maxVerifyChain = 10;

        int sizeOfData = ao.count(JenkinsServerConfiguration.class);

        cpm.setJenkinsServerConfiguration(null, url, username, password,
            null, stashUsername, stashPassword, maxVerifyChain, false, false, false, null);
        JenkinsServerConfiguration jsc = cpm
            .getJenkinsServerConfiguration(null);
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals(url, jsc.getUrl());
        Assert.assertEquals(username, jsc.getUsername());
        Assert.assertEquals(password, jsc.getPassword());

        Assert.assertEquals(sizeOfData + 1,
            ao.count(JenkinsServerConfiguration.class));
    }

    @Test
    public void getsDefaultjenkinsServerConfiguration() throws Exception {

        JenkinsServerConfiguration jsc = cpm
            .getJenkinsServerConfiguration(null);
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals("empty", jsc.getUrl());
        Assert.assertEquals("empty", jsc.getUsername());
        Assert.assertEquals("empty", jsc.getPassword());
    }

    @Test
    public void getsAllJenkinsServerConfigurationsEmpty() throws Exception {

        Collection<JenkinsServerConfiguration> jscs = cpm
            .getAllJenkinsServerConfigurations();
        Assert.assertEquals(jscs.size(), 1);
        JenkinsServerConfiguration jsc = jscs.iterator().next();
        Assert.assertEquals("default", jsc.getName());
        Assert.assertEquals("empty", jsc.getUrl());
        Assert.assertEquals("empty", jsc.getUsername());
        Assert.assertEquals("empty", jsc.getPassword());
    }

    @Test
    public void getsAllJenkinsServerConfigurationsNotEmpty() throws Exception {

        cpm.setJenkinsServerConfiguration(null, "url1", "yuser", "pw",
            null, "stashuser", "stashpw", 10, false, false, false, null);
        cpm.setJenkinsServerConfiguration("foo", "url2", "yuser", "pw",
            null, "stashuser", "stashpw", 10, false, false, false, null);

        Collection<JenkinsServerConfiguration> jscs = cpm
            .getAllJenkinsServerConfigurations();
        Assert.assertEquals(jscs.size(), 2);
        Map<String, JenkinsServerConfiguration> configs = new HashMap<String, JenkinsServerConfiguration>();

        for (JenkinsServerConfiguration jsc : jscs) {
            configs.put(jsc.getName(), jsc);
        }
        Assert.assertEquals("default", configs.get("default").getName());
        Assert.assertEquals("url1", configs.get("default").getUrl());
        Assert.assertEquals("foo", configs.get("foo").getName());
        Assert.assertEquals("url2", configs.get("foo").getUrl());
        Assert.assertEquals(new Integer(10), configs.get("foo")
            .getMaxVerifyChain());
    }

    @Test
    public void storesRepoData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(1);
        Mockito.when(repo.getName()).thenReturn("repoName");

        int size = ao.count(RepositoryConfiguration.class);

        cpm.setRepositoryConfigurationForRepository(repo, true,
            "verifyBranchRegex", "verifyBuildCommand",
            false, "N/A", "publishBranchRegex",
            "publishBuildCommand", false, "N/A", "prebuildCommand", "default", true, false, "N/A", false, "N/A",
            size, new EmailSettings(true, "a@a.a", true, true, true), false, false,
            false, false, new BuildTimeoutSettings(false, 0));

        RepositoryConfiguration rc = cpm
            .getRepositoryConfigurationForRepository(repo);

        Assert.assertEquals("publishBranchRegex", rc.getPublishBranchRegex());
        Assert.assertEquals("publishBuildCommand", rc.getPublishBuildCommand());
        Assert.assertEquals("verifyBranchRegex", rc.getVerifyBranchRegex());
        Assert.assertEquals("verifyBuildCommand", rc.getVerifyBuildCommand());
        Assert.assertEquals("prebuildCommand", rc.getPrebuildCommand());
        Assert.assertEquals("default", rc.getJenkinsServerName());
        Assert.assertTrue(rc.getCiEnabled());
        Assert.assertTrue(rc.getEmailNotificationsEnabled());
        Assert.assertFalse(rc.getPreserveJenkinsJobConfig());

        Assert.assertEquals(size + 1, ao.count(RepositoryConfiguration.class));
    }

    @Test
    public void failsWithBadData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(1);
        Mockito.when(repo.getName()).thenReturn("repoName");

        try {
            cpm.setRepositoryConfigurationForRepository(repo, true,
                "verifyBranchRegex", "verifyBuildCommand",
                false, "N/A",
                "publishBranchRegex", "publishBuildCommand", false, "N/A", "prebuildCommand", "BADNAME", true, false,
                "N/A", false, "N/A", null, new EmailSettings(), false, false,
                false, false, new BuildTimeoutSettings(false, 0));
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            // success
        }
    }

    @Test
    public void getsStoredRepoData() throws Exception {
        Repository repo = Mockito.mock(Repository.class);
        Mockito.when(repo.getId()).thenReturn(10);
        RepositoryConfiguration rc = cpm
            .getRepositoryConfigurationForRepository(repo);

        Assert.assertEquals("publishBranchRegex", rc.getPublishBranchRegex());
        Assert.assertEquals("publishBuildCommand", rc.getPublishBuildCommand());
        Assert.assertEquals("verifyBranchRegex", rc.getVerifyBranchRegex());
        Assert.assertEquals("verifyBuildCommand", rc.getVerifyBuildCommand());
        Assert.assertTrue(rc.getCiEnabled());
        Assert.assertFalse(rc.getPreserveJenkinsJobConfig());

    }

    public static class DataStuff implements DatabaseUpdater {

        @SuppressWarnings("unchecked")
        @Override
        public void update(EntityManager entityManager) throws Exception {
            entityManager.migrate(JenkinsServerConfiguration.class, RepositoryConfiguration.class,
                PullRequestMetadata.class, JobTypeStatusMapping.class, AuthenticationCredential.class);

            RepositoryConfiguration rc = entityManager.create(
                RepositoryConfiguration.class, new DBParam("REPO_ID",
                    new Integer(10)));

            rc.setCiEnabled(true);
            rc.setPublishBranchRegex("publishBranchRegex");
            rc.setPublishBuildCommand("publishBuildCommand");
            rc.setVerifyBranchRegex("verifyBranchRegex");
            rc.setVerifyBuildCommand("verifyBuildCommand");
            rc.save();
        }

    }

    @Test
    public void testPullRequestMetadata() throws Exception {
        Assert.assertEquals(0, ao.count(PullRequestMetadata.class));
        PullRequestMetadata prm = cpm.getPullRequestMetadata(pr);

        Assert.assertEquals(1, ao.count(PullRequestMetadata.class));
        Assert.assertEquals(PR_ID, prm.getPullRequestId());
        Assert.assertEquals(FROM_SHA, prm.getFromSha());
        Assert.assertEquals(TO_SHA, prm.getToSha());
        pr.getToRef().getRepository().getId();
        cpm.setPullRequestMetadata(pr, true, false, null);
        PullRequestMetadata prm2 = cpm.getPullRequestMetadata(pr);
        Assert.assertEquals(1, ao.count(PullRequestMetadata.class));
        Assert.assertEquals(PR_ID, prm2.getPullRequestId());
        Assert.assertEquals(FROM_SHA, prm2.getFromSha());
        Assert.assertEquals(TO_SHA, prm2.getToSha());
        Assert.assertEquals(true, prm2.getBuildStarted().booleanValue());
        Assert.assertEquals(false, prm2.getSuccess().booleanValue());
        Assert.assertEquals(false, prm2.getOverride().booleanValue());
        Mockito.verify(publisher).publish(Mockito.any(StashbotMetadataUpdatedEvent.class));
    }

    @Test
    public void testFixesUrlEndingInSlash() throws Exception {
        String url = "http://url.that.ends.in";
        ao.create(
            JenkinsServerConfiguration.class,
            new DBParam("NAME", "sometest"),
            new DBParam("URL", url + "/"),
            new DBParam("USERNAME", "someuser"),
            new DBParam("PASSWORD", "somepw"),
            new DBParam("STASH_USERNAME", "someuser"),
            new DBParam("STASH_PASSWORD", "somepw"),
            new DBParam("MAX_VERIFY_CHAIN", 1));
        JenkinsServerConfiguration jsc = cpm
            .getJenkinsServerConfiguration("sometest");
        Assert.assertEquals(url, jsc.getUrl());
    }

    @Test
    public void testJenkinsServerConfigurationAuthModes() throws Exception {
        ImmutableList<ImmutableMap<String, String>> test = AuthenticationMode.getSelectList(null);
        Assert.assertTrue(test.contains(AuthenticationMode.USERNAME_AND_PASSWORD.getSelectListEntry(false)));
        Assert.assertTrue(test.contains(AuthenticationMode.CREDENTIAL_MANUALLY_CONFIGURED.getSelectListEntry(false)));

        test = AuthenticationMode.getSelectList(AuthenticationMode.USERNAME_AND_PASSWORD);
        Assert.assertTrue(test.contains(AuthenticationMode.USERNAME_AND_PASSWORD.getSelectListEntry(true)));
        Assert.assertTrue(test.contains(AuthenticationMode.CREDENTIAL_MANUALLY_CONFIGURED.getSelectListEntry(false)));

        ImmutableMap<String, String> entry = AuthenticationMode.USERNAME_AND_PASSWORD.getSelectListEntry(false);

        Assert.assertTrue(entry.containsKey("text"));
        Assert.assertTrue(entry.containsKey("value"));
        Assert.assertFalse(entry.containsKey("selected"));

        entry = AuthenticationMode.USERNAME_AND_PASSWORD.getSelectListEntry(true);

        Assert.assertTrue(entry.containsKey("text"));
        Assert.assertTrue(entry.containsKey("value"));
        Assert.assertTrue(entry.containsKey("selected"));
    }

    @Test
    public void testAuthenticationCredentials() {
        // get the default key the first time
        String privKey = cpm.getDefaultPrivateSshKey();
        String pubKey = cpm.getDefaultPublicSshKey();

        Assert.assertTrue(pubKey.startsWith("ssh-rsa AAAA"));
        Assert.assertTrue(privKey.startsWith("-----BEGIN RSA PRIVATE KEY-----"));

        // basic sanity check - pub key should be over 300, priv key should be over 1500
        Assert.assertTrue(pubKey.length() > 300);
        Assert.assertTrue(privKey.length() > 1500);

        // ensure future calls return the same value
        Assert.assertEquals(privKey, cpm.getDefaultPrivateSshKey());
        Assert.assertEquals(pubKey, cpm.getDefaultPublicSshKey());
    }

    @Test
    @Ignore
    public void testJenkinsConfigurationLocked() throws Exception {
    }
}
