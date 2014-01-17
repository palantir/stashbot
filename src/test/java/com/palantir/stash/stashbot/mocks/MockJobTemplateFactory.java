package com.palantir.stash.stashbot.mocks;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;

import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JobMapping;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;

public class MockJobTemplateFactory {

    private final JobTemplateManager jtm;

    private final List<JobTemplate> templates;
    private final List<JobMapping> mappings;

    public MockJobTemplateFactory(JobTemplateManager jtm) {
        this.jtm = jtm;
        this.templates = new ArrayList<JobTemplate>();
        this.mappings = new ArrayList<JobMapping>();
    }

    public void generateDefaultsForRepo(Repository repo, RepositoryConfiguration rc) throws Exception {
        JobTemplate verifyCommit = getJobTemplate(repo, rc, JobType.VERIFY_COMMIT);
        JobTemplate verifyPR = getJobTemplate(repo, rc, JobType.VERIFY_PR);
        JobTemplate publish = getJobTemplate(repo, rc, JobType.PUBLISH);

        Mockito.when(jtm.getDefaultVerifyJob()).thenReturn(verifyCommit);
        Mockito.when(jtm.getDefaultVerifyPullRequestJob()).thenReturn(verifyPR);
        Mockito.when(jtm.getDefaultPublishJob()).thenReturn(publish);
    }

    public JobTemplate getJobTemplate(Repository repo, RepositoryConfiguration rc, JobType jt) throws Exception {
        JobTemplate template = Mockito.mock(JobTemplate.class);

        Mockito.when(template.getJobType()).thenReturn(jt);

        Mockito.when(template.getName()).thenReturn(jt.toString());
        Mockito.when(template.getBuildNameFor(repo)).thenReturn("somename_" + jt.toString());
        Mockito.when(template.getTemplateFile()).thenReturn("src/test/resources/test-template.vm");

        JobMapping jm = Mockito.mock(JobMapping.class);
        Mockito.when(jm.getRepositoryConfiguration()).thenReturn(rc);
        Mockito.when(jm.getJobTemplate()).thenReturn(template);
        Mockito.when(jm.isVisible()).thenReturn(true);
        Mockito.when(jm.isEnabled()).thenReturn(true);

        Mockito.when(jtm.getJenkinsJobsForRepository(rc)).thenReturn(ImmutableList.copyOf(templates));
        Mockito.when(jtm.fromString(rc, jt.toString())).thenReturn(template);

        templates.add(template);
        mappings.add(jm);
        return template;
    }

    public List<JobTemplate> getMockTemplates() {
        return templates;
    }

    public List<JobMapping> getJobMappings() {
        return mappings;
    }
}
