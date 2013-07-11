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
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbothelper.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbothelper.config.RepositoryConfiguration;
import com.palantir.stash.stashbothelper.managers.JenkinsManager;

public class RepoConfigurationServletTest {

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private WebResourceManager webResourceManager;
    @Mock
    private SoyTemplateRenderer soyTemplateRenderer;
    @Mock
    private JenkinsManager jenkinsManager;

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

    private RepoConfigurationServlet rcs;

    private static final String PBR = "publishBranchRegexString";
    private static final String VBR = "verifyBranchRegexString";
    private static final String PBC = "publishBranchCommandString";
    private static final String VBC = "verifyBranchCommandString";
    private static final String PREBC = "prebuildCommandString";

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        Mockito.when(res.getWriter()).thenReturn(writer);
        Mockito.when(req.getPathInfo()).thenReturn("/projectName/repoName");
        Mockito.when(repositoryService.findBySlug("projectName", "repoName")).thenReturn(mockRepo);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc);

        Mockito.when(rc.getCiEnabled()).thenReturn(true);
        Mockito.when(rc.getPublishBranchRegex()).thenReturn(PBR);
        Mockito.when(rc.getPublishBuildCommand()).thenReturn(PBC);
        Mockito.when(rc.getVerifyBranchRegex()).thenReturn(VBR);
        Mockito.when(rc.getVerifyBuildCommand()).thenReturn(VBC);
        Mockito.when(rc.getPrebuildCommand()).thenReturn(PREBC);
        Mockito.when(rc2.getPublishBranchRegex()).thenReturn(PBR + "2");
        Mockito.when(rc2.getPublishBuildCommand()).thenReturn(PBC + "2");
        Mockito.when(rc2.getVerifyBranchRegex()).thenReturn(VBR + "2");
        Mockito.when(rc2.getVerifyBuildCommand()).thenReturn(VBC + "2");
        Mockito.when(rc2.getPrebuildCommand()).thenReturn(PREBC + "2");

        rcs =
            new RepoConfigurationServlet(repositoryService, soyTemplateRenderer, webResourceManager, cpm,
                jenkinsManager);
    }

    @Test
    public void getTest() throws Exception {

        rcs.doGet(req, res);

        Mockito.verify(res).setContentType("text/html;charset=UTF-8");
        Mockito.verify(webResourceManager).requireResourcesForContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class<Map<String, Object>>) (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        Mockito.verify(soyTemplateRenderer).render(Mockito.eq(writer),
            Mockito.eq("com.palantir.stash.stashbot-helper:stashbotConfigurationResources"),
            Mockito.eq("plugin.page.stashbot.repositoryConfigurationPanel"), mapCaptor.capture());

        Map<String, Object> map = mapCaptor.getValue();

        Assert.assertEquals(true, map.get("ciEnabled"));
        Assert.assertEquals(PBR, map.get("publishBranchRegex"));
        Assert.assertEquals(PBC, map.get("publishBuildCommand"));
        Assert.assertEquals(VBR, map.get("verifyBranchRegex"));
        Assert.assertEquals(VBC, map.get("verifyBuildCommand"));
    }

    @Test
    public void postTest() throws Exception {

        // test that things are actually updated
        Mockito.when(req.getParameter("ciEnabled")).thenReturn(null);
        Mockito.when(req.getParameter("publishBranchRegex")).thenReturn(PBR + "2");
        Mockito.when(req.getParameter("publishBuildCommand")).thenReturn(PBC + "2");
        Mockito.when(req.getParameter("verifyBranchRegex")).thenReturn(VBR + "2");
        Mockito.when(req.getParameter("verifyBuildCommand")).thenReturn(VBC + "2");
        Mockito.when(req.getParameter("prebuildCommand")).thenReturn(PREBC + "2");

        Mockito.when(cpm.getRepositoryConfigurationForRepository(mockRepo)).thenReturn(rc2);

        rcs.doPost(req, res);

        // Verify it persists
        Mockito.verify(cpm).setRepositoryConfigurationForRepository(mockRepo, false, VBR + "2", VBC + "2", PBR + "2",
            PBC + "2", PREBC + "2");

        // doGet() is then called, so this is the same as getTest()...
        Mockito.verify(res).setContentType("text/html;charset=UTF-8");
        Mockito.verify(webResourceManager).requireResourcesForContext("plugin.page.stashbot");

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<Map<String, Object>> cls = (Class<Map<String, Object>>) (Class) Map.class;
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(cls);

        Mockito.verify(soyTemplateRenderer).render(Mockito.eq(writer),
            Mockito.eq("com.palantir.stash.stashbot-helper:stashbotConfigurationResources"),
            Mockito.eq("plugin.page.stashbot.repositoryConfigurationPanel"), mapCaptor.capture());

        Map<String, Object> map = mapCaptor.getValue();

        // Except the details are now changed
        Assert.assertEquals(false, map.get("ciEnabled"));
        Assert.assertEquals(PBR + "2", map.get("publishBranchRegex"));
        Assert.assertEquals(PBC + "2", map.get("publishBuildCommand"));
        Assert.assertEquals(VBR + "2", map.get("verifyBranchRegex"));
        Assert.assertEquals(VBC + "2", map.get("verifyBuildCommand"));
        Assert.assertEquals(PREBC + "2", map.get("prebuildCommand"));
    }
}
