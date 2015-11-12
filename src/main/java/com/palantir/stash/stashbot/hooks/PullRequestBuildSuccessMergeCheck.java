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

import com.atlassian.bitbucket.build.BuildStatusService;
import com.atlassian.bitbucket.build.BuildSummary;
import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.CommitsBetweenRequest;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.scm.pull.MergeRequestCheck;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.logger.PluginLoggerFactory;
import com.palantir.stash.stashbot.persistence.PullRequestMetadata;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

/**
 * This class is a MergeRequestCheck to disable merging where the target repo
 * has CI enabled and no comments which
 * 
 * @author cmyers
 * 
 */
public class PullRequestBuildSuccessMergeCheck implements MergeRequestCheck {

    public static enum MergeCheckStatus {
        NO_BUILD(
            "Cannot merge without successful Stashbot build",
            "Either retrigger the build so it succeeds, or add a comment with the string '==OVERRIDE==' to override the requirement"),
        BUILD_IN_PROGRESS("Cannot merge until the Stashbot build currently in progress succeeds",
            "The build is still in progress, either wait for it to complete or ensure it isn't hung"),
        BUILD_FAILED("Cannot merge because the Stashbot build failed",
            "Either retrigger the build (if you suspect the failure was transient) or correct the PR to build successfully");

        private final String summary;
        private final String description;

        MergeCheckStatus(String summary, String description) {
            this.summary = summary;
            this.description = description;
        }

        public String getSummary() {
            return summary;
        }

        public String getDescription() {
            return description;
        }
    }

    private final CommitService cs;
    private final BuildStatusService bss;
    private final ConfigurationPersistenceService cpm;
    private final Logger log;

    public PullRequestBuildSuccessMergeCheck(
        CommitService cs, BuildStatusService bss, ConfigurationPersistenceService cpm, PluginLoggerFactory lf) {
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
        if (!cpm.getJobTypeStatusMapping(rc, JobType.VERIFY_PR)) {
            // speculative merge builds are disabled
            return;
        }
        if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
            log.debug("Pull Request " + pr.toString() + " ignored, branch "
                + pr.getToRef().getId() + " doesn't match verify regex");
            return;
        }

        // First, if strict mode is on, we want to veto for each commit in the PR that is missing a successful verify build
        if (rc.getStrictVerifyMode()) {
            CommitsBetweenRequest cbr = new CommitsBetweenRequest.Builder(pr).build();
            PageRequest pageReq = new PageRequestImpl(0, 500);
            Page<? extends Commit> page = cs.getCommitsBetween(cbr, pageReq);
            while (true) {
                for (Commit c : page.getValues()) {
                    log.trace("Processing commit " + c.getId());
                    BuildSummary bs = bss.getSummary(c.getId());
                    if (bs.getSuccessfulCount() == 0) {
                        mr.veto("Commit " + c.getId() + " not verified",
                            "When in strict verification mode, each commit in the PR must have at least one successful build");
                    }

                }
                if (page.getIsLastPage()) {
                    break;
                }
                pageReq = page.getNextPageRequest();
                page = cs.getCommitsBetween(cbr, pageReq);
            }
        }

        PullRequestMetadata prm = null;
        if (!rc.getRebuildOnTargetUpdate()) {
            // we want a PRM which simply matches the fromSha and the pull request ID.
            Collection<PullRequestMetadata> prms = cpm.getPullRequestMetadataWithoutToRef(pr);
            for (PullRequestMetadata cur : prms) {
                if (cur.getFromSha().equals(pr.getFromRef().getLatestCommit())
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

        // Possible states (true/false/dontcare): (buildStarted, success, override, failed)
        // Override (DC, DC, true, DC)
        // Success (DC, true, false, DC)
        // Failed (DC, false, false, true)
        // In progress but not success or fail (true, false, false, false)

        // Override || Success
        if (prm.getOverride() || prm.getSuccess()) {
            return;
        }

        // in all other cases, we want to veto for some reason - but figure out the most accurate reason here.
        MergeCheckStatus status;
        if (prm.getFailed()) {
            status = MergeCheckStatus.BUILD_FAILED;
        } else if (prm.getBuildStarted()) {
            status = MergeCheckStatus.BUILD_IN_PROGRESS;
        } else {
            status = MergeCheckStatus.NO_BUILD;
        }
        mr.veto(status.getSummary(), status.getDescription());
    }
}
