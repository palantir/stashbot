package com.palantir.stash.stashbot.urlbuilder;

import java.sql.SQLException;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.repository.Repository;
import com.palantir.stash.stashbot.managers.JenkinsBuildTypes;

public class TriggerBuildUrlBuilder {

    private final NavBuilder nb;

    public TriggerBuildUrlBuilder(NavBuilder nb) {
        this.nb = nb;
    }

    public String getJenkinsTriggerUrl(Repository repo, JenkinsBuildTypes type, String buildHead,
        Long pullRequestId, String mergeHead) throws SQLException {
        StringBuffer urlB = new StringBuffer(nb.buildAbsolute());
        urlB.append("/plugins/servlet/stashbot/build-trigger/");
        urlB.append(repo.getId().toString()).append("/");
        urlB.append(type.toString()).append("/");
        urlB.append(buildHead).append("/");
        if (pullRequestId != null && mergeHead != null) {
            urlB.append(mergeHead).append("/");
            urlB.append(pullRequestId.toString());
        }
        return urlB.toString();
    }
}
