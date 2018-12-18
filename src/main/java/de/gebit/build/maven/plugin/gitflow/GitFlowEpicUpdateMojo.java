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
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Merge the development branch into the epic branch.
 * <p>
 * Integrates the changes from development branch into the epic branch. If
 * conflicts occur on merge, you can fix the conflicts and continue merge
 * process by executing <code>flow:epic-update</code> again. We need to use a
 * merge here, as the epic branch must not be rebased due to several feature
 * branches being branched off further.
 *
 * @author Volodymyr Medvid
 * @see GitFlowEpicStartMojo
 * @since 2.0.0
 */
@Mojo(name = "epic-update", aggregator = true)
public class GitFlowEpicUpdateMojo extends AbstractGitFlowEpicMojo {

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic update process");
        checkCentralBranchConfig();
        String epicBranchName = gitMergeIntoEpicBranchInProcess();
        boolean continueOnCleanInstall = false;
        if (epicBranchName == null) {
            String currentBranch = gitCurrentBranch();
            if (isEpicBranch(currentBranch)) {
                String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("epicUpdate.cleanInstall".equals(breakpoint)) {
                        continueOnCleanInstall = true;
                        epicBranchName = currentBranch;
                    }
                }
            }
        }
        if (!continueOnCleanInstall) {
            if (epicBranchName == null) {
                checkUncommittedChanges();

                List<String> branches = gitAllEpicBranches();
                if (branches.isEmpty()) {
                    throw new GitFlowFailureException("There are no epic branches in your repository.",
                            "Please start an epic first.", "'mvn flow:epic-start'");
                }
                String currentBranch = gitCurrentBranch();
                boolean isOnEpicBranch = branches.contains(currentBranch);
                if (!isOnEpicBranch) {
                    epicBranchName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                            "Choose epic branch to update", branches,
                            new GitFlowFailureInfo(
                                    "In non-interactive mode 'mvn flow:epic-update' can be executed only on an epic branch.",
                                    "Please switch to an epic branch first or run in interactive mode.",
                                    "'git checkout INTERNAL' to switch to the epic branch",
                                    "'mvn flow:epic-update' to run in interactive mode"));
                    getLog().info("Updating epic on selected epic branch: " + epicBranchName);
                    gitEnsureLocalBranchIsUpToDateIfExists(epicBranchName,
                            new GitFlowFailureInfo("Remote and local epic branches '" + epicBranchName + "' diverge.",
                                    "Rebase or merge the changes in local epic branch '" + epicBranchName + "' first.",
                                    "'git rebase'"));
                    getMavenLog().info("Switching to epic branch '" + epicBranchName + "'");
                    gitCheckout(epicBranchName);
                } else {
                    epicBranchName = currentBranch;
                    getLog().info("Updating epic on current epic branch: " + epicBranchName);
                    gitEnsureCurrentLocalBranchIsUpToDate(
                            new GitFlowFailureInfo("Remote and local epic branches '{0}' diverge.",
                                    "Rebase or merge the changes in local epic branch '{0}' first.", "'git rebase'"));
                }

                String baseBranch = gitEpicBranchBaseBranch(epicBranchName);
                getMavenLog().info("Base branch of epic branch is '" + baseBranch + "'");

                // use integration branch?
                String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
                gitEnsureLocalBranchIsUpToDateIfExists(integrationBranch,
                        new GitFlowFailureInfo(
                                "Local and remote integration branches '" + integrationBranch
                                        + "' diverge, this indicates a severe error condition on your branches.",
                                "Please consult a gitflow expert on how to fix this!"));
                gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                        + "branch '" + baseBranch + "'"));
                if (gitBranchExists(integrationBranch)) {
                    boolean useIntegrationBranch = true;
                    if (!Objects.equals(getCurrentCommit(integrationBranch), getCurrentCommit(baseBranch))) {
                        useIntegrationBranch = getPrompter().promptConfirmation("The current commit on " + baseBranch
                                + " is not integrated. Update epic branch to the last integrated commit ("
                                + integrationBranch + ")?", true, true);
                    }
                    if (useIntegrationBranch) {
                        if (!gitIsAncestorBranch(integrationBranch, baseBranch)) {
                            throw new GitFlowFailureException(
                                    "Integration branch '" + integrationBranch + "' is ahead of base branch '"
                                            + baseBranch
                                            + "', this indicates a severe error condition on your branches.",
                                    " Please consult a gitflow expert on how to fix this!");
                        }

                        getMavenLog()
                                .info("Using integration branch '" + integrationBranch + "' to update the epic branch");
                        baseBranch = integrationBranch;
                    }
                }

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
                }

                try {
                    getMavenLog().info("Merging (--no-ff) base branch '" + baseBranch + "' into epic branch '"
                            + epicBranchName + "'...");
                    gitMerge(baseBranch, true);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Epic update process paused to resolve merge conflicts");
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
                getMavenLog().info("Continue merging base branch into epic branch...");
                try {
                    gitCommitMerge();
                } catch (MojoFailureException exc) {
                    getMavenLog().info("Epic update process paused to resolve merge conflicts");
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
        } else {
            getMavenLog().info("Restart after failed epic project installation detected");
            checkUncommittedChanges();
        }
        if (installProject) {
            getMavenLog().info("Installing the epic project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Epic update process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(epicBranchName, "breakpoint", "epicUpdate.cleanInstall");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        "Failed to install the project on epic branch after epic update."
                                + (reason != null ? "\nReason: " + reason : ""),
                        "Please solve the problems on project, add and commit your changes and run "
                                + "'mvn flow:epic-update' again in order to continue.\n"
                                + "Do NOT push the feature branch!\nAlternatively you can use property "
                                + "'-Dflow.installProject=false' while running "
                                + "'mvn flow:epic-update' to skip the project installation.",
                        "'git add' and 'git commit' to commit your changes",
                        "'mvn flow:epic-update' to continue epic update process after problem solving",
                        "or 'mvn flow:epic-update -Dflow.installProject=false' to continue by skipping the project "
                                + "installation");
            }
        }
        gitRemoveBranchLocalConfig(epicBranchName, "breakpoint");
        if (pushRemote) {
            getMavenLog().info("Pushing epic branch '" + epicBranchName + "' to remote repository");
            gitPush(epicBranchName, false, false);
        }
        getMavenLog().info("Epic update process finished");
    }

}
