package com.palantir.stash.stashbot.mocks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.stash.scm.git.GitCommand;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitScmCommandBuilder;
import com.atlassian.stash.scm.git.revlist.GitRevListBuilder;

public class MockGitCommandBuilderFactory {

    private GitCommandBuilderFactory gcbf;
    private GitScmCommandBuilder gscb;
    private GitRevListBuilder grlb;
    private GitCommand<Object> cmd;

    private Set<String> changesets;

    public MockGitCommandBuilderFactory() {
        reset();
    }

    @SuppressWarnings("unchecked")
    private void reset() {
        changesets = new HashSet<String>();
        gcbf = Mockito.mock(GitCommandBuilderFactory.class);
        grlb = Mockito.mock(GitRevListBuilder.class);
        gscb = Mockito.mock(GitScmCommandBuilder.class);
        cmd = Mockito.mock(GitCommand.class);

        Mockito.when(gcbf.builder()).thenReturn(gscb);
        Mockito.when(gcbf.builder(Mockito.any(Repository.class))).thenReturn(gscb);
        Mockito.when(gscb.revList()).thenReturn(grlb);
        final ArgumentCaptor<CommandOutputHandler<Object>> cohCaptor =
            (ArgumentCaptor<CommandOutputHandler<Object>>) (Object) ArgumentCaptor.forClass(CommandOutputHandler.class);
        Mockito.when(grlb.build(Mockito.any(CommandOutputHandler.class))).thenReturn(cmd);
        Mockito.when(grlb.build(cohCaptor.capture())).thenReturn(cmd);

        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CommandOutputHandler<Object> coh = cohCaptor.getValue();

                String str = StringUtils.join(changesets, "\n") + "\n";
                InputStream is = new ByteArrayInputStream(str.getBytes());
                coh.process(is);
                return null;
            }
        }).when(cmd).call();
    }

    public void addChangeset(String changeset) {
        changesets.add(changeset);
    }

    public GitCommandBuilderFactory getGitCommandBuilderFactory() {
        return gcbf;
    }
}
