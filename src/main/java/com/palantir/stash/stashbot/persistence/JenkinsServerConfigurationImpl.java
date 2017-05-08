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
package com.palantir.stash.stashbot.persistence;

import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;

public class JenkinsServerConfigurationImpl {

	private final JenkinsServerConfiguration jsc;

	public JenkinsServerConfigurationImpl(JenkinsServerConfiguration jsc) {
		this.jsc = jsc;
	}

	public AuthenticationMode getAuthenticationMode() {
		return AuthenticationMode.fromMode(jsc.getAuthenticationModeStr());
	}

	public void setAuthenticationMode(AuthenticationMode authMode) {
		jsc.setAuthenticationModeStr(authMode.getMode());
	}

	public String getUrlForRepo(Repository r) {
		if (!jsc.getPrefixTemplate().equals("")) {
			String template = jsc.getPrefixTemplate();
			template = template.replaceAll("\\$project", r.getProject()
					.getKey());
			template = template.replaceAll("\\$repo", r.getSlug());

			return template;
		} else {
			return "";
		}
	}

	public static String convertCredUUID (String password, Repository r) {
	    password = password.replaceAll("\\$project", r.getProject().getKey());
	    password = password.replaceAll("\\$repo", r.getSlug());
	    return password;
	}

}
