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
package com.palantir.stash.stashbot.mocks;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;

import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.jobtemplate.JobTemplateManager;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobMapping;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

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
        Mockito.when(jtm.getJenkinsJobsForRepository(rc)).thenReturn(ImmutableList.copyOf(templates));
    }

    public JobTemplate getJobTemplate(Repository repo, RepositoryConfiguration rc, JobType jt) throws Exception {
        JobTemplate template = Mockito.mock(JobTemplate.class);

        Mockito.when(template.getJobType()).thenReturn(jt);

        Mockito.when(template.getName()).thenReturn(jt.toString());
        Mockito.when(template.getBuildNameFor(eq(repo), any(JenkinsServerConfiguration.class)))
            .thenReturn(
                "somename_" + jt.toString());
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
