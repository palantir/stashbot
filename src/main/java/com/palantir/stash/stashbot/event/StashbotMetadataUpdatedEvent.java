package com.palantir.stash.stashbot.event;

import javax.annotation.Nonnull;

import com.atlassian.stash.event.pull.PullRequestEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestAction;

/*
 * Defines an event to describe when the stashbot metadata is updated
 */
public class StashbotMetadataUpdatedEvent extends PullRequestEvent {

       // Documentation says that the constructor is turning protected, this is okay
       @SuppressWarnings("deprecation") 
       public StashbotMetadataUpdatedEvent(@Nonnull Object source,
                       @Nonnull PullRequest pullRequest) {
               super(source, pullRequest, PullRequestAction.UPDATED);
       }

}
