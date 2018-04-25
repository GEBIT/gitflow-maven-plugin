//
// GitFlowEpicFinishMojo.java
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
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Finish epic branch. Merge it into base branch and remove it.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
@Mojo(name = "epic-finish", aggregator = true)
public class GitFlowEpicFinishMojo extends AbstractGitFlowEpicMojo {

    /** Whether to keep epic branch after finish. */
    @Parameter(property = "keepEpicBranch", defaultValue = "false")
    private boolean keepEpicBranch = false;

    /** Whether to skip calling Maven test goal before merging the branch. */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to allow fast forward merge of epic branch into development
     * branch.
     *
     * @since 2.0.0
     */
    @Parameter(property = "allowFF", defaultValue = "false")
    private boolean allowFF = false;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getLog().info("Starting feature finish process.");
        checkCentralBranchConfig();
        String baseBranch;
        String epicBranchName = gitMergeFromEpicBranchInProcess();
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
                        "Choose epic branch to finish", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:epic-finish' can be executed only on an epic branch.",
                                "Please switch to an epic branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the epic branch",
                                "'mvn flow:epic-finish' to run in interactive mode"));
                getLog().info("Finishing epic on selected epic branch: " + epicBranchName);
                gitEnsureLocalBranchIsUpToDateIfExists(epicBranchName,
                        new GitFlowFailureInfo("Remote and local epic branches '" + epicBranchName + "' diverge.",
                                "Rebase or merge the changes in local epic branch '" + epicBranchName + "' first.",
                                "'git rebase'"));
            } else {
                epicBranchName = currentBranch;
                getLog().info("Finishing epic on current epic branch: " + epicBranchName);
                gitEnsureCurrentLocalBranchIsUpToDate(
                        new GitFlowFailureInfo("Remote and local epic branches '{0}' diverge.",
                                "Rebase or merge the changes in local epic branch '{0}' first.", "'git rebase'"));
            }
            baseBranch = gitEpicBranchBaseBranch(epicBranchName);
            gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                    new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                            "Rebase the changes in local branch '" + baseBranch
                                    + "' and then include these changes in the epic branch '" + epicBranchName
                                    + "' in order to proceed.",
                            "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base branch '"
                                    + baseBranch + "'",
                            "'git checkout " + epicBranchName
                                    + "' and 'mvn flow:epic-update' to include these changes in the epic branch '"
                                    + epicBranchName + "'"));
            if (!hasCommitsExceptVersionChangeCommitOnEpicBranch(epicBranchName, baseBranch)) {
                throw new GitFlowFailureException("There are no real changes in epic branch '" + epicBranchName + "'.",
                        "Delete the epic branch or commit some changes first.",
                        "'mvn flow:epic-abort' to delete the epic branch",
                        "'git add' and 'git commit' to commit some changes into epic branch and "
                                + "'mvn flow:epic-finish' to run the epic finish again");
            }
            if (!gitIsAncestorBranch(baseBranch, epicBranchName)) {
                boolean confirmed = getPrompter().promptConfirmation(
                        "Base branch '" + baseBranch + "' has changes that are not yet included in epic branch '"
                                + epicBranchName + "'. If you continue it will be tryed to merge the changes. "
                                + "But it is strongly recomended to run 'mvn flow:epic-update' first and then "
                                + "run 'mvn flow:epic-finish' again. Are you sure you want to continue?",
                        false, false);
                if (!confirmed) {
                    throw new GitFlowFailureException(
                            "Base branch '" + baseBranch + "' has changes that are not yet included in epic branch '"
                                    + epicBranchName + "'.",
                            "Merge the changes into epic branch first in order to proceed.",
                            "'mvn flow:epic-update' to merge the changes into epic branch");
                }
            }
            if (!isOnEpicBranch) {
                gitCheckout(epicBranchName);
            }

            if (!skipTestProject) {
                mvnCleanVerify();
            }

            String epicVersion = getCurrentProjectVersion();
            getLog().info("Project version on epic branch: " + epicVersion);

            gitCheckout(baseBranch);

            String baseVersion = getCurrentProjectVersion();
            getLog().info("Project version on base branch: " + baseVersion);

            if (!tychoBuild) {
                if (!epicVersion.equals(baseVersion)) {
                    getLog().info("Reverting the project version on epic branch to the version on base branch.");
                    gitCheckout(epicBranchName);
                    String issueNumber = getEpicIssueNumber(epicBranchName);
                    String epicFinishMessage = substituteInEpicMessage(commitMessages.getEpicFinishMessage(),
                            issueNumber);
                    mvnSetVersions(baseVersion);
                    gitCommit(epicFinishMessage);

                    gitCheckout(baseBranch);
                } else {
                    getLog().info("Project version on epic branch is same as project version on base branch. "
                            + "Version update not needed.");
                }
            }

            try {
                gitMerge(epicBranchName, !allowFF);
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:epic-finish' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-finish' to continue epic finish process");
            }
        } else {
            if (!getPrompter().promptConfirmation(
                    "You have a merge in process on your current branch. If you run 'mvn flow:epic-finish' before "
                            + "and merge had conflicts you can continue. In other case it is better to clarify the "
                            + "reason of merge in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of epic finish aborted by user.", null);
            }
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge.\nGit error message:\n"
                                + StringUtils.trim(exc.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. "
                                + "After that, run 'mvn flow:epic-finish' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                        "'mvn flow:epic-finish' to continue epic finish process");
            }
            baseBranch = gitCurrentBranch();
        }

        if (installProject) {
            mvnCleanInstall();
        }

        if (!keepEpicBranch) {
            getLog().info("Removing local epic branch.");
            gitBranchDeleteForce(epicBranchName);

            if (pushRemote) {
                getLog().info("Removing remote epic branch.");
                gitBranchDeleteRemote(epicBranchName);
            }
        }

        if (pushRemote) {
            gitPush(baseBranch, false, false);
        }
        getLog().info("Epic finish process finished.");
    }

}
