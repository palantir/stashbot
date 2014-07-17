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
package com.palantir.stash.stashbot.admin;

import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.stash.exception.AuthorisationException;
import com.atlassian.stash.i18n.KeyedMessage;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.atlassian.webresource.api.assembler.RequiredResources;
import com.atlassian.webresource.api.assembler.WebResourceAssembler;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.managers.PluginUserManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RepoConfigurationServletTest {

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private PageBuilderService pageBuilderService;
    @Mock
    private WebResourceAssembler webResourceAssembler;
    @Mock
    private RequiredResources rr;
    @Mock
    private SoyTemplateRenderer soyTemplateRenderer;
    @Mock
    private JenkinsManager jenkinsManager;
    @Mock
    private PluginUserManager pum;
    @Mock
    private PermissionValidationService pvs;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private PrintWriter writer;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private RepositoryConfiguration rc2;
    @Mock
    private Repository mockRepo;
    @Mock
    private JenkinsServerConfiguration jsc;
    @Mock
    private JenkinsServerConfiguration jsc2;

    private ImmutableCollection<JenkinsServerConfiguration> allServers;

    private RepoConfigurationServlet rcs;

    private static final String PBR = "publishBranchRegexString";
    private static final String VBR = "verifyBranchRegexString";
    private static final String PBC = "publishBranchCommandString";
    private static final String VBC = "verifyBranchCommandString";
    private static final String PREBC = "prebuildCommandString";
    private static final String JSN = "default";
    private static final boolean RB = true;

    private final StashbotLoggerFactory lf = new StashbotLoggerFactory();

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        when(res.getWriter()).thenReturn(writer);
        when(req.getPathInfo()).thenReturn("/projectName/repoName");
        when(repositoryService.findBySlug("projectName", "repoName")).thenReturn(mockRepo);
        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc);
        when(cpm.getAllJenkinsServerNames()).thenReturn(ImmutableList.of("default"));

        when(rc.getCiEnabled()).thenReturn(true);
        when(rc.getPublishBranchRegex()).thenReturn(PBR);
        when(rc.getPublishBuildCommand()).thenReturn(PBC);
        when(rc.getVerifyBranchRegex()).thenReturn(VBR);
        when(rc.getVerifyBuildCommand()).thenReturn(VBC);
        when(rc.getPrebuildCommand()).thenReturn(PREBC);
        when(rc.getJenkinsServerName()).thenReturn(JSN);
        when(rc.getRebuildOnTargetUpdate()).thenReturn(RB);
        when(rc.getVerifyPinned()).thenReturn(false);
        when(rc.getVerifyLabel()).thenReturn("N/A");
        when(rc.getPublishPinned()).thenReturn(false);
        when(rc.getPublishLabel()).thenReturn("N/A");
        when(rc.getJunitEnabled()).thenReturn(false);
        when(rc.getJunitPath()).thenReturn("N/A");
        when(rc.getEmailNotificationsEnabled()).thenReturn(false);
        when(rc.getEmailRecipients()).thenReturn("empty");
        when(rc.getEmailForEveryUnstableBuild()).thenReturn(false);
        when(rc.getEmailPerModuleEmail()).thenReturn(false);
        when(rc.getEmailSendToIndividuals()).thenReturn(false);

        when(rc2.getPublishBranchRegex()).thenReturn(PBR + "2");
        when(rc2.getPublishBuildCommand()).thenReturn(PBC + "2");
        when(rc2.getVerifyBranchRegex()).thenReturn(VBR + "2");
        when(rc2.getVerifyBuildCommand()).thenReturn(VBC + "2");
        when(rc2.getPrebuildCommand()).thenReturn(PREBC + "2");
        when(rc2.getJenkinsServerName()).thenReturn(JSN + "2");
        when(rc2.getRebuildOnTargetUpdate()).thenReturn(RB);
        when(rc2.getVerifyPinned()).thenReturn(false);
        when(rc2.getVerifyLabel()).thenReturn("N/A");
        when(rc2.getPublishPinned()).thenReturn(false);
        when(rc2.getPublishLabel()).thenReturn("N/A");
        when(rc2.getJunitEnabled()).thenReturn(false);
        when(rc2.getJunitPath()).thenReturn("N/A");
        when(rc2.getEmailNotificationsEnabled()).thenReturn(true);
        when(rc2.getEmailRecipients()).thenReturn("a@a.a");
        when(rc2.getEmailForEveryUnstableBuild()).thenReturn(true);
        when(rc2.getEmailPerModuleEmail()).thenReturn(true);
        when(rc2.getEmailSendToIndividuals()).thenReturn(true);
        when(jsc.getName()).thenReturn(JSN);
        when(jsc.getStashUsername()).thenReturn("someuser");
        when(jsc2.getName()).thenReturn(JSN + "2");
        when(jsc2.getStashUsername()).thenReturn("someuser");



        allServers = ImmutableList.of(jsc, jsc2);
        when(cpm.getAllJenkinsServerConfigurations()).thenReturn(allServers);
        when(cpm.getJenkinsServerConfiguration(JSN)).thenReturn(jsc);
        when(cpm.getJenkinsServerConfiguration(JSN + "2")).thenReturn(jsc2);

        when(pageBuilderService.assembler()).thenReturn(webResourceAssembler);
        when(webResourceAssembler.resources()).thenReturn(rr);

        rcs =
            new RepoConfigurationServlet(repositoryService, soyTemplateRenderer, pageBuilderService, cpm,
                jenkinsManager, pum, pvs, lf);
    }

    @Test
    public void getTestWhenNotRepoAdmin() throws Exception {

        doThrow(new AuthorisationException(new KeyedMessage("testException", "testException", "testException")))
            .when(pvs).validateForRepository(Mockito.any(Repository.class), eq(Permission.REPO_ADMIN));

        rcs.doGet(req, res);

        verify(res).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), Mockito.any(String.class));
    }

    @Test
    public void getTest() throws Exception {

        rcs.doGet(req, res);

        verify(res).setContentType("text/html;charset=UTF-8");
        verify(rr).requireContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        verify(soyTemplateRenderer).render(eq(writer),
            eq("com.palantir.stash.stashbot:stashbotConfigurationResources"),
            eq("plugin.page.stashbot.repositoryConfigurationPanel"), mapCaptor.capture());

        verify(pum, Mockito.never())
            .addUserToRepoForReading(Mockito.anyString(), Mockito.any(Repository.class));

        Map<String, Object> map = mapCaptor.getValue();

        assertEquals(true, map.get("ciEnabled"));
        assertEquals(PBR, map.get("publishBranchRegex"));
        assertEquals(PBC, map.get("publishBuildCommand"));
        assertEquals(VBR, map.get("verifyBranchRegex"));
        assertEquals(VBC, map.get("verifyBuildCommand"));
    }

    @Test
    public void postTest() throws Exception {

        when(req.getParameter("jenkinsServerName")).thenReturn("default");

        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc2);

        rcs.doPost(req, res);

        // Verify it persists
        verify(cpm).setRepositoryConfigurationForRepositoryFromRequest(mockRepo, req);

        // doGet() is then called, so this is the same as getTest()...
        verify(res).setContentType("text/html;charset=UTF-8");
        verify(rr).requireContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        verify(soyTemplateRenderer).render(eq(writer),
            eq("com.palantir.stash.stashbot:stashbotConfigurationResources"),
            eq("plugin.page.stashbot.repositoryConfigurationPanel"), mapCaptor.capture());

        verify(pum, Mockito.atLeastOnce()).addUserToRepoForReading(Mockito.anyString(),
            Mockito.any(Repository.class));

        Map<String, Object> map = mapCaptor.getValue();

        // Except the details are now changed
        assertEquals(false, map.get("ciEnabled"));
        assertEquals(PBR + "2", map.get("publishBranchRegex"));
        assertEquals(PBC + "2", map.get("publishBuildCommand"));
        assertEquals(VBR + "2", map.get("verifyBranchRegex"));
        assertEquals(VBC + "2", map.get("verifyBuildCommand"));
        assertEquals(PREBC + "2", map.get("prebuildCommand"));
    }
}
