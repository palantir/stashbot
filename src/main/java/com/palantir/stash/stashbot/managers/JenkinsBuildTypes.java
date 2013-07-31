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
package com.palantir.stash.stashbot.managers;

import com.atlassian.stash.repository.Repository;

public enum JenkinsBuildTypes {
    NOOP,
    VERIFICATION,
    PUBLISH;

    public static JenkinsBuildTypes fromString(String s) {
        for (JenkinsBuildTypes t : JenkinsBuildTypes.values()) {
            if (t.toString().equals(s.toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    public String toString() {
        return super.toString().toLowerCase();
    }

    // TODO: remove invalid characters from repo
    public String getBuildNameFor(Repository repo) {
        if (this == NOOP) {
            throw new IllegalStateException("Cannot getBuildName for NOOP build");
        }
        String project = repo.getProject().getKey();
        String nameSlug = repo.getSlug();
        String key = project + "_" + nameSlug + "_" + this.toString();
        // jenkins does toLowerCase() on all keys, so we must do the same
        return key.toLowerCase();
    }
}
