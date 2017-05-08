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
package com.palantir.stash.stashbot.managers;

import static org.mockito.Matchers.any;

import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.util.Assert;

import com.atlassian.stash.repository.Repository;
import com.offbytwo.jenkins.JenkinsServer;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class JenkinsClientManagerTest {

	private static final String JENKINS_URL = "http://www.example.com:8080/jenkins";
	private static final String JENKINS_USERNAME = "jenkins_user";
	private static final String JENKINS_PW = "jenkins_pw";
	@Mock
	private RepositoryConfiguration rc;
	@Mock
	private JenkinsServerConfiguration jsc;

	private JenkinsClientManager jcm;

	@Before
	public void setUp() {

		MockitoAnnotations.initMocks(this);

		Mockito.when(jsc.getUrl()).thenReturn(JENKINS_URL);
		Mockito.when(jsc.getUsername()).thenReturn(JENKINS_USERNAME);
		Mockito.when(jsc.getPassword()).thenReturn(JENKINS_PW);
		Mockito.when(jsc.getPrefixTemplate()).thenReturn("");
		Mockito.when(jsc.getUrlForRepo(any(Repository.class))).thenReturn(
				JENKINS_URL);
		jcm = new JenkinsClientManager();
	}

	@Test
	public void testJCM() throws URISyntaxException {
		JenkinsServer js = jcm.getJenkinsServer(jsc, rc, null);
		Assert.notNull(js);
	}
}
