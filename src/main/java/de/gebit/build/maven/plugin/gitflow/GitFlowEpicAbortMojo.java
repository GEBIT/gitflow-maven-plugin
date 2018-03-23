//
// GitFlowEpicAbortMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort epic branch.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
@Mojo(name = "epic-abort", aggregator = true)
public class GitFlowEpicAbortMojo extends AbstractGitFlowEpicMojo {

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        if (gitRebaseInProcess()) {
            throw new GitFlowFailureException("A rebase of the current branch is in process. Cannot abort epic now.",
                    "Finish rebase process first in order to proceed.");
        } else if (gitMergeInProcess()) {
            throw new GitFlowFailureException("A merge into the current branch is in process. Cannot abort epic now.",
                    "Finish merge process first in order to proceed.");
        }

        List<String> branches = gitAllEpicBranches();
        if (branches.isEmpty()) {
            throw new GitFlowFailureException("There are no epic branches in your repository.", null);
        }
        String currentBranch = gitCurrentBranch();
        boolean isOnEpicBranch = false;
        for (String branch : branches) {
            if (branch.equals(currentBranch)) {
                isOnEpicBranch = true;
                getLog().info("Current epic branch: " + currentBranch);
                break;
            }
        }
        String epicBranchName;
        if (!isOnEpicBranch) {
            epicBranchName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                    "Choose epic branch to abort", branches,
                    new GitFlowFailureInfo(
                            "In non-interactive mode 'mvn flow:epic-abort' can be executed only on an epic branch.",
                            "Please switch to an epic branch first or run in interactive mode.",
                            "'git checkout BRANCH' to switch to the epic branch",
                            "'mvn flow:epic-abort' to run in interactive mode"));
        } else {
            if (executeGitHasUncommitted()) {
                boolean confirmed = getPrompter().promptConfirmation(
                        "You have some uncommitted files. If you continue any changes will be discarded. Continue?",
                        false,
                        new GitFlowFailureInfo("You have some uncommitted files.",
                                "Commit or discard local changes in order to proceed or run in interactive mode.",
                                "'git add' and 'git commit' to commit your changes",
                                "'git reset --hard' to throw away your changes",
                                "'mvn flow:epic-abort' to run in interactive mode"));
                if (!confirmed) {
                    throw new GitFlowFailureException(
                            "You have aborted epic-abort process because of uncommitted files.",
                            "Commit or discard local changes in order to proceed.",
                            "'git add' and 'git commit' to commit your changes",
                            "'git reset --hard' to throw away your changes");
                }
            }
            epicBranchName = currentBranch;
            String baseBranch = gitEpicBranchBaseBranch(epicBranchName);
            gitEnsureLocalBranchExists(baseBranch);
            gitResetHard();
            gitCheckout(baseBranch);
        }

        if (gitBranchExists(epicBranchName)) {
            gitBranchDeleteForce(epicBranchName);
        }
        if (pushRemote) {
            gitBranchDeleteRemote(epicBranchName);
        }
    }

}
