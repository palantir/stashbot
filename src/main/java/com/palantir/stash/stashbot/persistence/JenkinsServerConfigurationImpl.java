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

import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration.AuthenticationMode;

public class JenkinsServerConfigurationImpl {

    private static final String EMPTY_PREFIX = "/";
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

    public String getFolderPrefix() {
        if (jsc.getFolderPrefixRaw().equals(EMPTY_PREFIX)) {
            return "";
        }
        return jsc.getFolderPrefixRaw();
    }

    public void setFolderPrefix(String folderPrefix) {
        if (folderPrefix == null || folderPrefix.isEmpty()) {
            jsc.setFolderPrefixRaw(EMPTY_PREFIX);
            return;
        }
        jsc.setFolderPrefixRaw(folderPrefix);
    }
}
