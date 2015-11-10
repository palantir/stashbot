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
package com.palantir.stash.stashbot.admin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.bitbucket.AuthorisationException;
import com.atlassian.bitbucket.i18n.KeyedMessage;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionValidationService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestSearchRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.atlassian.webresource.api.assembler.RequiredResources;
import com.atlassian.webresource.api.assembler.WebResourceAssembler;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.managers.PluginUserManager;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.servlet.RepoConfigurationServlet;

public class RepoConfigurationServletTest {

    @Mock
    private ConfigurationPersistenceImpl cpm;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private PullRequestService prs;
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

    @Mock
    private Page<PullRequest> page;

    private ImmutableCollection<JenkinsServerConfiguration> allServers;

    private RepoConfigurationServlet rcs;

    private static final String PBR = "publishBranchRegexString";
    private static final String VBR = "verifyBranchRegexString";
    private static final String PBC = "publishBranchCommandString";
    private static final String VBC = "verifyBranchCommandString";
    private static final String PREBC = "prebuildCommandString";
    private static final String JSN = "default";
    private static final boolean RB = true;

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        Mockito.when(res.getWriter()).thenReturn(writer);
        Mockito.when(req.getPathInfo()).thenReturn("/projectName/repoName");
        Mockito.when(repositoryService.getBySlug("projectName", "repoName")).thenReturn(mockRepo);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc);
        Mockito.when(cpm.getAllJenkinsServerNames()).thenReturn(ImmutableList.of(JSN, JSN + "2"));

        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getPublishBranchRegex()).thenReturn(PBR);
        Mockito.when(rc.getPublishBuildCommand()).thenReturn(PBC);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(VBR);
        Mockito.when(rc.getVerifyBuildCommand()).thenReturn(VBC);
        Mockito.when(rc.getPrebuildCommand()).thenReturn(PREBC);
        Mockito.when(rc.getJenkinsServerName()).thenReturn(JSN);
        Mockito.when(rc.getRebuildOnTargetUpdate()).thenReturn(RB);
        Mockito.when(rc.getVerifyPinned()).thenReturn(false);
        Mockito.when(rc.getVerifyLabel()).thenReturn("N/A");
        Mockito.when(rc.getPublishPinned()).thenReturn(false);
        Mockito.when(rc.getPublishLabel()).thenReturn("N/A");
        Mockito.when(rc.getJunitEnabled()).thenReturn(false);
        Mockito.when(rc.getJunitPath()).thenReturn("N/A");
        Mockito.when(rc.getArtifactsEnabled()).thenReturn(false);
        Mockito.when(rc.getArtifactsPath()).thenReturn("N/A");
        Mockito.when(rc.getEmailNotificationsEnabled()).thenReturn(false);
        Mockito.when(rc.getEmailRecipients()).thenReturn("a@example.com");
        Mockito.when(rc.getEmailForEveryUnstableBuild()).thenReturn(false);
        Mockito.when(rc.getEmailPerModuleEmail()).thenReturn(false);
        Mockito.when(rc.getEmailSendToIndividuals()).thenReturn(false);

        Mockito.when(rc2.getPublishBranchRegex()).thenReturn(PBR + "2");
        Mockito.when(rc2.getPublishBuildCommand()).thenReturn(PBC + "2");
        Mockito.when(rc2.getVerifyBranchRegex()).thenReturn(VBR + "2");
        Mockito.when(rc2.getVerifyBuildCommand()).thenReturn(VBC + "2");
        Mockito.when(rc2.getPrebuildCommand()).thenReturn(PREBC + "2");
        Mockito.when(rc2.getJenkinsServerName()).thenReturn(JSN + "2");
        Mockito.when(rc2.getRebuildOnTargetUpdate()).thenReturn(RB);
        Mockito.when(rc2.getVerifyPinned()).thenReturn(false);
        Mockito.when(rc2.getVerifyLabel()).thenReturn("N/A");
        Mockito.when(rc2.getPublishPinned()).thenReturn(false);
        Mockito.when(rc2.getPublishLabel()).thenReturn("N/A");
        Mockito.when(rc2.getJunitEnabled()).thenReturn(false);
        Mockito.when(rc2.getJunitPath()).thenReturn("N/A");
        Mockito.when(rc2.getArtifactsEnabled()).thenReturn(false);
        Mockito.when(rc2.getArtifactsPath()).thenReturn("N/A");
        Mockito.when(rc2.getEmailNotificationsEnabled()).thenReturn(true);
        Mockito.when(rc2.getEmailRecipients()).thenReturn("a@example.com");
        Mockito.when(rc2.getEmailForEveryUnstableBuild()).thenReturn(true);
        Mockito.when(rc2.getEmailPerModuleEmail()).thenReturn(true);
        Mockito.when(rc2.getEmailSendToIndividuals()).thenReturn(true);

        Mockito.when(jsc.getName()).thenReturn(JSN);
        Mockito.when(jsc.getStashUsername()).thenReturn("someuser");
        Mockito.when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);
        Mockito.when(jsc2.getName()).thenReturn(JSN + "2");
        Mockito.when(jsc2.getStashUsername()).thenReturn("someuser");
        Mockito.when(jsc2.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);

        allServers = ImmutableList.of(jsc, jsc2);
        when(cpm.getAllJenkinsServerConfigurations()).thenReturn(allServers);
        when(cpm.getJenkinsServerConfiguration(JSN)).thenReturn(jsc);
        when(cpm.getJenkinsServerConfiguration(JSN + "2")).thenReturn(jsc2);

        when(pageBuilderService.assembler()).thenReturn(webResourceAssembler);
        when(webResourceAssembler.resources()).thenReturn(rr);

        Mockito.when(prs.search(Mockito.any(PullRequestSearchRequest.class), Mockito.any(PageRequest.class)))
            .thenReturn(page);
        // empty list, no PRs, nothign to do.  not testing this bit.
        Mockito.when(page.getValues()).thenReturn(ImmutableList.of());
        Mockito.when(page.getIsLastPage()).thenReturn(true);

        rcs =
            new RepoConfigurationServlet(repositoryService, prs, soyTemplateRenderer, pageBuilderService,
                cpm, jenkinsManager, pum, pvs, lf);
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
    public void postTestDisabled() throws Exception {

        when(req.getParameter("jenkinsServerName")).thenReturn("default");
        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc2);
        when(rc2.getCiEnabled()).thenReturn(false);

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

        verify(pum, Mockito.never()).addUserToRepoForReading(Mockito.anyString(),
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

    @Test
    public void postTestEnabled() throws Exception {

        when(req.getParameter("jenkinsServerName")).thenReturn("default");
        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc2);
        when(rc2.getCiEnabled()).thenReturn(true);

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
        assertEquals(true, map.get("ciEnabled"));
        assertEquals(PBR + "2", map.get("publishBranchRegex"));
        assertEquals(PBC + "2", map.get("publishBuildCommand"));
        assertEquals(VBR + "2", map.get("verifyBranchRegex"));
        assertEquals(VBC + "2", map.get("verifyBuildCommand"));
        assertEquals(PREBC + "2", map.get("prebuildCommand"));
    }

    @Test
    public void postTestWhenLockedSrc() throws Exception {

        when(jsc.getLocked()).thenReturn(true);
        when(jsc2.getLocked()).thenReturn(false);
        Exception permissionException =
            new AuthorisationException(new KeyedMessage("permission exceptionz", "", ""));
        Mockito.doThrow(permissionException).when(pvs).validateForGlobal(Permission.SYS_ADMIN);

        when(req.getParameter("jenkinsServerName")).thenReturn("default2");

        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc);

        rcs.doPost(req, res);
        verify(res).sendError(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void postTestWhenLockedDst() throws Exception {

        when(jsc.getLocked()).thenReturn(false);
        when(jsc2.getLocked()).thenReturn(true);
        Exception permissionException =
            new AuthorisationException(new KeyedMessage("permission exceptionz", "", ""));
        Mockito.doThrow(permissionException).when(pvs).validateForGlobal(Permission.SYS_ADMIN);

        when(req.getParameter("jenkinsServerName")).thenReturn("default2");

        when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc);

        rcs.doPost(req, res);
        verify(res).sendError(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void testAddsUserToRepo() throws SQLException, ServletException, IOException {
        when(req.getParameter("jenkinsServerName")).thenReturn("default");
        when(cpm.getDefaultPublicSshKey()).thenReturn("somekey");
        when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.CREDENTIAL_AUTOMATIC_SSH_KEY);

        rcs.doPost(req, res);

        // verify the key is added
        verify(pum).addUserToRepoForReading("someuser", mockRepo);
    }
}
