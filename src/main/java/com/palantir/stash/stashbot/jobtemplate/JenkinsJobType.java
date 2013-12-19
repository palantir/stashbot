package com.palantir.stash.stashbot.jobtemplate;

/**
 * Represents the types of jobs we create in jenkins
 * 
 * @author cmyers
 */
public enum JenkinsJobType {
    NOOP_BUILD,
    VERIFY_BUILD,
    RELEASE_BUILD;
}