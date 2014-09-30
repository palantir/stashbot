package com.palantir.stash.stashbot.outputhandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.utils.process.ProcessException;
import com.atlassian.utils.process.Watchdog;
import com.google.common.collect.ImmutableList;
import com.google.common.io.LineReader;

public class CommandOutputHandlerFactory {

    public CommandOutputHandler<Object> getRevlistOutputHandler() {
        return new CommandOutputHandler<Object>() {

            private ArrayList<String> changesets;
            private LineReader lr;
            private boolean processed = false;

            @Override
            public void complete() throws ProcessException {
            }

            @Override
            public void setWatchdog(Watchdog watchdog) {
            }

            @Override
            public Object getOutput() {
                if (processed == false) {
                    throw new IllegalStateException("getOutput() called before process()");
                }
                return ImmutableList.copyOf(changesets).reverse();
            }

            @Override
            public void process(InputStream output) throws ProcessException {
                processed = true;
                if (changesets == null) {
                    changesets = new ArrayList<String>();
                    lr = new LineReader(new InputStreamReader(output));
                }

                String sha1 = "";
                while (sha1 != null) {
                    try {
                        sha1 = lr.readLine();
                    } catch (IOException e) {
                        // dear god, why is this happening?
                        throw new RuntimeException(e);
                    }
                    if (sha1 != null && sha1.matches("[0-9a-fA-F]{40}")) {
                        changesets.add(sha1);
                    }
                }
            }
        };
    }

    /**
     * Returns an output handler which provides an ImmutableList of branches in the form "refs/heads/foo" when the
     * command prints out a list of the form "XXfoo\n" where X doesn't matter.
     * 
     * @return ImmutableList<String> branchNames
     */
    public CommandOutputHandler<Object> getBranchContainsOutputHandler() {
        return new CommandOutputHandler<Object>() {

            private ArrayList<String> branches;
            private LineReader lr;
            private boolean processed = false;

            @Override
            public void complete() throws ProcessException {
            }

            @Override
            public void setWatchdog(Watchdog watchdog) {
            }

            @Override
            public Object getOutput() {
                if (processed == false) {
                    throw new IllegalStateException("getOutput() called before process()");
                }
                return ImmutableList.copyOf(branches);
            }

            @Override
            public void process(InputStream output) throws ProcessException {
                processed = true;
                if (branches == null) {
                    branches = new ArrayList<String>();
                    lr = new LineReader(new InputStreamReader(output));
                }

                String branch = "";
                while (branch != null) {
                    try {
                        branch = lr.readLine();
                    } catch (IOException e) {
                        // dear god, why is this happening?
                        throw new RuntimeException(e);
                    }
                    // format is "  somebranch\n* branchIamOn\n  someOtherBranch"
                    if (branch != null) {
                        branches.add("refs/heads/" + branch.substring(2));
                    }
                }
            }
        };
    }
}
