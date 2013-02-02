package com.palantir.stash.stashbothelper;

import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.repository.ScmType;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.google.common.collect.ImmutableMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

	public CiInfoServlet(RepositoryService repositoryService, ApplicationPropertiesService applicationPropertiesService,
							PermissionValidationService permissionValidationService, SoyTemplateRenderer soyTemplateRenderer,
							WebResourceManager webResourceManager) {
		this.repositoryService = repositoryService;
		this.applicationPropertiesService = applicationPropertiesService;
		this.permissionValidationService = permissionValidationService;
		this.soyTemplateRenderer = soyTemplateRenderer;
		this.webResourceManager = webResourceManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
	}

}
