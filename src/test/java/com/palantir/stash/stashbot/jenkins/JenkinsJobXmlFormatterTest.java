//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.palantir.stash.stashbot.jenkins.JenkinsJobXmlFormatter;
import com.palantir.stash.stashbot.jenkins.JenkinsJobXmlFormatter.JenkinsBuildParam;
import com.palantir.stash.stashbot.jenkins.JenkinsJobXmlFormatter.JenkinsBuildParamType;

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
