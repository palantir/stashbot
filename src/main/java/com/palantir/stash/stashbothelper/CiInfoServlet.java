package com.palantir.stash.stashbothelper;

import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.repository.ScmType;
import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CiInfoServlet extends HttpServlet {
	private final RepositoryService repositoryService;
	private final ApplicationPropertiesService applicationPropertiesService;
	private final PermissionValidationService permissionValidationService;
	private final SoyTemplateRenderer soyTemplateRenderer;
	private final WebResourceManager webResourceManager;
	private final GitCommandBuilderFactory gitCommandBuilderFactory;

	private static final Logger log = LoggerFactory.getLogger(CiInfoServlet.class);

	public final String CI_ROOT_KEY = "stashbot";
	public final String CI_ENABLED_KEY = "stashbot.ci-enabled";
	public final String CI_PUBLISH_BRANCHES_KEY = "stashbot.publish-branches";

	public final String ENABLE_CI_DOM_ID = "enable-ci-radio";
	public final String CI_BRANCHES_DOM_ID = "ci-branch-field";

	public CiInfoServlet(RepositoryService repositoryService, ApplicationPropertiesService applicationPropertiesService,
							PermissionValidationService permissionValidationService, SoyTemplateRenderer soyTemplateRenderer,
							WebResourceManager webResourceManager, GitCommandBuilderFactory gitCommandBuilderFactory) {
		this.repositoryService = repositoryService;
		this.applicationPropertiesService = applicationPropertiesService;
		this.permissionValidationService = permissionValidationService;
		this.soyTemplateRenderer = soyTemplateRenderer;
		this.webResourceManager = webResourceManager;
		this.gitCommandBuilderFactory = gitCommandBuilderFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		//permissionValidationService.validateFor
		Repository rep = getRepository(req);
		if (rep == null) {
			res.sendError(404);
			return;
		}

		CiConfigUtility configUtil = new CiConfigUtility(repositoryService, gitCommandBuilderFactory);

		boolean enabled = configUtil.getCiEnabled(rep);
		String branches = configUtil.getPublishBranches(rep);
		if (branches == null) {
			branches = "";
		}
		res.setContentType("text/html;charset=UTF-8");
		//res.getWriter().write(CI_ENABLED_KEY + " is " + enabled + " branches " + branches);

		try {
			webResourceManager.requireResourcesForContext("plugin.page.ciInfo");
			soyTemplateRenderer.render(res.getWriter(),
					"com.palantir.stash.stashbot-helper:ciInfoResources",
					"plugin.page.ciInfo.ciSettingsPanel",
					ImmutableMap.<String,Object>builder()
						.put("repository", rep)
						.put("ciEnabled", enabled)
						.put("publishBranches", branches)
						.put("enableCiRadioId", ENABLE_CI_DOM_ID)
						.put("ciBranchesId", CI_BRANCHES_DOM_ID)
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
		res.setContentType("text/html;charset=UTF-8");
		String ciEnabled = req.getParameter(ENABLE_CI_DOM_ID);
		String branches = req.getParameter(CI_BRANCHES_DOM_ID);

		Repository rep = getRepository(req);
		if (rep == null) {
			res.sendError(404);
			return;
		}


		CiConfigUtility configUtil = new CiConfigUtility(repositoryService, gitCommandBuilderFactory);
		if (ciEnabled == null)
			configUtil.setCiEnabled(rep, false);
		else
			configUtil.setCiEnabled(rep, true);

		if (branches == null)
			branches = "";
		configUtil.setPublishBranches(rep, branches);

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
