package com.palantir.stash.stashbothelper.managers;

import com.atlassian.stash.repository.Repository;

public enum JenkinsBuildTypes {
    NOOP,
    VERIFICATION,
    PUBLISH;

    public static JenkinsBuildTypes fromString(String s) {
        for (JenkinsBuildTypes t : JenkinsBuildTypes.values()) {
            if (t.toString().equals(s.toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    public String toString() {
        return super.toString().toLowerCase();
    }

    // TODO: remove invalid characters from repo
    public String getBuildNameFor(Repository repo) {
        if (this == NOOP) {
            throw new IllegalStateException("Cannot getBuildName for NOOP build");
        }
        String project = repo.getProject().getKey();
        String nameSlug = repo.getSlug();
        String key = project + "_" + nameSlug + "_" + this.toString();
        // jenkins does toLowerCase() on all keys, so we must do the same
        return key.toLowerCase();
    }
}
