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
package com.palantir.stash.stashbot.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import com.atlassian.bitbucket.permission.PermissionValidationService;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.managers.PluginUserManager;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class RepoConfigurationStatusServlet extends HttpServlet {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final RepositoryService repositoryService;
    private final PullRequestService prs;
    private final SoyTemplateRenderer soyTemplateRenderer;
    private final PageBuilderService pageBuilderService;
    private final ConfigurationPersistenceService configurationPersistanceManager;
    private final JenkinsManager jenkinsManager;
    private final PluginUserManager pluginUserManager;
    private final PermissionValidationService permissionValidationService;
    private final Logger log;

    public RepoConfigurationStatusServlet(RepositoryService repositoryService, PullRequestService prs,
        SoyTemplateRenderer soyTemplateRenderer, PageBuilderService pageBuilderService,
        ConfigurationPersistenceService configurationPersistenceManager, JenkinsManager jenkinsManager,
        PluginUserManager pluginUserManager, PermissionValidationService permissionValidationService,
        PluginLoggerFactory lf) {
        this.repositoryService = repositoryService;
        this.prs = prs;
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
        RepositoryConfiguration rc;
        try {
            rc = configurationPersistanceManager.getRepositoryConfigurationForRepository(rep);
        } catch (SQLException e1) {
            throw new ServletException(e1);
        }
        res.setContentType("text/html;charset=UTF-8");
        res.getWriter().print(rc.getCiEnabled());
        res.getWriter().flush();
        res.getWriter().close();
        return;
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
