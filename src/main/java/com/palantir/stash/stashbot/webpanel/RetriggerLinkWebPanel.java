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
package com.palantir.stash.stashbot.webpanel;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.plugin.web.model.WebPanel;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class RetriggerLinkWebPanel implements WebPanel {

    private final ConfigurationPersistenceService cpm;
    private final StashbotUrlBuilder ub;
    private final Logger log;

    public RetriggerLinkWebPanel(ConfigurationPersistenceService cpm,
        StashbotUrlBuilder ub, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.ub = ub;
        this.log = lf.getLoggerForThis(this);
    }

    @Override
    public String getHtml(Map<String, Object> context) {
        Writer holdSomeText = new StringWriter();
        try {
            writeHtml(holdSomeText, context);
        } catch (IOException e) {
            log.error("Error occured rendering web panel", e);
            return "Error occured loading text";
        }
        return holdSomeText.toString();
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context)
        throws IOException {
        try {
            Repository repo = (Repository) context.get("repository");
            RepositoryConfiguration rc = cpm
                .getRepositoryConfigurationForRepository(repo);

            if (!rc.getCiEnabled()) {
                // No link
                return;
            }

            // XXX: MIGRATION: does this work?
            Commit commit = (Commit) context.get("commit");
            String url = ub.getJenkinsTriggerUrl(repo, JobType.VERIFY_COMMIT,
                commit.getId(), null);
            String pubUrl = ub.getJenkinsTriggerUrl(repo, JobType.PUBLISH,
                commit.getId(), null);
            // TODO: add ?reason=<buildRef> somehow to end of URLs?
            writer.append("Trigger: ( <a href=\"" + url + "\">Verify</a> | ");
            writer.append("<a href=\"" + pubUrl + "\">Publish</a> )");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
