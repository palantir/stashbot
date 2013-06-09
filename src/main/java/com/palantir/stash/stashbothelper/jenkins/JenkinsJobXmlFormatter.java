package com.palantir.stash.stashbothelper.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Scanner;

import org.apache.commons.lang.StringEscapeUtils;

public class JenkinsJobXmlFormatter {

    private static final String JOB_TEMPLATE_NAME = "job-template.xml";

    private final String jobTemplate;

    public JenkinsJobXmlFormatter() throws IOException {
        URL xml_url = this.getClass().getClassLoader().getResource(JOB_TEMPLATE_NAME);
        if (xml_url == null) {
            throw new IOException("Unable to load job template '" + JOB_TEMPLATE_NAME + "'");
        }
        Scanner s = new Scanner(xml_url.openStream());
        StringBuffer sb = new StringBuffer();
        try {
            s.useDelimiter("\\z");
            while (s.hasNext()) {
                sb.append(s.next());
            }
            jobTemplate = sb.toString();
        } finally {
            s.close();
        }
    }

    /**
     * This method uses the job-template.xml file, but replaces certain sigils with the dynamic XML content.
     * 
     * Sigils include:
     * 
     * $$PARAMETER_DEFINITIONS$$ - the definitions for a parameterized build (or empty for no params)<br/>
     * $$REPOSITORY_URL$$ - the URL of the repository<br/>
     * $$PREBUILD_COMMAND$$ - the command to run before the build<br/>
     * $$BUILD_COMMAND$$ - the actual command to run for the build<br/>
     * $$STARTED_COMMAND$$ - the command to run as soon as the build starts<br/>
     * $$SUCCESS_COMMAND$$ - the command to run if the build is successful<br/>
     * $$FAILURE_COMMAND$$ - the command to run if the build fails<br/>
     * 
     * @return
     */
    public String getJobXml(String repositoryUrl, String prebuildCommand, String buildCommand, String startedCommand,
        String successCommand, String failureCommand, Collection<JenkinsBuildParam> params) {
        String newXmlText = jobTemplate;

        newXmlText = newXmlText.replace("$$REPOSITORY_URL$$", escapeXml(repositoryUrl));
        newXmlText = newXmlText.replace("$$PREBUILD_COMMAND$$", escapeXml(prebuildCommand));
        newXmlText = newXmlText.replace("$$BUILD_COMMAND$$", escapeXml(buildCommand));
        newXmlText = newXmlText.replace("$$STARTED_COMMAND$$", escapeXml(startedCommand));
        newXmlText = newXmlText.replace("$$SUCCESS_COMMAND$$", escapeXml(successCommand));
        newXmlText = newXmlText.replace("$$FAILURE_COMMAND$$", escapeXml(failureCommand));

        StringBuffer paramXml = new StringBuffer();

        if (!params.isEmpty()) {
            paramXml.append("  <hudson.model.ParametersDefinitionProperty>\n");
            paramXml.append("    <parameterDefinitions>\n");
        }
        for (JenkinsBuildParam jbp : params) {
            paramXml.append(jbp.toString());
        }
        if (!params.isEmpty()) {
            paramXml.append("    </parameterDefinitions>\n");
            paramXml.append("  </hudson.model.ParametersDefinitionProperty>\n");
        }

        newXmlText = newXmlText.replace("$$PARAMETER_DEFINITIONS$$", paramXml.toString());
        return newXmlText;
    }

    /**
     * Represents a parameter for a parameterized build. All builds we create in jenkins are at least parameterized on
     * git hash, since we want to pass in the hash to check out or merge against.
     * 
     * @author cmyers
     */
    public static class JenkinsBuildParam {

        private final String name;
        private final JenkinsBuildParamType type;
        private final String defaultValue;
        private final String description;

        public JenkinsBuildParam(String name, JenkinsBuildParamType type, String description, String defaultValue) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("        <hudson.model." + type.toString() + ">\n");
            sb.append("          <name>" + escapeXml(name) + "</name>\n");
            sb.append("          <description>" + escapeXml(description) + "</description>\n");
            sb.append("          <defaultValue>" + escapeXml(defaultValue) + "</defaultValue>\n");
            sb.append("        </hudson.model." + type.toString() + ">\n");
            return sb.toString();
        }
    }

    public static String escapeXml(String text) {
        return StringEscapeUtils.escapeXml(text);
    }

    /**
     * Represents the types of jobs we create in jenkins
     * 
     * @author cmyers
     */
    public static enum JenkinsJobType {
        NOOP_BUILD,
        VERIFY_BUILD,
        RELEASE_BUILD;
    }

    /**
     * XML specific parameter types
     * 
     * @author cmyers
     */
    public static enum JenkinsBuildParamType {
        StringParameterDefinition,
        BooleanParameterDefinition;
        // TODO: more?
    }
}