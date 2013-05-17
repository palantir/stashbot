package com.palantir.stash.stashbothelper.admin;

import java.io.PrintWriter;
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

import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.palantir.stash.stashbothelper.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbothelper.config.JenkinsServerConfiguration;

public class JenkinsConfigurationServletTest {

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private WebResourceManager webResourceManager;
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

    private JenkinsConfigurationServlet jcs;

    private static final String JN = "default";
    private static final String JURL = "JenkinsURL";
    private static final String JU = "JenkisnUsername";
    private static final String JP = "JenkinsPassword";

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        Mockito.when(res.getWriter()).thenReturn(writer);
        Mockito.when(cpm.getJenkinsServerConfiguration()).thenReturn(jsc);

        Mockito.when(jsc.getName()).thenReturn(JN);
        Mockito.when(jsc.getUrl()).thenReturn(JURL);
        Mockito.when(jsc.getUsername()).thenReturn(JU);
        Mockito.when(jsc.getPassword()).thenReturn(JP);

        Mockito.when(jsc2.getName()).thenReturn(JN + "2");
        Mockito.when(jsc2.getUrl()).thenReturn(JURL + "2");
        Mockito.when(jsc2.getUsername()).thenReturn(JU + "2");
        Mockito.when(jsc2.getPassword()).thenReturn(JP + "2");

        jcs = new JenkinsConfigurationServlet(soyTemplateRenderer, webResourceManager, cpm);
    }

    @Test
    public void getTest() throws Exception {

        jcs.doGet(req, res);

        Mockito.verify(res).setContentType("text/html;charset=UTF-8");
        Mockito.verify(webResourceManager).requireResourcesForContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class<Map<String, Object>>) (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        Mockito.verify(soyTemplateRenderer).render(Mockito.eq(writer),
            Mockito.eq("com.palantir.stash.stashbot-helper:stashbotConfigurationResources"),
            Mockito.eq("plugin.page.stashbot.jenkinsConfigurationPanel"), mapCaptor.capture());

        Map<String, Object> map = mapCaptor.getValue();

        Assert.assertEquals(JN, map.get("name"));
        Assert.assertEquals(JURL, map.get("url"));
        Assert.assertEquals(JU, map.get("username"));
        Assert.assertEquals(JP, map.get("password"));
    }

    @Test
    public void postTest() throws Exception {

        // test that things are actually updated
        Mockito.when(req.getParameter("name")).thenReturn(JN + "2");
        Mockito.when(req.getParameter("url")).thenReturn(JURL + "2");
        Mockito.when(req.getParameter("username")).thenReturn(JU + "2");
        Mockito.when(req.getParameter("password")).thenReturn(JP + "2");

        Mockito.when(cpm.getJenkinsServerConfiguration()).thenReturn(jsc2);

        jcs.doPost(req, res);

        // Verify it persists
        Mockito.verify(cpm).setJenkinsServerConfiguration(JURL + "2", JU + "2", JP + "2");

        // doGet() is then called, so this is the same as getTest()...
        Mockito.verify(res).setContentType("text/html;charset=UTF-8");
        Mockito.verify(webResourceManager).requireResourcesForContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class<Map<String, Object>>) (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        Mockito.verify(soyTemplateRenderer).render(Mockito.eq(writer),
            Mockito.eq("com.palantir.stash.stashbot-helper:stashbotConfigurationResources"),
            Mockito.eq("plugin.page.stashbot.jenkinsConfigurationPanel"), mapCaptor.capture());

        Map<String, Object> map = mapCaptor.getValue();

        // Except the details are now changed
        Assert.assertEquals(JN + "2", map.get("name"));
        Assert.assertEquals(JURL + "2", map.get("url"));
        Assert.assertEquals(JU + "2", map.get("username"));
        Assert.assertEquals(JP + "2", map.get("password"));
    }
}
