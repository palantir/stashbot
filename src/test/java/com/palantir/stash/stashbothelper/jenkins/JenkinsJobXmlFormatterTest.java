package com.palantir.stash.stashbothelper.jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.palantir.stash.stashbothelper.jenkins.JenkinsJobXmlFormatter.JenkinsBuildParam;
import com.palantir.stash.stashbothelper.jenkins.JenkinsJobXmlFormatter.JenkinsBuildParamType;

public class JenkinsJobXmlFormatterTest {

    private JenkinsJobXmlFormatter jjxf;

    private final String repositoryUrl = "some repo url";
    private final String prebuildCommand = "some pre build command";
    private final String buildCommand = "some build command";
    private final String startedCommand = "command to say a build started";
    private final String successCommand = "command to say a build is successful";
    private final String failureCommand = "command to say a build failed";
    private final List<JenkinsBuildParam> params = new ArrayList<JenkinsBuildParam>();

    @Before
    public void setUp() throws IOException {
        params.add(new JenkinsBuildParam("parameterOne", JenkinsBuildParamType.StringParameterDefinition,
            "param description", ""));
        params.add(new JenkinsBuildParam("parameterTwo", JenkinsBuildParamType.StringParameterDefinition,
            "param description", "SOME DEFAULT"));
        jjxf = new JenkinsJobXmlFormatter();
    }

    @Test
    public void testJJXF() {
        String jobXml =
            jjxf.getJobXml(repositoryUrl, prebuildCommand, buildCommand, startedCommand, successCommand,
                failureCommand, params);
        Assert.assertTrue(jobXml.contains("parameterOne"));
        Assert.assertTrue(jobXml.contains("parameterTwo"));
        Assert.assertTrue(jobXml.contains("SOME DEFAULT"));
        Assert.assertTrue(jobXml.contains(repositoryUrl));
        Assert.assertTrue(jobXml.contains(prebuildCommand));
        Assert.assertTrue(jobXml.contains(buildCommand));
        Assert.assertTrue(jobXml.contains(startedCommand));
        Assert.assertTrue(jobXml.contains(successCommand));
        Assert.assertTrue(jobXml.contains(failureCommand));

        Assert.assertTrue(jobXml.contains("parameterOne"));
        Assert.assertTrue(jobXml.contains("parameterTwo"));

        Assert.assertFalse(jobXml.contains("$$"));
    }
}
