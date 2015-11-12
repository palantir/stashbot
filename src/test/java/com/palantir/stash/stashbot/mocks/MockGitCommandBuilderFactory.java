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
package com.palantir.stash.stashbot.mocks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.scm.git.command.GitCommand;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.GitScmCommandBuilder;
import com.atlassian.bitbucket.scm.git.command.revlist.GitRevListBuilder;

public class MockGitCommandBuilderFactory {

    private GitCommandBuilderFactory gcbf;
    private GitScmCommandBuilder gscb;
    private GitRevListBuilder grlb;
    private GitCommand<Object> cmd;

    private GitScmCommandBuilder branchCommandBuilder;
    private GitCommand<Object> branchCommand;

    private List<String> changesets;
    private Set<String> blacklistedChangesets;
    private Map<String, List<String>> branchMap;

    public MockGitCommandBuilderFactory() {
        reset();
    }

    @SuppressWarnings("unchecked")
    private void reset() {
        // list of changesets in order
        changesets = new ArrayList<String>();
        blacklistedChangesets = new HashSet<String>();
        // for each hash, list of branches that contain said hash
        branchMap = new HashMap<String, List<String>>();

        gcbf = Mockito.mock(GitCommandBuilderFactory.class);
        grlb = Mockito.mock(GitRevListBuilder.class);
        gscb = Mockito.mock(GitScmCommandBuilder.class);
        branchCommandBuilder = Mockito.mock(GitScmCommandBuilder.class);
        cmd = Mockito.mock(GitCommand.class);
        branchCommand = Mockito.mock(GitCommand.class);

        // RevList cmd
        Mockito.when(gcbf.builder()).thenReturn(gscb);
        Mockito.when(gcbf.builder(Mockito.any(Repository.class))).thenReturn(gscb);
        Mockito.when(gscb.revList()).thenReturn(grlb);
        final ArgumentCaptor<CommandOutputHandler<Object>> cohCaptor =
            (ArgumentCaptor<CommandOutputHandler<Object>>) (Object) ArgumentCaptor.forClass(CommandOutputHandler.class);
        Mockito.when(grlb.build(cohCaptor.capture())).thenReturn(cmd);

        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CommandOutputHandler<Object> coh = cohCaptor.getValue();

                List<String> finalCS = new ArrayList<String>();
                for (String cs : changesets) {
                    if (!blacklistedChangesets.contains(cs)) {
                        finalCS.add(cs);
                    }
                }
                String str = StringUtils.join(finalCS, "\n") + "\n";
                InputStream is = new ByteArrayInputStream(str.getBytes());
                coh.process(is);
                return null;
            }
        }).when(cmd).call();

        // Branch cmd - returns list of all branches
        final ArgumentCaptor<CommandOutputHandler<Object>> branchCOHCaptor =
            (ArgumentCaptor<CommandOutputHandler<Object>>) (Object) ArgumentCaptor.forClass(CommandOutputHandler.class);
        Mockito.when(gscb.command("branch")).thenReturn(branchCommandBuilder);
        Mockito.when(branchCommandBuilder.build(branchCOHCaptor.capture())).thenReturn(branchCommand);
        Mockito.doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CommandOutputHandler<Object> coh = branchCOHCaptor.getValue();

                String output = "";
                List<String> branches = new ArrayList<String>(branchMap.keySet());

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

    public Set<String> getBlacklistedChangesets() {
        return blacklistedChangesets;
    }

    public Map<String, List<String>> getBranchMap() {
        return branchMap;
    }

    public GitCommandBuilderFactory getGitCommandBuilderFactory() {
        return gcbf;
    }
}
