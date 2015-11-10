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
package com.palantir.stash.stashbot.jobtemplate;

import junit.framework.Assert;
import net.java.ao.EntityManager;
import net.java.ao.test.jdbc.Data;
import net.java.ao.test.jdbc.DatabaseUpdater;
import net.java.ao.test.jdbc.DynamicJdbcConfiguration;
import net.java.ao.test.jdbc.Jdbc;
import net.java.ao.test.junit.ActiveObjectsJUnitRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.event.api.EventPublisher;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobTest.DataStuff;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JobMapping;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.JobTypeStatusMapping;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

@RunWith(ActiveObjectsJUnitRunner.class)
@Jdbc(DynamicJdbcConfiguration.class)
@Data(DataStuff.class)
public class JenkinsJobTest {

    private EntityManager entityManager;
    private ActiveObjects ao;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    private JobTemplateManager jtm;
    private ConfigurationPersistenceService cpm;

    private RepositoryConfiguration rc;
    @Mock
    private JobTemplate verifyCommitJT;
    @Mock
    private JobTemplate verifyPRJT;
    @Mock
    private JobTemplate publishJT;
    @Mock
    private RepositoryService rs;

    @Mock
    private Repository repo;
    @Mock
    private Project project;
    @Mock
    private EventPublisher publisher;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // ensure our runner sets this for us
        Assert.assertNotNull(entityManager);

        // Init mocks worked?
        Assert.assertNotNull(rs);
        Assert.assertNotNull(repo);

        Mockito.when(rs.getById(1234)).thenReturn(repo);
        Mockito.when(repo.getId()).thenReturn(1234);
        Mockito.when(repo.getProject()).thenReturn(project);
        Mockito.when(project.getName()).thenReturn("projectName");
        Mockito.when(rs.getById(Mockito.anyInt())).thenReturn(repo);

        ao = new TestActiveObjects(entityManager);

        jtm = new JobTemplateManager(ao, lf);
        cpm = new ConfigurationPersistenceImpl(ao, lf, publisher);

        verifyCommitJT = jtm.getDefaultVerifyJob();
        verifyPRJT = jtm.getDefaultVerifyPullRequestJob();
        publishJT = jtm.getDefaultPublishJob();

        rc = cpm.getRepositoryConfigurationForRepository(repo);

        jtm.setJenkinsJobMapping(rc, verifyCommitJT, true, true);
        jtm.setJenkinsJobMapping(rc, publishJT, true, true);
        jtm.setJenkinsJobMapping(rc, verifyPRJT, true, true);

    }

    @Test
    public void testCreatesDefaultObjects() throws Exception {
        JobTemplate jjtV = jtm.getDefaultVerifyJob();
        JobTemplate jjtP = jtm.getDefaultPublishJob();
        JobTemplate jjtPR = jtm.getDefaultVerifyPullRequestJob();

        Assert.assertEquals(jjtV.getName(), "verification");
        Assert.assertEquals(jjtPR.getName(), "verify-pr");
        Assert.assertEquals(jjtP.getName(), "publish");

        int newSizeOfData = ao.count(JobTemplate.class);
        Assert.assertTrue(newSizeOfData == 3);
    }

    @Test
    public void testNewJobWorkflow() throws Exception {
        int sizeOfData = ao.count(JobTemplate.class);
        final String NEW_JOB = "newJobName";
        final String TEMPLATE_FILE = "verify-template.xml";

        JobTemplate newjob = jtm.getJobTemplate(NEW_JOB);

        Assert.assertEquals(newjob.getName(), NEW_JOB);
        Assert.assertEquals(newjob.getTemplateFile(), TEMPLATE_FILE);
        Assert.assertEquals(newjob.getJobType(), JobType.NOOP);

        int newSizeOfData = ao.count(JobTemplate.class);
        Assert.assertTrue(newSizeOfData == sizeOfData + 1);
    }

    @Test
    public void testFromString() throws Exception {
        String v = JobType.VERIFY_COMMIT.toString();
        String p = JobType.PUBLISH.toString();
        String pr = JobType.VERIFY_PR.toString();

        JobTemplate vjt = jtm.fromString(rc, v);
        Assert.assertNotNull(vjt);
        Assert.assertEquals(JobType.VERIFY_COMMIT, vjt.getJobType());

        JobTemplate pjt = jtm.fromString(rc, p);
        Assert.assertNotNull(pjt);
        Assert.assertEquals(JobType.PUBLISH, pjt.getJobType());

        JobTemplate prjt = jtm.fromString(rc, pr);
        Assert.assertNotNull(prjt);
        Assert.assertEquals(JobType.VERIFY_PR, prjt.getJobType());
    }

    public static class DataStuff implements DatabaseUpdater {

        @SuppressWarnings("unchecked")
        @Override
        public void update(EntityManager entityManager) throws Exception {
            entityManager.migrate(JobTemplate.class, JobMapping.class,
                RepositoryConfiguration.class, JobTypeStatusMapping.class);
        }

    }
}
