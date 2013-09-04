package com.palantir.stash.stashbot.mocks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private GitScmCommandBuilder branchCommandBuilder;
    private GitScmCommandBuilder branchCommandBuilderArg;
    private GitCommand<Object> branchCommand;

    private List<String> changesets;
    private Map<String, List<String>> branchMap;

    public MockGitCommandBuilderFactory() {
        reset();
    }

    @SuppressWarnings("unchecked")
    private void reset() {
        // list of changesets in order
        changesets = new ArrayList<String>();
        // for each hash, list of branches that contain said hash
        branchMap = new HashMap<String, List<String>>();

        gcbf = Mockito.mock(GitCommandBuilderFactory.class);
        grlb = Mockito.mock(GitRevListBuilder.class);
        gscb = Mockito.mock(GitScmCommandBuilder.class);
        branchCommandBuilder = Mockito.mock(GitScmCommandBuilder.class);
        branchCommandBuilderArg = Mockito.mock(GitScmCommandBuilder.class);
        cmd = Mockito.mock(GitCommand.class);
        branchCommand = Mockito.mock(GitCommand.class);

        // RevList cmd
        Mockito.when(gcbf.builder()).thenReturn(gscb);
        Mockito.when(gcbf.builder(Mockito.any(Repository.class))).thenReturn(gscb);
        Mockito.when(gscb.revList()).thenReturn(grlb);
        final ArgumentCaptor<CommandOutputHandler<Object>> cohCaptor =
            (ArgumentCaptor<CommandOutputHandler<Object>>) (Object) ArgumentCaptor.forClass(CommandOutputHandler.class);
        // Mockito.when(grlb.build(Mockito.any(CommandOutputHandler.class))).thenReturn(cmd);
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

        // Branch cmd
        final ArgumentCaptor<CommandOutputHandler<Object>> branchCOHCaptor =
            (ArgumentCaptor<CommandOutputHandler<Object>>) (Object) ArgumentCaptor.forClass(CommandOutputHandler.class);
        Mockito.when(gscb.command("branch")).thenReturn(branchCommandBuilder);
        final ArgumentCaptor<String> branchCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.when(branchCommandBuilder.argument("--contains")).thenReturn(branchCommandBuilderArg);
        Mockito.when(branchCommandBuilderArg.argument(branchCaptor.capture())).thenReturn(branchCommandBuilder);
        Mockito.when(branchCommandBuilder.build(branchCOHCaptor.capture())).thenReturn(branchCommand);
        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CommandOutputHandler<Object> coh = branchCOHCaptor.getValue();

                String output = "";
                String currentHash = branchCaptor.getValue();
                List<String> branches = branchMap.get(currentHash);
                if (branches == null) {
                    branches = new ArrayList<String>();
                }

                for (String branch : branches) {
                    output = output + "  " + branch + "\n";
                }
                InputStream is = new ByteArrayInputStream(output.getBytes());
                coh.process(is);
                return null;
            }
        }).when(branchCommand).call();

    }

    public List<String> getChangesets() {
        return changesets;
    }

    public Map<String, List<String>> getBranchMap() {
        return branchMap;
    }

    public GitCommandBuilderFactory getGitCommandBuilderFactory() {
        return gcbf;
    }
}
