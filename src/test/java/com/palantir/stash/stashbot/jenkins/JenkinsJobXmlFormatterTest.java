// Copyright 2013 Palantir Technologies
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
package com.palantir.stash.stashbot.jenkins;

import java.io.Writer;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.nav.NavBuilder.BrowseRepoResource;
import com.atlassian.stash.nav.NavBuilder.Repo;
import com.atlassian.stash.nav.NavBuilder.RepoClone;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.ssh.api.SshCloneUrlResolver;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobTemplate;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.managers.VelocityManager;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsJobXmlFormatterTest {

    private JenkinsJobXmlFormatter jjxf;

    private static final String TEMPLATE_NAME = "src/test/resources/test-template.vm";
    private static final String EXAMPLE_XML_TEXT = "<xml></xml>";
    private static final String ABSOLUTE_PATH = "http://www.example.com/stash";
    private static final String ABSOLUTE_PATH2 = "http://www.example.com/stash/browse";
    private static final String REPO_URL = "http://www.example.com/stash/repo.git";

    @Mock
    private VelocityManager velocityManager;
    @Mock
    private VelocityEngine velocityEngine;
    @Mock
    private VelocityContext velocityContext;
    @Mock
    private Template velocityTemplate;
    @Mock
    private JobTemplate jobTemplate;
    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private StashbotUrlBuilder sub;

    // nav builder intermediaries - god damn this is annoying to mock
    @Mock
    private NavBuilder navBuilder;
    @Mock
    private SshCloneUrlResolver sshCloneUrlResolver;
    @Mock
    private Repo nbRepo;
    @Mock
    private RepoClone nbRepoClone;
    @Mock
    private BrowseRepoResource nbRepoBrowse;

    @Mock
    private Repository repo;
    @Mock
    private Project project;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getJenkinsServerName()).thenReturn("NAME");
        Mockito.when(cpm.getJenkinsServerConfiguration("NAME")).thenReturn(jsc);

        Mockito.when(navBuilder.repo(Mockito.any(Repository.class))).thenReturn(nbRepo);
        Mockito.when(navBuilder.buildAbsolute()).thenReturn(ABSOLUTE_PATH);
        Mockito.when(nbRepo.clone(Mockito.anyString())).thenReturn(nbRepoClone);
        Mockito.when(nbRepo.browse()).thenReturn(nbRepoBrowse);
        Mockito.when(nbRepoClone.buildAbsoluteWithoutUsername()).thenReturn(ABSOLUTE_PATH);
        Mockito.when(nbRepoBrowse.buildAbsolute()).thenReturn(ABSOLUTE_PATH2);

        Mockito.when(jsc.getUrl()).thenReturn(REPO_URL);
        Mockito.when(jsc.getStashUsername()).thenReturn(REPO_URL);
        Mockito.when(jsc.getStashPassword()).thenReturn(REPO_URL);

        Mockito.when(velocityManager.getVelocityEngine()).thenReturn(velocityEngine);
        Mockito.when(velocityManager.getVelocityContext()).thenReturn(velocityContext);

        Mockito.when(velocityEngine.getTemplate(TEMPLATE_NAME)).thenReturn(velocityTemplate);

        Mockito.when(jobTemplate.getJobType()).thenReturn(JobType.VERIFY_COMMIT);
        Mockito.when(jobTemplate.getTemplateFile()).thenReturn(TEMPLATE_NAME);
        Mockito.when(repo.getProject()).thenReturn(project);
        Mockito.when(repo.getName()).thenReturn("reponame");
        Mockito.when(project.getName()).thenReturn("projectname");

        final ArgumentCaptor<Writer> writerCaptor = ArgumentCaptor.forClass(Writer.class);
        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Writer w = writerCaptor.getValue();
                w.append(EXAMPLE_XML_TEXT);
                return null;
            }
        }).when(velocityTemplate).merge(Mockito.eq(velocityContext), writerCaptor.capture());

        jjxf = new JenkinsJobXmlFormatter(velocityManager, cpm, sub, navBuilder, sshCloneUrlResolver);
    }

    @Test
    public void testJJXF() throws Exception {

        String jobXml = jjxf.generateJobXml(jobTemplate, repo);

        Mockito.verify(velocityTemplate).merge(Mockito.eq(velocityContext), Mockito.any(Writer.class));

        Assert.assertEquals(EXAMPLE_XML_TEXT, jobXml);
    }
}
