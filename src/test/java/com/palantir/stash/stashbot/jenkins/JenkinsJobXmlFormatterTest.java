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
package com.palantir.stash.stashbot.jenkins;

import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

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

import com.atlassian.bitbucket.nav.NavBuilder;
import com.atlassian.bitbucket.nav.NavBuilder.BrowseRepoResource;
import com.atlassian.bitbucket.nav.NavBuilder.Repo;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryCloneLinksRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.NamedLink;
import com.atlassian.bitbucket.util.SimpleNamedLink;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.managers.VelocityManager;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsJobXmlFormatterTest {

    private JenkinsJobXmlFormatter jjxf;

    private static final String TEMPLATE_NAME = "src/test/resources/test-template.vm";
    private static final String EXAMPLE_XML_TEXT = "<xml></xml>";
    private static final String ABSOLUTE_PATH = "http://www.example.com/stash";
    private static final String ABSOLUTE_PATH2 = "http://www.example.com/stash/browse";
    private static final String REPO_URL = "http://www.example.com/stash/repo.git";
    private static final String STASH_PW = "somepassword";

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
    private ConfigurationPersistenceService cpm;
    @Mock
    private StashbotUrlBuilder sub;
    @Mock
    private RepositoryService rs;

    // nav builder intermediaries - god damn this is annoying to mock
    @Mock
    private NavBuilder navBuilder;
    @Mock
    private Repo nbRepo;
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

    private Set<NamedLink> links;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        links = new HashSet<NamedLink>();
        links.add(new SimpleNamedLink(ABSOLUTE_PATH, "http"));

        Mockito.when(cpm.getRepositoryConfigurationForRepository(repo)).thenReturn(rc);
        Mockito.when(rc.getJenkinsServerName()).thenReturn("NAME");
        Mockito.when(cpm.getJenkinsServerConfiguration("NAME")).thenReturn(jsc);

        Mockito.when(navBuilder.repo(Mockito.any(Repository.class))).thenReturn(nbRepo);
        Mockito.when(navBuilder.buildAbsolute()).thenReturn(ABSOLUTE_PATH);
        Mockito.when(rs.getCloneLinks(Mockito.any(RepositoryCloneLinksRequest.class))).thenReturn(links);
        Mockito.when(nbRepo.browse()).thenReturn(nbRepoBrowse);
        Mockito.when(nbRepoBrowse.buildAbsolute()).thenReturn(ABSOLUTE_PATH2);

        Mockito.when(jsc.getUrl()).thenReturn(REPO_URL);
        Mockito.when(jsc.getStashUsername()).thenReturn(REPO_URL);
        Mockito.when(jsc.getStashPassword()).thenReturn(STASH_PW);
        Mockito.when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);

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

        jjxf = new JenkinsJobXmlFormatter(velocityManager, cpm, sub, navBuilder, rs);
    }

    @Test
    public void testJJXF() throws Exception {

        String jobXml = jjxf.generateJobXml(jobTemplate, repo);

        Mockito.verify(velocityTemplate).merge(Mockito.eq(velocityContext), Mockito.any(Writer.class));
        Mockito.verify(velocityContext, Mockito.never()).put(Mockito.eq("credentailUUID"), Mockito.anyObject());

        Assert.assertEquals(EXAMPLE_XML_TEXT, jobXml);
    }

    @Test
    public void testJJXFWithAuthMode() throws Exception {

        Mockito.when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.CREDENTIAL_MANUALLY_CONFIGURED);

        String jobXml = jjxf.generateJobXml(jobTemplate, repo);

        Mockito.verify(velocityTemplate).merge(Mockito.eq(velocityContext), Mockito.any(Writer.class));
        Mockito.verify(velocityContext).put(Mockito.eq("credentialUUID"), Mockito.eq(STASH_PW));

        Assert.assertEquals(EXAMPLE_XML_TEXT, jobXml);
    }
}
