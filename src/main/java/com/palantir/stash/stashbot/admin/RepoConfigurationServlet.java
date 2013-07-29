package com.palantir.stash.stashbot.admin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.JenkinsManager;

public class RepoConfigurationServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final RepositoryService repositoryService;
    private final SoyTemplateRenderer soyTemplateRenderer;
    private final WebResourceManager webResourceManager;
    private final ConfigurationPersistenceManager configurationPersistanceManager;
    private final JenkinsManager jenkinsManager;

    public RepoConfigurationServlet(RepositoryService repositoryService, SoyTemplateRenderer soyTemplateRenderer,
        WebResourceManager webResourceManager, ConfigurationPersistenceManager configurationPersistenceManager,
        JenkinsManager jenkinsManager) {
        this.repositoryService = repositoryService;
        this.soyTemplateRenderer = soyTemplateRenderer;
        this.webResourceManager = webResourceManager;
        this.configurationPersistanceManager = configurationPersistenceManager;
        this.jenkinsManager = jenkinsManager;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        Repository rep = getRepository(req);
        if (rep == null) {
            res.sendError(404);
            return;
        }

        RepositoryConfiguration rc;
        try {
            rc = configurationPersistanceManager.getRepositoryConfigurationForRepository(rep);
        } catch (SQLException e1) {
            throw new ServletException(e1);
        }

        System.out.println("Got existing setting for JSNL: " + rc.getJenkinsServerName() + "\n\n\n");
        res.setContentType("text/html;charset=UTF-8");

        try {
            List<Map<String, String>> jenkinsServersData = new ArrayList<Map<String, String>>();
            for (JenkinsServerConfiguration jsc : configurationPersistanceManager.getAllJenkinsServerConfigurations()) {
                HashMap<String, String> m = new HashMap<String, String>();
                m.put("text", jsc.getName());
                m.put("value", jsc.getName());
                if (rc.getJenkinsServerName().equals(jsc.getName())) {
                    System.out.println("name " + jsc.getName() + " is selected");
                    m.put("selected", "true");
                }
                jenkinsServersData.add(m);
            }
            webResourceManager.requireResourcesForContext("plugin.page.stashbot");
            soyTemplateRenderer.render(res.getWriter(),
                "com.palantir.stash.stashbot:stashbotConfigurationResources",
                "plugin.page.stashbot.repositoryConfigurationPanel",
                ImmutableMap.<String, Object> builder()
                    .put("repository", rep)
                    .put("ciEnabled", rc.getCiEnabled())
                    .put("publishBranchRegex", rc.getPublishBranchRegex())
                    .put("publishBuildCommand", rc.getPublishBuildCommand())
                    .put("verifyBranchRegex", rc.getVerifyBranchRegex())
                    .put("verifyBuildCommand", rc.getVerifyBuildCommand())
                    .put("prebuildCommand", rc.getPrebuildCommand())
                    .put("jenkinsServerName", rc.getJenkinsServerName())
                    .put("jenkinsServersData", jenkinsServersData)
                    .build()
                );
        } catch (SoyException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new ServletException(e);
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        Repository rep = getRepository(req);
        if (rep == null) {
            res.sendError(404);
            return;
        }

        Boolean ciEnabled = (req.getParameter("ciEnabled") == null) ? false : true;
        String publishBranchRegex = req.getParameter("publishBranchRegex");
        String publishBuildCommand = req.getParameter("publishBuildCommand");
        String verifyBranchRegex = req.getParameter("verifyBranchRegex");
        String verifyBuildCommand = req.getParameter("verifyBuildCommand");
        String prebuildCommand = req.getParameter("prebuildCommand");
        String jenkinsServerName = req.getParameter("jenkinsServerName");

        System.out.println("JSN: " + jenkinsServerName + "\n\n\n");
        try {
            configurationPersistanceManager.setRepositoryConfigurationForRepository(rep, ciEnabled, verifyBranchRegex,
                verifyBuildCommand, publishBranchRegex, publishBuildCommand, prebuildCommand, jenkinsServerName);
            // ensure hook is enabled, jobs exist
            jenkinsManager.updateRepo(rep);

        } catch (SQLException e) {
            res.sendError(500, e.getMessage());
        }
        doGet(req, res);
    }

    private Repository getRepository(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            return null;
        }
        pathInfo = pathInfo.startsWith("/") ? pathInfo.substring(0) : pathInfo;
        String[] pathParts = pathInfo.split("/");
        if (pathParts.length != 3) {
            return null;
        }
        return repositoryService.findBySlug(pathParts[1], pathParts[2]);
    }

}
