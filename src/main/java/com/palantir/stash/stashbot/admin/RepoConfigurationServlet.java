package com.palantir.stash.stashbot.admin;

import java.io.IOException;
import java.sql.SQLException;

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

        res.setContentType("text/html;charset=UTF-8");

        try {
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
                    .put("allJenkinsServerNames", configurationPersistanceManager.getAllJenkinsServerNames())
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
