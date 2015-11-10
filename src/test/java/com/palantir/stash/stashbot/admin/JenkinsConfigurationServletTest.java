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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
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
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.atlassian.webresource.api.assembler.RequiredResources;
import com.atlassian.webresource.api.assembler.WebResourceAssembler;
import com.google.common.collect.ImmutableList;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceImpl;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.PluginUserManager;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;
import com.palantir.stash.stashbot.servlet.JenkinsConfigurationServlet;

public class JenkinsConfigurationServletTest {

    @Mock
    private ConfigurationPersistenceImpl cpm;
    @Mock
    private PageBuilderService pageBuilderService;
    @Mock
    private WebResourceAssembler webResourceAssembler;
    @Mock
    private RequiredResources rr;
    @Mock
    private SoyTemplateRenderer soyTemplateRenderer;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private PrintWriter writer;
    @Mock
    private JenkinsServerConfiguration jsc;
    @Mock
    private JenkinsServerConfiguration jsc2;
    @Mock
    private PluginUserManager pum;
    @Mock
    private LoginUriProvider lup;
    @Mock
    private PermissionValidationService pvs;

    private JenkinsConfigurationServlet jcs;

    private static final String JN = "default";
    private static final String JURL = "JenkinsURL";
    private static final String JU = "JenkisnUsername";
    private static final String JP = "JenkinsPassword";
    private static final String SU = "StashUsername";
    private static final String SP = "StashPassword";
    private static final String MVC_S = "10";
    private static final Integer MVC = 10;
    private static final String USER = "logged_in_user";
    private static final URI LOGIN_URI = URI.create("https://stash.example.com/");

    private static final String REQUEST_URI = "http://someuri.example.com/blah";

    private final PluginLoggerFactory lf = new PluginLoggerFactory();

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        when(res.getWriter()).thenReturn(writer);
        when(req.getRequestURL()).thenReturn(new StringBuffer(REQUEST_URI));
        when(req.getPathInfo()).thenReturn("");
        when(req.getRemoteUser()).thenReturn(USER);

        when(lup.getLoginUri(any(URI.class))).thenReturn(LOGIN_URI);

        when(cpm.getJenkinsServerConfiguration(null)).thenReturn(jsc);
        when(cpm.getJenkinsServerConfiguration(JN)).thenReturn(jsc);
        when(cpm.getJenkinsServerConfiguration(JN + "2")).thenReturn(jsc2);
        when(cpm.getAllJenkinsServerConfigurations()).thenReturn(ImmutableList.of(jsc));

        when(jsc.getName()).thenReturn(JN);
        when(jsc.getUrl()).thenReturn(JURL);
        when(jsc.getUsername()).thenReturn(JU);
        when(jsc.getPassword()).thenReturn(JP);
        when(jsc.getStashUsername()).thenReturn(SU);
        when(jsc.getStashPassword()).thenReturn(SP);
        when(jsc.getMaxVerifyChain()).thenReturn(MVC);
        when(jsc.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);

        when(jsc2.getName()).thenReturn(JN + "2");
        when(jsc2.getUrl()).thenReturn(JURL + "2");
        when(jsc2.getUsername()).thenReturn(JU + "2");
        when(jsc2.getPassword()).thenReturn(JP + "2");
        when(jsc2.getStashUsername()).thenReturn(SU + "2");
        when(jsc2.getStashPassword()).thenReturn(SP + "2");
        when(jsc2.getMaxVerifyChain()).thenReturn(MVC);
        when(jsc2.getAuthenticationMode()).thenReturn(AuthenticationMode.USERNAME_AND_PASSWORD);

        when(pageBuilderService.assembler()).thenReturn(webResourceAssembler);
        when(webResourceAssembler.resources()).thenReturn(rr);

        jcs = new JenkinsConfigurationServlet(soyTemplateRenderer, pageBuilderService, cpm, pum, null, lup, lf, pvs);
    }

    @Test
    public void getTestWhenNotLoggedIn() throws Exception {

        when(req.getRemoteUser()).thenReturn(null);
        doThrow(
            new AuthorisationException(new KeyedMessage("testException", "testException", "testException")))
            .when(pvs).validateAuthenticated();

        jcs.doGet(req, res);

        verify(res).sendRedirect(Mockito.anyString());
        verify(res, Mockito.never()).getWriter();
    }

    @Test
    public void getTestWhenNotSysAdmin() throws Exception {

        when(req.getRemoteUser()).thenReturn("nonAdminStashUser");
        doThrow(
            new AuthorisationException(new KeyedMessage("testException", "testException", "testException")))
            .when(pvs).validateForGlobal(Permission.SYS_ADMIN);

        jcs.doGet(req, res);

        verify(res).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any(String.class));
    }

    @Test
    public void getTest() throws Exception {

        jcs.doGet(req, res);

        verify(res).setContentType("text/html;charset=UTF-8");
        verify(rr).requireContext("plugin.page.stashbot");

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Map<String, Object>> cls = (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        verify(soyTemplateRenderer).render(eq(writer),
            eq("com.palantir.stash.stashbot:stashbotConfigurationResources"),
            eq("plugin.page.stashbot.jenkinsConfigurationPanel"), mapCaptor.capture());

        verify(pum, Mockito.never()).addUserToRepoForReading(any(String.class),
            any(Repository.class));
        verify(pum, Mockito.never()).createStashbotUser(any(JenkinsServerConfiguration.class));

        Map<String, Object> map = mapCaptor.getValue();

        @SuppressWarnings("unchecked")
        List<JenkinsServerConfiguration> jscs = (List<JenkinsServerConfiguration>) map.get("jenkinsConfigs");
        Assert.assertEquals(jsc, jscs.get(0));
    }

    @Test
    public void postTest() throws Exception {

        // test that things are actually updated
        when(req.getParameter("name")).thenReturn(JN + "2");
        when(req.getParameter("url")).thenReturn(JURL + "2");
        when(req.getParameter("username")).thenReturn(JU + "2");
        when(req.getParameter("password")).thenReturn(JP + "2");
        when(req.getParameter("stashUsername")).thenReturn(SU + "2");
        when(req.getParameter("stashPassword")).thenReturn(SP + "2");
        when(req.getParameter("emailNotificationsEnabled")).thenReturn("true");
        when(req.getParameter("maxVerifyChain")).thenReturn(MVC_S);

        jcs.doPost(req, res);

        // Verify it persists
        verify(cpm).setJenkinsServerConfigurationFromRequest(req);

        // doGet() is then called, so this is the same as getTest()...
        verify(res).setContentType("text/html;charset=UTF-8");
        verify(rr).requireContext("plugin.page.stashbot");

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Map<String, Object>> cls = (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        verify(soyTemplateRenderer).render(eq(writer),
            eq("com.palantir.stash.stashbot:stashbotConfigurationResources"),
            eq("plugin.page.stashbot.jenkinsConfigurationPanel"), mapCaptor.capture());

        verify(pum, atLeastOnce()).createStashbotUser(any(JenkinsServerConfiguration.class));

        Map<String, Object> map = mapCaptor.getValue();

        // Except the details are now changed
        @SuppressWarnings("unchecked")
        List<JenkinsServerConfiguration> jscs = (List<JenkinsServerConfiguration>) map.get("jenkinsConfigs");
        Assert.assertEquals(jsc, jscs.get(0));
    }
}
