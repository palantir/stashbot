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

import java.net.URI;
import java.net.URISyntaxException;

import com.atlassian.stash.repository.Repository;
import com.offbytwo.jenkins.JenkinsServer;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

/**
 * This class exists to encapsulate the jenkins client library and make
 * mocking/testing easier.
 *
 * TODO: cache jenkins server object?
 *
 * @author cmyers
 *
 */
public class JenkinsClientManager {

	public JenkinsServer getJenkinsServer(JenkinsServerConfiguration jsc,
			RepositoryConfiguration rc, Repository r) throws URISyntaxException {
        System.out.println(jsc.getUrlForRepo(r));
		return new JenkinsServer(new URI(jsc.getUrlForRepo(r)),
				jsc.getUsername(), jsc.getPassword());
	}

}
