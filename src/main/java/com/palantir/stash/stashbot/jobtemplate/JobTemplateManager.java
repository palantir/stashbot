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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.java.ao.DBParam;
import net.java.ao.Query;

import org.slf4j.Logger;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JobMapping;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class JobTemplateManager {

    private static final String DEFAULT_VERIFY_JOB_NAME = "verification";
    private static final String DEFAULT_VERIFY_JOB_FILE = "jenkins-verify-job.vm";
    private static final String DEFAULT_PUBLISH_JOB_NAME = "publish";
    private static final String DEFAULT_PUBLISH_JOB_FILE = "jenkins-publish-job.vm";
    private static final String DEFAULT_VERIFY_PR_JOB_NAME = "verify-pr";
    private static final String DEFAULT_VERIFY_PR_JOB_FILE = "jenkins-verify-pull-request-job.vm";

    private final ActiveObjects ao;
    private final Logger log;

    public JobTemplateManager(ActiveObjects ao, PluginLoggerFactory lf) {
        this.ao = ao;
        this.log = lf.getLoggerForThis(this);
    }

    public JobTemplate getDefaultVerifyJob() {
        JobTemplate[] jobs = ao.find(JobTemplate.class,
            Query.select().where("NAME = ?", DEFAULT_VERIFY_JOB_NAME));

        if (jobs.length == 1) {
            return jobs[0];
        }

        // Create the default verify job
        JobTemplate jjt = ao.create(JobTemplate.class, new DBParam("NAME",
            DEFAULT_VERIFY_JOB_NAME), new DBParam("TEMPLATE_FILE",
            DEFAULT_VERIFY_JOB_FILE), new DBParam("JOB_TYPE",
            JobType.VERIFY_COMMIT));
        jjt.save();
        return jjt;
    }

    public JobTemplate getDefaultVerifyPullRequestJob() {
        JobTemplate[] jobs = ao.find(JobTemplate.class,
            Query.select().where("NAME = ?", DEFAULT_VERIFY_PR_JOB_NAME));

        if (jobs.length == 1) {
            return jobs[0];
        }

        // Create the default verify job
        JobTemplate jjt = ao.create(JobTemplate.class, new DBParam("NAME",
            DEFAULT_VERIFY_PR_JOB_NAME), new DBParam("TEMPLATE_FILE",
            DEFAULT_VERIFY_PR_JOB_FILE), new DBParam("JOB_TYPE",
            JobType.VERIFY_PR));
        jjt.save();
        return jjt;
    }

    public JobTemplate getDefaultPublishJob() {
        JobTemplate[] jobs = ao.find(JobTemplate.class,
            Query.select().where("NAME = ?", DEFAULT_PUBLISH_JOB_NAME));

        if (jobs.length == 1) {
            return jobs[0];
        }

        // Create the default verify job
        JobTemplate jjt = ao.create(JobTemplate.class, new DBParam("NAME",
            DEFAULT_PUBLISH_JOB_NAME), new DBParam("TEMPLATE_FILE",
            DEFAULT_PUBLISH_JOB_FILE), new DBParam("JOB_TYPE",
            JobType.PUBLISH));
        jjt.save();
        return jjt;
    }

    public JobTemplate getJobTemplate(String name) throws SQLException {
        JobTemplate[] jobs = ao.find(JobTemplate.class,
            Query.select().where("NAME = ?", name));
        if (jobs.length == 0) {
            // just use the defaults
            return ao.create(JobTemplate.class, new DBParam("NAME", name),
                new DBParam("TEMPLATE_FILE", "verify-template.xml"),
                new DBParam("JOB_TYPE", JobType.NOOP));
        }

        return jobs[0];
    }

    public JobTemplate getJobTemplate(JobType jobType,
        RepositoryConfiguration rc) throws SQLException {
        List<JobTemplate> jobs = getJenkinsJobsForRepository(rc);
        for (JobTemplate jt : jobs) {
            if (jt.getJobType().equals(jobType)) {
                return jt;
            }
        }
        return null;
    }

    public void setJobTemplate(String name, String templateFile,
        JobType jenkinsJobType) throws SQLException {
        JobTemplate[] jobs = ao.find(JobTemplate.class,
            Query.select().where("NAME = ?", name));

        if (jobs.length == 0) {
            log.info("Creating jenkins job template: " + name);
            ao.create(JobTemplate.class, new DBParam("NAME", name),
                new DBParam("TEMPLATE_FILE", templateFile), new DBParam(
                    "JOB_TYPE", jenkinsJobType));
            return;
        }
        // already exists, so update it
        jobs[0].setTemplateFile(templateFile);
        jobs[0].setJobType(jenkinsJobType);
        jobs[0].save();
    }

    public void setJenkinsJobMapping(RepositoryConfiguration rc,
        JobTemplate jjt, Boolean isVisible, Boolean isEnabled)
        throws SQLException {
        JobMapping[] mappings = ao
            .find(JobMapping.class,
                Query.select()
                    .where("REPOSITORY_CONFIGURATION_ID = ? and JOB_TEMPLATE_ID = ?",
                        rc.getID(), jjt.getID()));
        if (mappings.length == 0) {
            // just use the defaults
            JobMapping jjm = ao.create(JobMapping.class, new DBParam(
                "REPOSITORY_CONFIGURATION_ID", rc.getID()), new DBParam(
                "JOB_TEMPLATE_ID", jjt.getID()));
            if (isVisible != null) {
                jjm.setVisible(isVisible);
            }
            if (isEnabled != null) {
                jjm.setEnabled(isEnabled);
            }
            jjm.save();
        }
    }

    private void createDefaultMappingsIfNeeded(RepositoryConfiguration rc)
        throws SQLException {

        for (JobTemplate defaultJob : ImmutableList.of(getDefaultVerifyJob(),
            getDefaultVerifyPullRequestJob(), getDefaultPublishJob())) {
            if (ao.find(
                JobMapping.class,
                Query.select()
                    .where("REPOSITORY_CONFIGURATION_ID = ? and JOB_TEMPLATE_ID = ?",
                        rc.getID(), defaultJob.getID())).length == 0) {
                setJenkinsJobMapping(rc, defaultJob, true, false);
            }
        }
    }

    public ImmutableList<JobTemplate> getJenkinsJobsForRepository(
        RepositoryConfiguration rc) throws SQLException {
        // Ensure each default plan exists (in case it hasn't been created yet)
        createDefaultMappingsIfNeeded(rc);

        JobMapping[] mappings = ao.find(
            JobMapping.class,
            Query.select().where(
                "REPOSITORY_CONFIGURATION_ID = ? AND VISIBLE = ?",
                rc.getID(), true));

        List<JobTemplate> templates = new ArrayList<JobTemplate>();
        for (JobMapping jm : mappings) {
            templates.add(jm.getJobTemplate());
        }
        return ImmutableList.copyOf(templates);
    }

    /**
     * Turn type string "verification" and a repository config into an
     * applicable job template
     * 
     * @param rc
     * @param s
     * @return
     * @throws SQLException
     */
    public JobTemplate fromString(RepositoryConfiguration rc, String s)
        throws SQLException {
        for (JobTemplate jt : getJenkinsJobsForRepository(rc)) {
            if (jt.getJobType().toString().equals(s.toLowerCase())) {
                return jt;
            }
        }
        return null;
    }
}
