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
import org.mockito.MockitoAnnotations;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobTest.DataStuff;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;

@RunWith(ActiveObjectsJUnitRunner.class)
@Jdbc(DynamicJdbcConfiguration.class)
@Data(DataStuff.class)
public class JenkinsJobTest {

    private EntityManager entityManager;
    private ActiveObjects ao;
    private JobTemplateManager jjtm;

    private StashbotLoggerFactory lf = new StashbotLoggerFactory();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // ensure our runner sets this for us
        Assert.assertNotNull(entityManager);

        ao = new TestActiveObjects(entityManager);

        jjtm = new JobTemplateManager(ao, lf);
    }

    @Test
    public void testCreatesDefaultObjects() throws Exception {
        int sizeOfData = ao.count(JobTemplate.class);
        JobTemplate jjtV = jjtm.getDefaultVerifyJob();
        JobTemplate jjtP = jjtm.getDefaultPublishJob();

        Assert.assertEquals(jjtV.getName(), "defaultVerifyJob");
        Assert.assertEquals(jjtP.getName(), "defaultPublishJob");

        int newSizeOfData = ao.count(JobTemplate.class);
        Assert.assertTrue(newSizeOfData == sizeOfData + 2);
    }

    @Test
    public void testNewJobWorkflow() throws Exception {
        int sizeOfData = ao.count(JobTemplate.class);
        final String NEW_JOB = "newJobName";
        final String TEMPLATE_FILE = "verify-template.xml";

        JobTemplate newjob = jjtm.getJobTemplate(NEW_JOB);

        Assert.assertEquals(newjob.getName(), NEW_JOB);
        Assert.assertEquals(newjob.getTemplateFile(), TEMPLATE_FILE);
        Assert.assertEquals(newjob.getJobType(), JenkinsJobType.NOOP_BUILD);

        int newSizeOfData = ao.count(JobTemplate.class);
        Assert.assertTrue(newSizeOfData == sizeOfData + 1);
    }

    public static class DataStuff implements DatabaseUpdater {

        @SuppressWarnings("unchecked")
        @Override
        public void update(EntityManager entityManager) throws Exception {
            entityManager.migrate(JobTemplate.class, JobMapping.class);

        }

    }
}
