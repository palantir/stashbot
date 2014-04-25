package com.palantir.stash.stashbot.util;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.util.Operation;

public class RepoIdFetcherOperation implements Operation<Void, Exception> {

    private final RepositoryService repositoryService;
    private final Integer repoId;

    private Repository repo;
    private Boolean wasCalled;

    public RepoIdFetcherOperation(RepositoryService repositoryService, Integer id) {
        this.repositoryService = repositoryService;
        this.repoId = id;
        wasCalled = false;
    }

    @Override
    public Void perform() throws Exception {
        repo = repositoryService.getById(repoId);
        wasCalled = true;
        return null;
    }

    public Repository getRepo() {
        if (!wasCalled) {
            throw new IllegalStateException("Tried to get repo before calling perform()");
        }
        return repo;
    }
}