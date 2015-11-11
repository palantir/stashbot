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

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryCloneLinksRequest;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbot.jobtemplate.JobType;
import com.palantir.stash.stashbot.persistence.JenkinsServerConfiguration;

public class StashbotUrlBuilder {

    private final NavBuilder nb;
    private final RepositoryService rs;

    public StashbotUrlBuilder(NavBuilder nb, RepositoryService rs) {
        this.nb = nb;
        this.rs = rs;
    }

    public String getJenkinsTriggerUrl(Repository repo, JobType jt,
        String buildHead, PullRequest pullRequest) throws SQLException {
        StringBuffer urlB = new StringBuffer(nb.buildAbsolute());
        urlB.append("/plugins/servlet/stashbot/build-trigger/");
        urlB.append(repo.getId().toString()).append("/");
        urlB.append(jt.toString()).append("/");
        urlB.append(buildHead);
        if (pullRequest != null) {
            urlB.append("/");
            urlB.append(pullRequest.getToRef().getLatestChangeset());
            urlB.append("/");
            urlB.append(pullRequest.getId().toString());
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
        RepositoryCloneLinksRequest rclr = null;
        String url = null;

        // we build without username because we insert username AND password, and need both, in the case where we are using USERNAME_AND_PASSWORD.
        switch (jsc.getAuthenticationMode()) {
        case USERNAME_AND_PASSWORD:
            rclr = new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("http").user(null).build();
            url = rs.getCloneLinks(rclr).iterator().next().getHref();
            url = url.replace("://",
                "://" + mask(jsc.getStashUsername()) + ":" + mask(jsc.getStashPassword())
                    + "@");
            break;
        case CREDENTIAL_MANUALLY_CONFIGURED:
            rclr = new RepositoryCloneLinksRequest.Builder().repository(repo).protocol("ssh").build();
            url = rs.getCloneLinks(rclr).iterator().next().getHref();
            break;
        default:
            throw new IllegalStateException("Invalid value - update this code after adding an authentication mode");
        }
        return url;
    }

    public String buildStashCommitUrl(Repository repo, String changeset) {
        return nb.repo(repo).changeset(changeset).buildAbsolute();
    }

    private String mask( String str ) {
        try {
            return URLEncoder.encode( str, "UTF-8" );
        } catch( UnsupportedEncodingException e ) {
            return str;
        }
    }
}
