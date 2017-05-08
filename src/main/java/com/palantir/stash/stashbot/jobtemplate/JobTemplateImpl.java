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

import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;

// Custom AO implementation
// For details, see: http://www.javalobby.org/articles/activeobjects/
public class JobTemplateImpl {

    private final JobTemplate dis;

    public JobTemplateImpl(JobTemplate jt) {
        this.dis = jt;
    }

    // TODO: remove invalid characters from repo
    public String getBuildNameFor(Repository repo, JenkinsServerConfiguration jsc) {
        String project = repo.getProject().getKey();
        String nameSlug = repo.getSlug();
        String template = jsc.getJobTemplate();
        template = template.replaceAll("\\$repo", nameSlug).replaceAll("\\$project", project);
        String key = template + "_" + dis.getJobType().toString();
        // jenkins does toLowerCase() on all keys, so we must do the same
        return key.toLowerCase();
    }

}
