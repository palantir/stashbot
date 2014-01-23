package com.palantir.stash.stashbot.jobtemplate;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Represents the types of jobs we create using stashbot
 * 
 * @author cmyers
 */
public enum JobType {
    VERIFY_COMMIT, VERIFY_PR, PUBLISH, NOOP; // "default" or "null" job type

    private static final Map<JobType, String> nameMap = ImmutableMap.of(
        VERIFY_COMMIT, "verification", VERIFY_PR, "verify_pr", PUBLISH,
        "publish", NOOP, "noop");

    @Override
    public String toString() {
        return nameMap.get(this);
    }
}