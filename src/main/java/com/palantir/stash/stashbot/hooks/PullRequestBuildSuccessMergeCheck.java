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
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;
import java.util.Collection;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.atlassian.stash.build.BuildStats;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.scm.pull.MergeRequestCheck;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;

/**
 * This class is a MergeRequestCheck to disable merging where the target repo
 * has CI enabled and no comments which
 * 
 * @author cmyers
 * 
 */
public class PullRequestBuildSuccessMergeCheck implements MergeRequestCheck {

    private final CommitService cs;
    private final BuildStatusService bss;
    private final ConfigurationPersistenceManager cpm;
    private final Logger log;

    public PullRequestBuildSuccessMergeCheck(
        CommitService cs, BuildStatusService bss, ConfigurationPersistenceManager cpm, PluginLoggerFactory lf) {
        this.cpm = cpm;
        this.log = lf.getLoggerForThis(this);
        this.cs = cs;
        this.bss = bss;
    }

    @Override
    public void check(@Nonnull MergeRequest mr) {
        PullRequest pr = mr.getPullRequest();
        Repository repo = pr.getToRef().getRepository();

        RepositoryConfiguration rc;
        try {
            rc = cpm.getRepositoryConfigurationForRepository(repo);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get RepositoryConfiguration",
                e);
        }
        if (!rc.getCiEnabled()) {
            return;
        }
        if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
            log.debug("Pull Request " + pr.toString() + " ignored, branch "
                + pr.getToRef().getId() + " doesn't match verify regex");
            return;
        }

        // First, if strict mode is on, we want to fail fast if any commit in the PR is missing a successful verify build
        if (rc.getStrictVerifyMode()) {
            ChangesetsBetweenRequest cbr = new ChangesetsBetweenRequest.Builder(pr).build();
            PageRequest pageReq = new PageRequestImpl(0, 500);
            Page<? extends Changeset> page = cs.getChangesetsBetween(cbr, pageReq);
            while (true) {
                for (Changeset c : page.getValues()) {
                    log.trace("Processing commit " + c.getId());
                    BuildStats bs = bss.getStats(c.getId());
                    if (bs.getSuccessfulCount() == 0) {
                        mr.veto("Commit " + c.getId() + " not verified",
                            "When in strict verification mode, each commit in the PR must have at least one successful build");
                        return;
                    }

                }
                if (page.getIsLastPage()) {
                    break;
                }
                pageReq = page.getNextPageRequest();
                page = cs.getChangesetsBetween(cbr, pageReq);
            }
        }

        PullRequestMetadata prm = null;
        if (!rc.getRebuildOnTargetUpdate()) {
            // we want a PRM which simply matches the fromSha and the pull request ID.
            Collection<PullRequestMetadata> prms = cpm.getPullRequestMetadataWithoutToRef(pr);
            for (PullRequestMetadata cur : prms) {
                if (cur.getFromSha().equals(pr.getFromRef().getLatestChangeset())
                    && (cur.getOverride() || cur.getSuccess())) {
                    log.debug("Found match PRM");
                    log.debug("PRM: success " + cur.getSuccess().toString() + " override "
                        + cur.getOverride().toString());
                    return;
                }
            }
            prm = cpm.getPullRequestMetadata(pr);
        } else {
            // Then we want to ensure a build that matches exactly succeeded / was overridden
            prm = cpm.getPullRequestMetadata(pr);
        }

        if (prm.getOverride() || prm.getSuccess()) {
            return;
        }
        mr.veto(
            "Green build required to merge",
            "Either retrigger the build so it succeeds, or add a comment with the string '==OVERRIDE==' to override the requirement");
    }
}
