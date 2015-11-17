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
package com.palantir.stash.stashbot.urlbuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.bitbucket.nav.NavBuilder;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryCloneLinksRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.persistence.JobTemplate;
import com.palantir.stash.stashbot.persistence.RepositoryConfiguration;

public class StashbotUrlBuilder {

    private final ConfigurationPersistenceService cps;
    private final NavBuilder nb;
    private final RepositoryService rs;

    public StashbotUrlBuilder(ConfigurationPersistenceService cps, NavBuilder nb, RepositoryService rs) {
        this.cps = cps;
        this.nb = nb;
        this.rs = rs;
    }

    public String getJenkinsTriggerUrl(Repository repo, JobType jt,
        String buildHead, PullRequest pullRequest) throws SQLException {
        StringBuffer urlB = new StringBuffer(nb.buildAbsolute());
        urlB.append("/plugins/servlet/stashbot/build-trigger/");
        urlB.append(repo.getId()).append("/");
        urlB.append(jt.toString()).append("/");
        urlB.append(buildHead);
        if (pullRequest != null) {
            urlB.append("/");
            urlB.append(pullRequest.getToRef().getLatestCommit());
            urlB.append("/");
            urlB.append(pullRequest.getId());
        }
        return urlB.toString();
    }

    public String buildReportingUrl(Repository repo, JobType jobType,
        JenkinsServerConfiguration jsc, String status) {
        // Look at the BuildSuccessReportinServlet if you change this:
        // "BASE_URL/REPO_ID/JOB_NAME/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]";
        // SEE ALSO:
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project#Buildingasoftwareproject-JenkinsSetEnvironmentVariables
        // TODO: Remove $repoId, hardcode ID?
        String url = nb
            .buildAbsolute()
            .concat("/plugins/servlet/stashbot/build-reporting/$repoId/"
                + jobType.toString() + "/" + status
                + "/$BUILD_NUMBER/$buildHead/$mergeHead/$pullRequestId");
        return url;
    }

    public String buildCloneUrl(Repository repo, JenkinsServerConfiguration jsc) {
        RepositoryCloneLinksRequest rclr =
            new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("http").user(null).build();
        String url = rs.getCloneLinks(rclr).iterator().next().getHref();
        // we build without username because we insert username AND password, and need both, in the case where we are using USERNAME_AND_PASSWORD.
        switch (jsc.getAuthenticationMode()) {
        case USERNAME_AND_PASSWORD:
            url = url.replace("://",
                "://" + mask(jsc.getStashUsername()) + ":" + mask(jsc.getStashPassword())
                    + "@");
            break;
        case CREDENTIAL_AUTOMATIC_SSH_KEY:
            RepositoryCloneLinksRequest rclrssh =
                new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("ssh").build();
            url = rs.getCloneLinks(rclrssh).iterator().next().getHref();
            break;
        case CREDENTIAL_MANUALLY_CONFIGURED:
            // do nothing
            // XXX: do we need to get the git/ssh link instead of the http link here?  maybe that's a new mode?
            break;
        default:
            throw new IllegalStateException("Invalid value - update this code after adding an authentication mode");
        }
        return url;
    }

    public String buildStashCommitUrl(Repository repo, String commit) {
        return nb.repo(repo).commit(commit).buildAbsolute();
    }

    private String mask(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    /* NOTE: elsewhere in JenkinsManager, we generate this URL (when folders are enabled) by fetching the entire folder chain from jenkins.
     * But in places where we are generating the links only, we don't want to have several server round-trips so we will instead assume it
     * exists and just create the URL.
     */
    public String getJenkinsJobUrl(Repository repo, JobTemplate jt) throws SQLException {
        RepositoryConfiguration rc = cps.getRepositoryConfigurationForRepository(repo);
        JenkinsServerConfiguration jsc = cps.getJenkinsServerConfiguration(rc.getJenkinsServerName());
        String baseUrl = jsc.getUrl();
        String prefix = "";
        if (jsc.getFolderSupportEnabled()) {
            prefix = prefix + jsc.getFolderPrefix();
        }
        if (jsc.getUseSubFolders()) {
            if (!prefix.isEmpty()) {
                prefix = prefix + "/" + jt.getPathFor(repo);
            } else {
                prefix = jt.getPathFor(repo);
            }
        }
        String url = baseUrl;
        if (!prefix.isEmpty()) {
            url = url + "/job/" + StringUtils.join(prefix.split("/"), "/job/");
        }
        url = url + "/job/" + jt.getBuildNameFor(repo);
        return url;
    }

    public String getJenkinsBuildUrl(Repository repo, JobTemplate jt, long buildNumber) throws SQLException {
        return getJenkinsJobUrl(repo, jt) + "/" + Long.toString(buildNumber);
    }
}
