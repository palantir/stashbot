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
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CiConfigUtility {
	private final RepositoryService repositoryService;
	private final GitCommandBuilderFactory gitCommandBuilderFactory;

	private static final Logger log = LoggerFactory.getLogger(CiConfigUtility.class);

	public final String CI_ENABLED_KEY = "stashbot.ci-enabled";
	public final String CI_PUBLISH_BRANCHES_KEY = "stashbot.publish-branches";
	public final String CI_ANT_BUILDFILE_KEY = "stashbot.ant-buildfile";

	public CiConfigUtility(RepositoryService repositoryService, GitCommandBuilderFactory gitCommandBuilderFactory) {
		this.repositoryService = repositoryService;
		this.gitCommandBuilderFactory = gitCommandBuilderFactory;
	}

	public boolean getCiEnabled(Repository repo) {
		String enabled = getConfig(repo, CI_ENABLED_KEY);
		log.error("Enabled string value is " + enabled);
		return (enabled != null && enabled.equalsIgnoreCase("true")) ? true : false;
	}

	public void setCiEnabled(Repository repo, boolean enable) {
		setConfig(repo, CI_ENABLED_KEY, enable ? "true" : "false");
	}

	public String getPublishBranches(Repository repo) {
		return getConfig(repo, CI_PUBLISH_BRANCHES_KEY);
	}

	public void setPublishBranches(Repository repo, String branches) {
		setConfig(repo, CI_PUBLISH_BRANCHES_KEY, branches);
	}

	public String getAntBuildfile(Repository repo) {
		return getConfig(repo, CI_ANT_BUILDFILE_KEY);
	}

	public void setAntBuildfile(Repository repo, String file) {
		setConfig(repo, CI_ANT_BUILDFILE_KEY, file);
	}

	private void setConfig(Repository repo, String key, String value) {
		try {
			gitCommandBuilderFactory.builder(repo).config().unsetAll(key).build().call();
		} catch (Exception e) {}
		if (value != null && value.length() != 0)
			gitCommandBuilderFactory.builder(repo).config().set(key, value).build().call();
	}

	private String getConfig(Repository repo, String key) {
		try {
			return gitCommandBuilderFactory.builder(repo).config().get(key).build().call();
		} catch (Exception e) {
			return null;
		}
	}
}
