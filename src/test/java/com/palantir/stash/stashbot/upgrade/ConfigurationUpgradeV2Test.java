// Copyright 2015 Palantir Technologies
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
package com.palantir.stash.stashbot.upgrade;

import net.java.ao.DBParam;
import net.java.ao.EntityManager;
import net.java.ao.test.jdbc.Data;
import net.java.ao.test.jdbc.DatabaseUpdater;
import net.java.ao.test.jdbc.DynamicJdbcConfiguration;
import net.java.ao.test.jdbc.Jdbc;
import net.java.ao.test.jdbc.NonTransactional;
import net.java.ao.test.junit.ActiveObjectsJUnitRunner;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.external.ModelVersion;
import com.atlassian.activeobjects.test.TestActiveObjects;
import com.palantir.stash.stashbot.config.ConfigurationTest.DataStuff;
import com.palantir.stash.stashbot.persistence.JobTypeStatusMapping;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

@RunWith(ActiveObjectsJUnitRunner.class)
@Jdbc(DynamicJdbcConfiguration.class)
@Data(DataStuff.class)
public class ConfigurationUpgradeV2Test {

    private EntityManager entityManager;
    private ActiveObjects ao;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        ao = new TestActiveObjects(entityManager);

    }

    @Test
    @NonTransactional
    public void upgradeTest() throws Exception {
        ConfigurationV2UpgradeTask u = new ConfigurationV2UpgradeTask();

        u.upgrade(ModelVersion.valueOf("1"), ao);

        Assert.assertEquals(3, ao.find(JobTypeStatusMapping.class).length);

    }

    public static class DataStuff implements DatabaseUpdater {

        @SuppressWarnings("unchecked")
        @Override
        public void update(EntityManager entityManager) throws Exception {
            // create some repo config
            entityManager.migrate(RepositoryConfiguration.class);
            RepositoryConfiguration rc = entityManager.create(
                RepositoryConfiguration.class, new DBParam("REPO_ID",
                    new Integer(10)));

            rc.setCiEnabled(true);
            rc.setPublishBranchRegex("publishBranchRegex");
            rc.setPublishBuildCommand("publishBuildCommand");
            rc.setVerifyBranchRegex("verifyBranchRegex");
            rc.setVerifyBuildCommand("verifyBuildCommand");
            rc.save();

            RepositoryConfiguration rc2 = entityManager.create(
                RepositoryConfiguration.class, new DBParam("REPO_ID",
                    new Integer(11)));

            rc2.setCiEnabled(false);
            rc2.setPublishBranchRegex("publishBranchRegex");
            rc2.setPublishBuildCommand("publishBuildCommand");
            rc2.setVerifyBranchRegex("verifyBranchRegex");
            rc2.setVerifyBuildCommand("verifyBuildCommand");
            rc2.save();
        }

    }
}
