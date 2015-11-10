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
import java.net.URI;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import com.atlassian.bitbucket.AuthorisationException;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionValidationService;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.managers.JenkinsManager;
import com.palantir.stash.stashbot.managers.PluginUserManager;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;

public class JenkinsConfigurationServlet extends HttpServlet {

    private final String PATH_PREFIX = "/stashbot/jenkins-admin";
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final SoyTemplateRenderer soyTemplateRenderer;
    private final PageBuilderService pageBuilderService;
    private final ConfigurationPersistenceService configurationPersistanceManager;
    private final PluginUserManager pluginUserManager;
    private final JenkinsManager jenkinsManager;
    private final LoginUriProvider lup;
    private final PermissionValidationService permissionValidationService;
    private final Logger log;

    public JenkinsConfigurationServlet(SoyTemplateRenderer soyTemplateRenderer,
        PageBuilderService pageBuilderService,
        ConfigurationPersistenceService configurationPersistenceManager, PluginUserManager pluginUserManager,
        JenkinsManager jenkinsManager, LoginUriProvider lup, PluginLoggerFactory lf,
        PermissionValidationService permissionValidationService) {
        this.soyTemplateRenderer = soyTemplateRenderer;
        this.pageBuilderService = pageBuilderService;
        this.configurationPersistanceManager = configurationPersistenceManager;
        this.pluginUserManager = pluginUserManager;
        this.jenkinsManager = jenkinsManager;
        this.permissionValidationService = permissionValidationService;
        this.log = lf.getLoggerForThis(this);
        this.lup = lup;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        // Authenticate user
        try {
            permissionValidationService.validateAuthenticated();
        } catch (AuthorisationException notLoggedInException) {
            log.debug("User not logged in, redirecting to login page");
            // not logged in, redirect
            res.sendRedirect(lup.getLoginUri(getUri(req)).toASCIIString());
            return;
        }
        log.debug("User {} logged in", req.getRemoteUser());
        try {
            permissionValidationService.validateForGlobal(Permission.SYS_ADMIN);
        } catch (AuthorisationException notAdminException) {
            log.warn("User {} is not a system administrator", req.getRemoteUser());
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "You do not have permission to access this page.");
            return;
        }

        // Handle deletes
        String pathInfo = req.getPathInfo();
        String relUrl = req.getRequestURL().toString();
        relUrl = relUrl
            .replaceAll("/+$", "")
            .replaceAll("/delete/?.*$", "")
            .replaceAll("/reload-all/?.*$", "")
            .replaceAll("/create-new/?.*$", "")
            .replaceAll("\\?notice=.*$", "")
            .replaceAll("\\?error=.*$", "");

        String[] parts = pathInfo.replaceFirst(PATH_PREFIX, "").split("/");

        if (parts.length >= 2) {
            if (parts[1].equals("delete")) {
                log.info("Deleting configuration " + parts[2]);
                configurationPersistanceManager.deleteJenkinsServerConfiguration(parts[2]);
                res.sendRedirect(relUrl);
                return;
            }
            if (parts[1].equals("reload-all")) {
                jenkinsManager.updateAllJobs();
                res.sendRedirect(relUrl);
            }
            if (parts[1].equals("create-new")) {
                jenkinsManager.createMissingJobs();
                res.sendRedirect(relUrl);
            }
        }

        String error = req.getParameter("error");
        if (error == null) {
            error = new String();
        }
        String notice = req.getParameter("notice");
        if (notice == null) {
            notice = new String();
        }

        res.setContentType("text/html;charset=UTF-8");
        try {
            // Build select data for authentication modes
            // Structure is:  { "jenkinsServerName" => [ { "text" => "auth description", "value" => "auth code" }, { ... } ], ... }
            ImmutableMap.Builder<String, ImmutableList<ImmutableMap<String, String>>> authDataBuilder =
                ImmutableMap.builder();

            ImmutableMap.Builder<String, String> authDataSelectedBuilder = ImmutableMap.builder();

            for (JenkinsServerConfiguration jsc : configurationPersistanceManager.getAllJenkinsServerConfigurations()) {
                AuthenticationMode am = jsc.getAuthenticationMode();
                ImmutableList<ImmutableMap<String, String>> selectList = AuthenticationMode.getSelectList(am);

                authDataBuilder.put(jsc.getName(), selectList);

                // For convenience, store the value of the selected field in a separate map
                authDataSelectedBuilder.put(jsc.getName(),
                    jsc.getAuthenticationMode().getSelectListEntry(false).get("value"));

            }
            pageBuilderService.assembler().resources().requireContext("plugin.page.stashbot");
            ImmutableCollection<JenkinsServerConfiguration> jenkinsConfigs =
                configurationPersistanceManager.getAllJenkinsServerConfigurations();
            soyTemplateRenderer.render(res.getWriter(),
                "com.palantir.stash.stashbot:stashbotConfigurationResources",
                "plugin.page.stashbot.jenkinsConfigurationPanel",
                ImmutableMap.<String, Object> builder()
                    .put("relUrl", relUrl)
                    .put("jenkinsConfigs", jenkinsConfigs)
                    .put("error", error)
                    .put("notice", notice)
                    .put("authenticationModeData", authDataBuilder.build())
                    .put("authenticationModeDataSelected", authDataSelectedBuilder.build())
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
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            permissionValidationService.validateForGlobal(Permission.SYS_ADMIN);
        } catch (AuthorisationException e) {
            // Skip form processing
            doGet(req, res);
            return;
        }

        String name = req.getParameter("name");

        try {
            configurationPersistanceManager.setJenkinsServerConfigurationFromRequest(req);
            pluginUserManager.createStashbotUser(configurationPersistanceManager.getJenkinsServerConfiguration(name));
        } catch (SQLException e) {
            res.sendRedirect(req.getRequestURL().toString() + "?error=" + e.getMessage());
        } catch (Exception e) {
            res.sendRedirect(req.getRequestURL().toString() + "?error=" + e.getMessage());
        }
        doGet(req, res);
    }

    private URI getUri(HttpServletRequest req) {
        StringBuffer builder = req.getRequestURL();
        if (req.getQueryString() != null) {
            builder.append("?");
            builder.append(req.getQueryString());
        }
        return URI.create(builder.toString());
    }
}
