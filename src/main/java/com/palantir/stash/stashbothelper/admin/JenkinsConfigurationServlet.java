package com.palantir.stash.stashbothelper.admin;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbothelper.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbothelper.config.JenkinsServerConfiguration;

public class JenkinsConfigurationServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final SoyTemplateRenderer soyTemplateRenderer;
    private final WebResourceManager webResourceManager;
    private final ConfigurationPersistenceManager configurationPersistanceManager;

    public JenkinsConfigurationServlet(SoyTemplateRenderer soyTemplateRenderer, WebResourceManager webResourceManager,
        ConfigurationPersistenceManager configurationPersistenceManager) {
        this.soyTemplateRenderer = soyTemplateRenderer;
        this.webResourceManager = webResourceManager;
        this.configurationPersistanceManager = configurationPersistenceManager;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        JenkinsServerConfiguration jsc;
        try {
            jsc = configurationPersistanceManager.getJenkinsServerConfiguration();
        } catch (SQLException e1) {
            throw new ServletException(e1);
        }

        res.setContentType("text/html;charset=UTF-8");

        try {
            webResourceManager.requireResourcesForContext("plugin.page.stashbot");
            soyTemplateRenderer.render(res.getWriter(),
                "com.palantir.stash.stashbot-helper:stashbotConfigurationResources",
                "plugin.page.stashbot.jenkinsConfigurationPanel",
                ImmutableMap.<String, Object> builder()
                    .put("name", jsc.getName())
                    .put("url", jsc.getUrl())
                    .put("username", jsc.getUsername())
                    .put("password", jsc.getPassword())
                    .build()
                );
        } catch (SoyException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new ServletException(e);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        String url = req.getParameter("url");
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        try {
            configurationPersistanceManager.setJenkinsServerConfiguration(url, username, password);
        } catch (SQLException e) {
            res.sendError(500, e.getMessage());
        }
        doGet(req, res);
    }
}
