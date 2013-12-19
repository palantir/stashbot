package com.palantir.stash.stashbot.jobtemplate;

import java.sql.SQLException;

import net.java.ao.DBParam;
import net.java.ao.Query;

import org.slf4j.Logger;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;

public class JobTemplateManager {

    private static final String DEFAULT_VERIFY_JOB_NAME = "defaultVerifyJob";
    private static final String DEFAULT_PUBLISH_JOB_NAME = "defaultPublishJob";

    private final ActiveObjects ao;
    private final Logger log;

    public JobTemplateManager(ActiveObjects ao, StashbotLoggerFactory lf) {
        this.ao = ao;
        this.log = lf.getLoggerForThis(this);
    }

    public JobTemplate getDefaultVerifyJob() {
        JobTemplate[] jobs =
            ao.find(JobTemplate.class,
                Query.select().where("NAME = ?", DEFAULT_VERIFY_JOB_NAME));

        if (jobs.length == 1) {
            return jobs[0];
        }

        // Create the default verify job
        JobTemplate jjt = ao.create(JobTemplate.class,
            new DBParam("NAME", DEFAULT_VERIFY_JOB_NAME),
            new DBParam("TEMPLATE_FILE", "src/main/resources/jenkins-verify-job.vm"),
            new DBParam("JOB_TYPE", JenkinsJobType.VERIFY_BUILD)
            );
        jjt.save();
        return jjt;
    }

    public JobTemplate getDefaultPublishJob() {
        JobTemplate[] jobs =
            ao.find(JobTemplate.class, Query.select().where("NAME = ?", DEFAULT_PUBLISH_JOB_NAME));

        if (jobs.length == 1) {
            return jobs[0];
        }

        // Create the default verify job
        JobTemplate jjt = ao.create(JobTemplate.class,
            new DBParam("NAME", DEFAULT_PUBLISH_JOB_NAME),
            new DBParam("TEMPLATE_FILE", "src/main/resources/jenkins-publish-job.vm"),
            new DBParam("JOB_TYPE", JenkinsJobType.RELEASE_BUILD)
            );
        jjt.save();
        return jjt;
    }

    public JobTemplate getJobTemplate(String name) throws SQLException {
        JobTemplate[] jobs =
            ao.find(JobTemplate.class, Query.select().where("NAME = ?", name));
        if (jobs.length == 0) {
            // just use the defaults
            return ao.create(JobTemplate.class,
                new DBParam("NAME", name),
                new DBParam("TEMPLATE_FILE", "verify-template.xml"),
                new DBParam("JOB_TYPE", JenkinsJobType.NOOP_BUILD)
                );
        }

        return jobs[0];
    }

    public void setJobTemplate(String name, String templateFile, JenkinsJobType jenkinsJobType)
        throws SQLException {
        JobTemplate[] jobs =
            ao.find(JobTemplate.class, Query.select().where("NAME = ?", name));

        if (jobs.length == 0) {
            log.info("Creating jenkins job template: " + name);
            ao.create(JobTemplate.class,
                new DBParam("NAME", name),
                new DBParam("TEMPLATE_FILE", templateFile),
                new DBParam("JOB_TYPE", jenkinsJobType)
                );
            return;
        }
        // already exists, so update it
        jobs[0].setTemplateFile(templateFile);
        jobs[0].setJobType(jenkinsJobType);
        jobs[0].save();
    }

    public void setJenkinsJobMapping(RepositoryConfiguration rc, JobTemplate jjt, Boolean isVisible,
        Boolean isEnabled) throws SQLException {
        JobMapping[] mappings =
            ao.find(JobMapping.class,
                Query.select().where("REPOSITORY_CONFIGURATION = ? and JENKINS_JOB_TEMPLATE = ?", rc, jjt));
        if (mappings.length == 0) {
            // just use the defaults
            JobMapping jjm = ao.create(JobMapping.class,
                new DBParam("REPOSITORY_CONFIGURATION", rc),
                new DBParam("JOB_TEMPLATE", jjt)
                );
            if (isVisible != null) {
                jjm.setVisible(isVisible);
            }
            if (isEnabled != null) {
                jjm.setEnabled(isEnabled);
            }
            jjm.save();
        }
    }

    public ImmutableList<JobTemplate> getJenkinsJobsForRepository(RepositoryConfiguration rc)
        throws SQLException {
        JobTemplate[] jobs =
            ao.find(JobTemplate.class,
                Query.select().where("REPOSITORY_CONFIGURATION = ? AND VISIBLE = ?", rc, true));

        return ImmutableList.copyOf(jobs);
    }
}
