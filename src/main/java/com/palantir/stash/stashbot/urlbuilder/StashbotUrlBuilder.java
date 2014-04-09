package com.palantir.stash.stashbot.urlbuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.jobtemplate.JobType;

public class StashbotUrlBuilder {

    private final NavBuilder nb;

    public StashbotUrlBuilder(NavBuilder nb) {
        this.nb = nb;
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
        url = url.replace("://",
            "://" + mask(jsc.getStashUsername()) + ":" + mask(jsc.getStashPassword())
                + "@");
        return url;
    }

    public String buildCloneUrl(Repository repo, JenkinsServerConfiguration jsc) {
        String url = nb.repo(repo).clone("git").buildAbsoluteWithoutUsername();
        url = url.replace("://",
            "://" + mask(jsc.getStashUsername()) + ":" + mask(jsc.getStashPassword())
                + "@");
        return url;
    }

    private String mask( String str ) {
        try {
            return URLEncoder.encode( str, "UTF-8" );
        } catch( UnsupportedEncodingException e ) {
            return str;
        }
    }
}
