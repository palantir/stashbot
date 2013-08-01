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
package com.palantir.stash.stashbot.hooks;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.log4j.Logger;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestActivity;
import com.atlassian.stash.pull.PullRequestActivityVisitor;
import com.atlassian.stash.pull.PullRequestCommentActivity;
import com.atlassian.stash.pull.PullRequestMergeActivity;
import com.atlassian.stash.pull.PullRequestRescopeActivity;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.scm.pull.MergeRequestCheck;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;

/**
 * This class is a MergeRequestCheck to disable merging where the target repo has CI enabled and no comments which
 * 
 * @author cmyers
 * 
 */
public class PullRequestBuildSuccessMergeCheck implements MergeRequestCheck {

    private static final Logger log = Logger.getLogger(PullRequestBuildSuccessMergeCheck.class.toString());

    private final PullRequestService prs;
    private final ConfigurationPersistenceManager cpm;

    public PullRequestBuildSuccessMergeCheck(PullRequestService prs, ConfigurationPersistenceManager cpm) {
        this.prs = prs;
        this.cpm = cpm;
    }

    public class CustomPullRequestActivityVisitor implements PullRequestActivityVisitor {

        private final List<String> regexs;
        private final Logger log;

        private boolean isMatch;

        public CustomPullRequestActivityVisitor(Logger log, String regex) {
            this.log = log;
            this.regexs = new ArrayList<String>();
            regexs.add(regex);
            isMatch = false;
        }

        public CustomPullRequestActivityVisitor(Logger log, List<String> regexs) {
            this.log = log;
            this.regexs = regexs;
            isMatch = false;
        }

        @Override
        public void visit(@Nonnull PullRequestActivity arg0) {
            return;
        }

        @Override
        public void visit(@Nonnull PullRequestCommentActivity arg0) {
            // once we find a match, done
            if (isMatch == true)
                return;

            // see if it is a matching comment
            for (String regex : regexs) {
                if (arg0.getComment().getText().matches(regex)) {
                    log.debug("Text '" + arg0.getComment().getText() + "' matches regex '" + regex + "'");
                    isMatch = true;
                }
            }
        }

        @Override
        public void visit(@Nonnull PullRequestMergeActivity arg0) {
            return;
        }

        @Override
        public void visit(@Nonnull PullRequestRescopeActivity arg0) {
            return;
        }

        public boolean isMatch() {
            return isMatch;
        }
    }

    @Override
    public void check(@Nonnull MergeRequest mr) {
        PullRequest pr = mr.getPullRequest();
        Repository repo = pr.getToRef().getRepository();

        RepositoryConfiguration rc;
        try {
            rc = cpm.getRepositoryConfigurationForRepository(repo);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get RepositoryConfiguration", e);
        }
        if (!rc.getCiEnabled()) {
            return;
        }
        if (!pr.getToRef().getId().matches(rc.getVerifyBranchRegex())) {
            log.debug("Pull Request " + pr.toString() + " ignored, branch " + pr.getToRef().getId()
                + " doesn't match verify regex");
            return;
        }

        PageRequest pageReq = new PageRequestImpl(0, 500);
        Page<? extends PullRequestActivity> p = prs.getActivities(repo.getId(), pr.getId(), pageReq);
        List<String> regexes = new ArrayList<String>();
        regexes.add(".*==OVERRIDE==.*");
        regexes.add(".*==SUCCESSFUL==.*");

        CustomPullRequestActivityVisitor visitor = new CustomPullRequestActivityVisitor(log, regexes);

        while (true) {
            for (PullRequestActivity ra : p.getValues()) {
                // OMG really?
                ra.accept(visitor);
            }

            if (p.getIsLastPage())
                break;
            pageReq = p.getNextPageRequest();
            p = prs.getActivities(repo.getId(), pr.getId(), pageReq);
        }

        if (visitor.isMatch()) {
            return;
        }
        // no match, so don't approve
        mr.veto(
            "Green build required to merge",
            "Either retrigger the build so it succeeds, or add a comment with the string '==OVERRIDE==' to override the requirement");
    }
}
