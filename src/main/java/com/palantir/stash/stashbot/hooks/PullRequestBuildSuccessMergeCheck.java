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

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.scm.pull.MergeRequestCheck;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.PullRequestMetadata;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.logger.StashbotLoggerFactory;

/**
 * This class is a MergeRequestCheck to disable merging where the target repo
 * has CI enabled and no comments which
 * 
 * @author cmyers
 * 
 */
public class PullRequestBuildSuccessMergeCheck implements MergeRequestCheck {

	private final ConfigurationPersistenceManager cpm;
	private final Logger log;

	public PullRequestBuildSuccessMergeCheck(
			ConfigurationPersistenceManager cpm, StashbotLoggerFactory lf) {
		this.cpm = cpm;
		this.log = lf.getLoggerForThis(this);
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

		PullRequestMetadata prm = cpm.getPullRequestMetadata(pr);
		log.debug("PRM: success " + prm.getSuccess().toString() + " override "
				+ prm.getOverride().toString());

		if (prm.getOverride() || prm.getSuccess()) {
			return;
		}

		mr.veto("Green build required to merge",
				"Either retrigger the build so it succeeds, or add a comment with the string '==OVERRIDE==' to override the requirement");
	}
}
