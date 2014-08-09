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

import org.slf4j.Logger;

import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.stash.exception.AuthorisationException;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.managers.PluginUserManager;

public class RepoConfigurationServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final RepositoryService repositoryService;
    private final SoyTemplateRenderer soyTemplateRenderer;
    private final PageBuilderService pageBuilderService;
    private final ConfigurationPersistenceManager configurationPersistanceManager;
    private final JenkinsManager jenkinsManager;
    private final PluginUserManager pluginUserManager;
    private final PermissionValidationService permissionValidationService;
    private final Logger log;

    public RepoConfigurationServlet(RepositoryService repositoryService, SoyTemplateRenderer soyTemplateRenderer,
        PageBuilderService pageBuilderService, ConfigurationPersistenceManager configurationPersistenceManager,
        JenkinsManager jenkinsManager, PluginUserManager pluginUserManager,
        PermissionValidationService permissionValidationService, PluginLoggerFactory lf) {
        this.repositoryService = repositoryService;
        this.soyTemplateRenderer = soyTemplateRenderer;
        this.pageBuilderService = pageBuilderService;
        this.configurationPersistanceManager = configurationPersistenceManager;
        this.jenkinsManager = jenkinsManager;
        this.permissionValidationService = permissionValidationService;
        this.pluginUserManager = pluginUserManager;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        Repository rep = getRepository(req);
        if (rep == null) {
            res.sendError(404);
            return;
        }

        try {
            permissionValidationService.validateForRepository(rep, Permission.REPO_ADMIN);
        } catch (AuthorisationException notRepoAdmin) {
            log.warn("User {} tried to access the stashbot admin page for {}",
                req.getRemoteUser(), rep.getSlug());
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "You do not have permission to access this page.");
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
            List<Map<String, String>> jenkinsServersData = new ArrayList<Map<String, String>>();
            for (JenkinsServerConfiguration jsc : configurationPersistanceManager.getAllJenkinsServerConfigurations()) {
                HashMap<String, String> m = new HashMap<String, String>();
                m.put("text", jsc.getName());
                m.put("value", jsc.getName());
                if (rc.getJenkinsServerName().equals(jsc.getName())) {
                    m.put("selected", "true");
                }
                jenkinsServersData.add(m);
            }
            pageBuilderService.assembler().resources().requireContext("plugin.page.stashbot");
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
                    .put("maxVerifyChain", rc.getMaxVerifyChain().toString())
                    .put("rebuildOnUpdate", rc.getRebuildOnTargetUpdate())
                    .put("isVerifyPinned", rc.getVerifyPinned())
                    .put("verifyLabel", rc.getVerifyLabel())
                    .put("isPublishPinned", rc.getPublishPinned())
                    .put("publishLabel", rc.getPublishLabel())
                    .put("isJunit", rc.getJunitEnabled())
                    .put("junitPath", rc.getJunitPath())
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
            log.error("Failed to get repo for request" + req.toString());
            res.sendError(404);
            return;
        }

        try {
            permissionValidationService.validateForRepository(rep, Permission.REPO_ADMIN);
        } catch (AuthorisationException notRepoAdmin) {
            // Skip form processing
            doGet(req, res);
            return;
        }

        try {
            String jenkinsServerName = req.getParameter("jenkinsServerName");

            configurationPersistanceManager.setRepositoryConfigurationForRepositoryFromRequest(rep, req);

            // add permission to the requisite user
            JenkinsServerConfiguration jsc =
                configurationPersistanceManager.getJenkinsServerConfiguration(jenkinsServerName);
            pluginUserManager.addUserToRepoForReading(jsc.getStashUsername(), rep);

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
        return repositoryService.getBySlug(pathParts[1], pathParts[2]);
    }

}
