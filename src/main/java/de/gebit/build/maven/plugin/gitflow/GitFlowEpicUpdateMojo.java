//
// GitFlowEpicUpdateMojo.java
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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Merge the base branch into the epic branch.
 *
 * @author Volodymyr Medvid
 */
@Mojo(name = "epic-update", aggregator = true)
public class GitFlowEpicUpdateMojo extends AbstractGitFlowEpicMojo {

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        String epicBranchName = gitMergeIntoEpicBranchInProcess();
        if (epicBranchName == null) {
            checkUncommittedChanges();

            List<String> branches = gitAllEpicBranches();
            if (branches.isEmpty()) {
                throw new GitFlowFailureException("There are no epic branches in your repository.",
                        "Please start an epic first.", "'mvn flow:epic-start'");
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
            if (!isOnEpicBranch) {
                epicBranchName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                        "Choose epic branch to update", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:epic-update' can be executed only on an epic branch.",
                                "Please switch to an epic branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the epic branch",
                                "'mvn flow:epic-update' to run in interactive mode"));
                gitEnsureLocalBranchIsUpToDateIfExists(epicBranchName,
                        new GitFlowFailureInfo("Remote and local epic branches '" + epicBranchName + "' diverge.",
                                "Rebase or merge the changes in local epic branch '" + epicBranchName + "' first.",
                                "'git rebase'"));
                gitCheckout(epicBranchName);
            } else {
                epicBranchName = currentBranch;
                gitEnsureCurrentLocalBranchIsUpToDate(
                        new GitFlowFailureInfo("Remote and local epic branches '{0}' diverge.",
                                "Rebase or merge the changes in local epic branch '{0}' first.", "'git rebase'"));
            }

            String baseBranch = gitEpicBranchBaseBranch(epicBranchName);

            if (pushRemote) {
                gitEnsureLocalAndRemoteBranchesAreSynchronized(baseBranch, new GitFlowFailureInfo(
                        "Local base branch '" + baseBranch + "' is ahead of remote branch. Pushing of the updated "
                                + "epic branch will create an inconsistent state in remote repository.",
                        "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to false in "
                                + "order to avoid inconsistent state in remote repository."),
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                        + "branch '" + baseBranch + "'"),
                        new GitFlowFailureInfo(
                                "Base branch '" + baseBranch + "' doesn't exist remotely. Pushing of the updated "
                                        + "epic branch will create an inconsistent state in remote repository.",
                                "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to "
                                        + "false in order to avoid inconsistent state in remote repository."));
            } else {
                gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                        + "branch '" + baseBranch + "'"));
            }

            try {
                gitMerge(baseBranch, false, true);
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved.\nIMPORTANT: "
                                + "be sure not to update the version in epic branch while resolving conflicts!\n"
                                + "After that, run 'mvn flow:epic-update' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:epic-update' to continue epic update process");
            }
        } else {
            if (!getPrompter().promptConfirmation(
                    "You have a merge in process on your current branch. If you run 'mvn flow:epic-update' before "
                            + "and merge had conflicts you can continue. In other case it is better to clarify the "
                            + "reason of merge in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of epic update aborted by user.", null);
            }
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge.\nGit error message:\n"
                                + StringUtils.trim(exc.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:epic-update' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-update' to continue epic update process");
            }
        }
        if (installProject) {
            mvnCleanInstall();
        }
        if (pushRemote) {
            gitPush(epicBranchName, false, false);
        }
    }

}
