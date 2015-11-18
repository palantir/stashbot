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
package com.palantir.stash.stashbot.util;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Date;

import org.slf4j.Logger;

import com.atlassian.bitbucket.build.BuildState;
import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.build.BuildStatusSetRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.Operation;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class BuildStatusAddOperation implements Operation<Void, Exception> {

    private final StashbotUrlBuilder sub;
    private final BuildStatusService bss;
    private final BuildStatusSetRequest.Builder b;
    private final ConfigurationPersistenceService cps;
    private final Logger log;

    public BuildStatusAddOperation(StashbotUrlBuilder sub, BuildStatusService bss, ConfigurationPersistenceService cps,
        PluginLoggerFactory plf, String buildHead) {
        this.sub = sub;
        this.bss = bss;
        this.cps = cps;
        this.log = plf.getLoggerForThis(this);
        this.b = new BuildStatusSetRequest.Builder(buildHead);
    }

    public void setBuildStatus(Repository repo, JobTemplate jt, BuildState bs, long buildNumber) throws SQLException {
        Date now = new Date(java.lang.System.currentTimeMillis());
        DateFormat df = DateFormat.getDateTimeInstance();

        RepositoryConfiguration rc = cps.getRepositoryConfigurationForRepository(repo);
        JenkinsServerConfiguration jsc = cps.getJenkinsServerConfiguration(rc.getJenkinsServerName());

        // key should be the jenkins job name
        String key;
        if (jsc.getFolderSupportEnabled()) {
            key = jsc.getFolderPrefix() + "/" + jt.getPathFor(repo) + "/" + jt.getBuildNameFor(repo);
        } else {
            key = jt.getBuildNameFor(repo);
        }
        String name = key + " (build " + Long.toString(buildNumber) + ")";
        String description = "Build " + Long.toString(buildNumber) + " "
            + bs.toString() + " at " + df.format(now);
        String url = sub.getJenkinsBuildUrl(repo, jt, buildNumber);

        b.dateAdded(now);
        b.key(key);
        b.name(name);
        b.description(description);
        b.url(url);
        b.state(bs);
    }
    
    @Override
    public Void perform() throws Exception {
    	BuildStatusSetRequest bssr = b.build();
    	log.debug("Registering build status for " + bssr.getKey() + " commit " + bssr.getCommitId());
        bss.set(bssr);
        return null;
    }
}
