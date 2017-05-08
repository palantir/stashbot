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
package com.palantir.stash.stashbot.jobtemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryCloneLinksRequest;
import com.atlassian.stash.repository.RepositoryService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.managers.VelocityManager;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfigurationImpl;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsJobXmlFormatter {

    // Tacking this onto the end of the build command makes it print out
    // "BUILD SUCCESS0" on success and
    // "BUILD FAILURE1" on failure.
    private static final String BUILD_COMMAND_POSTFIX =
        "|| (echo \"BUILD FAILURE1 with status $?\" ; /bin/false) && echo \"BUILD SUCCESS0\"";

    private static final String PREBUILD_COMMAND_POSTFIX =
        "|| (echo \"PREBUILD FAILURE1 with status $?\" ; /bin/false) && echo \"PREBUILD SUCCESS\"";

    private static final String GLOBAL_PREBUILD_COMMAND_PREFIX = "{ ";

    private static final String GLOBAL_PREBUILD_COMMAND_POSTFIX =
        "\n } || (echo \"GLOBALPREBUILD FAILURE1 with status $?\" ; /bin/false) && echo \"GLOBALPREBUILD SUCCESS\"";

    private final VelocityManager velocityManager;
    private final ConfigurationPersistenceService cpm;
    private final StashbotUrlBuilder sub;
    private final NavBuilder navBuilder;
    private final RepositoryService rs;

    public JenkinsJobXmlFormatter(VelocityManager velocityManager,
        ConfigurationPersistenceService cpm, StashbotUrlBuilder sub,
        NavBuilder navBuilder, RepositoryService rs) throws IOException {
        this.velocityManager = velocityManager;
        this.cpm = cpm;
        this.sub = sub;
        this.navBuilder = navBuilder;
        this.rs = rs;
    }

    private String curlCommandBuilder(Repository repo, JobTemplate jobTemplate,
        RepositoryConfiguration rc, String repositoryUrl, String status)
        throws SQLException {
        final JenkinsServerConfiguration jsc = cpm
            .getJenkinsServerConfiguration(rc.getJenkinsServerName());
        StringBuffer sb = new StringBuffer();
        sb.append("/usr/bin/curl -s -i ");
        sb.append(sub.buildReportingUrl(repo, jobTemplate.getJobType(), jsc, status));
        return sb.toString();
    }

    public String generateJobXml(JobTemplate jobTemplate, Repository repo)
        throws SQLException {

        final VelocityContext vc = velocityManager.getVelocityContext();
        final RepositoryConfiguration rc = cpm
            .getRepositoryConfigurationForRepository(repo);
        final JenkinsServerConfiguration jsc = cpm
            .getJenkinsServerConfiguration(rc.getJenkinsServerName());


        RepositoryCloneLinksRequest rclr = null;
        String repositoryUrl = null;
        String cleanRepositoryUrl = null;

        // Handle the various Authentication modes
        switch (jsc.getAuthenticationMode()) {
        case USERNAME_AND_PASSWORD:
            // manually insert the username and pw we are configured to use
            rclr = new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("http").user(null).build();
            repositoryUrl = rs.getCloneLinks(rclr).iterator().next().getHref();
            cleanRepositoryUrl = repositoryUrl;
            repositoryUrl = repositoryUrl.replace("://",
                "://" + jsc.getStashUsername() + ":" + jsc.getStashPassword()
                    + "@");
            break;
        case CREDENTIAL_MANUALLY_CONFIGURED:
            rclr = new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("ssh").build();
            // FIXME: Carl replaced the following line:
            repositoryUrl = rs.getCloneLinks(rclr).iterator().next().getHref();
            /* FIXME with something like the following:

            Set<NamedLink> links = rs.getCloneLinks(sshrclr);
            if (links.size() != 1) {
                throw new RuntimeException("Unable to get a unique ssh clone URL for repo " + repo.getName());
            }
            repositoryUrl = links.iterator().next().getHref();

             * But that was for CREDENTIAL_AUTOMATIC_SSH_KEY, and I don't know
             * how similar/different it is from CREDENTIAL_MANUALLY_CONFIGURED.
             */

            cleanRepositoryUrl = repositoryUrl;
            vc.put("credentialUUID", JenkinsServerConfigurationImpl.convertCredUUID(jsc.getStashPassword(), repo));
            break;
        }
        vc.put("repositoryUrl", repositoryUrl);
        vc.put("cleanRepositoryUrl", cleanRepositoryUrl);

        vc.put("prebuildCommand", prebuildCommand(rc.getPrebuildCommand()));

        vc.put("globalPrebuildCommand", globalPrebuildCommand(jsc.getGlobalPrebuildCommand()));

        // Put build command depending on build type
        // TODO: figure out build command some other way?
        switch (jobTemplate.getJobType()) {
        case VERIFY_COMMIT:
            vc.put("buildCommand", buildCommand(rc.getVerifyBuildCommand()));
            break;
        case VERIFY_PR:
            vc.put("buildCommand", buildCommand(rc.getVerifyBuildCommand()));
            break;
        case PUBLISH:
            vc.put("buildCommand", buildCommand(rc.getPublishBuildCommand()));
            break;
        case NOOP:
            vc.put("buildCommand", buildCommand("/bin/true"));
            break;
        }

        // Add email notification stuff for all build types
        vc.put("isEmailNotificationsEnabled", rc.getEmailNotificationsEnabled());
        vc.put("emailRecipients", rc.getEmailRecipients());
        vc.put("isEmailForEveryUnstableBuild", rc.getEmailForEveryUnstableBuild());
        vc.put("isEmailSendToIndividuals", rc.getEmailSendToIndividuals());
        vc.put("isEmailPerModuleEmail", rc.getEmailPerModuleEmail());

        vc.put("startedCommand",
            curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
                "inprogress"));
        vc.put("successCommand",
            curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
                "successful"));
        vc.put("failedCommand",
            curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
                "failed"));
        vc.put("repositoryLink", navBuilder.repo(repo).browse().buildAbsolute());
        vc.put("repositoryName",
            repo.getProject().getName() + " " + repo.getName());

        // Parameters are type-dependent for now
        ImmutableList.Builder<Map<String, String>> paramBuilder = new ImmutableList.Builder<Map<String, String>>();
        switch (jobTemplate.getJobType()) {
        case VERIFY_COMMIT:
            // repoId
            paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "stash repository Id", "defaultValue",
                "unknown"));
            // buildHead
            paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "the change to build", "defaultValue",
                "head"));
            break;
        case VERIFY_PR:
            // repoId
            paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "stash repository Id", "defaultValue",
                "unknown"));
            // buildHead
            paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "the change to build", "defaultValue",
                "head"));
            // pullRequestId
            paramBuilder.add(ImmutableMap.of("name", "pullRequestId",
                "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "the pull request Id", "defaultValue", ""));
            break;
        case PUBLISH:
            // repoId
            paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "stash repository Id", "defaultValue",
                "unknown"));
            // buildHead
            paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
                JenkinsBuildParamType.StringParameterDefinition.toString(),
                "description", "the change to build", "defaultValue",
                "head"));
            break;
        case NOOP:
            // no params
            break;
        }
        vc.put("paramaterList", paramBuilder.build());

        // Junit settings
        vc.put("isJunit", rc.getJunitEnabled());
        vc.put("junitPath", rc.getJunitPath());

        // Artifact settings
        vc.put("artifactsEnabled", rc.getArtifactsEnabled());
        vc.put("artifactsPath", rc.getArtifactsPath());

        // Timeout setting
        Integer buildTimeout = rc.getBuildTimeout();
        if (buildTimeout == -1) {
            buildTimeout = jsc.getDefaultTimeout();
        }
        vc.put("buildTimeout", buildTimeout);


        // insert pinned data and expiry info
        switch (jobTemplate.getJobType()) {
        case VERIFY_COMMIT:
        case VERIFY_PR:
            vc.put("isPinned", rc.getVerifyPinned());
            vc.put("label", rc.getVerifyLabel());
            vc.put("verifyBuildExpiryDays", rc.getVerifyBuildExpiryDays());
            vc.put("verifyBuildExpiryNumber", rc.getVerifyBuildExpiryNumber());
            break;
        case PUBLISH:
            vc.put("isPinned", rc.getPublishPinned());
            vc.put("label", rc.getPublishLabel());
            vc.put("publishBuildExpiryDays", rc.getPublishBuildExpiryDays());
            vc.put("publishBuildExpiryNumber", rc.getPublishBuildExpiryNumber());
            break;
        case NOOP:
            vc.put("isPinned", false);
            break;
        }
        StringWriter xml = new StringWriter();

        VelocityEngine ve = velocityManager.getVelocityEngine();
        Template template = ve.getTemplate(jobTemplate.getTemplateFile());

        template.merge(vc, xml);
        return xml.toString();
    }

    /**
     * XML specific parameter types
     *
     * @author cmyers
     */
    public static enum JenkinsBuildParamType {
        StringParameterDefinition, BooleanParameterDefinition;
        // TODO: more?
    }

    /**
     * Appends the shell magics to the build command to make it succeed/fail
     * properly.
     *
     * TODO: move this into the template?
     *
     * @param command
     * @return
     */
    private String buildCommand(String command) {
        return command + " " + BUILD_COMMAND_POSTFIX;
    }

    private String prebuildCommand(String command) {
        return command + " " + PREBUILD_COMMAND_POSTFIX;
    }

    private String globalPrebuildCommand(String command) {
        return GLOBAL_PREBUILD_COMMAND_PREFIX + " " + command + " " + GLOBAL_PREBUILD_COMMAND_POSTFIX;
    }

}
