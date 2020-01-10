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
 * Finish an epic branch.
 * <p>
 * Finish the implementation of the epic. Reverts the initial version commit,
 * merges everything back into the development branch (using fast forward
 * strategy if <code>flow.allowFF=true</code>), pushes development branch to
 * remote and finally deletes the epic branch.
 * <p>
 * Make sure your local development branch is not behind the remote, before
 * executing.
 * <p>
 * Example:
 * 
 * <pre>
 * mvn flow:epic-finish [-DbranchName=XYZ] [-Dflow.allowFF=true|false] [-Dflow.keepEpicBranch=true|false] [-Dflow.skipTestProject=true|false] [-Dflow.installProject=true|false]
 * </pre>
 *
 * @author Volodja
 * @see GitFlowEpicStartMojo
 * @see GitFlowEpicAbortMojo
 * @see GitFlowEpicUpdateMojo
 * @since 2.0.0
 */
@Mojo(name = GitFlowEpicFinishMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowEpicFinishMojo extends AbstractGitFlowEpicMojo {

    static final String GOAL = "epic-finish";

    /** Whether to keep epic branch after finish. */
    @Parameter(property = "flow.keepEpicBranch", defaultValue = "false")
    private boolean keepEpicBranch = false;

    /** Whether to skip calling Maven test goal before merging the branch. */
    @Parameter(property = "flow.skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to allow fast forward merge of epic branch into development branch.
     *
     * @since 2.0.0
     */
    @Parameter(property = "flow.allowFF", defaultValue = "false")
    private boolean allowFF = false;

    /**
     * The epic branch to be finished.
     *
     * @since 2.2.0
     */
    @Parameter(property = "branchName", readonly = true)
    protected String branchName;
    
    /**
     * Whether to call Maven install goal after epic finish. By default the
     * value of <code>installProject</code> parameter
     * (<code>flow.installProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.installProjectOnEpicFinish")
    private Boolean installProjectOnEpicFinish;
    
    /**
     * Whether to skip calling Maven test goal before merging the epic branch
     * into base branch. By default the value of <code>skipTestProject</code>
     * parameter (<code>flow.skipTestProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.skipTestProjectOnEpicFinish")
    private Boolean skipTestProjectOnEpicFinish;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic finish process");
        checkCentralBranchConfig();
        String baseBranch;
        String epicBranchLocalName = gitMergeFromEpicBranchInProcess();
        boolean continueOnCleanInstall = false;
        String tmpBaseBranch = null;
        if (epicBranchLocalName == null) {
            String currentBranch = gitCurrentBranch();
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null) {
                if ("epicFinish.cleanInstall".equals(breakpoint)) {
                    continueOnCleanInstall = true;
                    tmpBaseBranch = currentBranch;
                    epicBranchLocalName = gitGetBranchLocalConfig(currentBranch, "breakpointEpicBranch");
                }
            }
        }
        if (!continueOnCleanInstall) {
            if (epicBranchLocalName == null) {
                checkUncommittedChanges();

                String currentBranch = gitCurrentBranch();
                boolean isOnEpicBranch;
                if (StringUtils.isNotEmpty(branchName)) {
                    epicBranchLocalName = gitLocalRef(branchName);
                    if (!isEpicBranch(epicBranchLocalName)) {
                        throw new GitFlowFailureException(
                                "Branch '" + branchName + "' defined in 'branchName' property is not an epic branch.",
                                "Please define an epic branch in order to proceed.");
                    }
                    getLog().info("Finishing epic on specified epic branch: " + epicBranchLocalName);
                    isOnEpicBranch = epicBranchLocalName.equals(currentBranch);
                    if (!isOnEpicBranch && !gitLocalOrRemoteBranchesExist(epicBranchLocalName)) {
                        throw new GitFlowFailureException(createBranchNotExistingError(
                                "Epic branch '" + branchName + "' defined in 'branchName' property",
                                "Please define an existing epic branch in order to proceed."));
                    }
                } else {
                    List<String> branches = gitAllEpicBranches();
                    if (branches.isEmpty()) {
                        throw new GitFlowFailureException("There are no epic branches in your repository.",
                                "Please start an epic first.", "'mvn flow:epic-start'");
                    }
                    isOnEpicBranch = branches.contains(currentBranch);
                    if (!isOnEpicBranch) {
                        epicBranchLocalName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                                "Choose epic branch to finish", branches,
                                new GitFlowFailureInfo(
                                        "In non-interactive mode 'mvn flow:epic-finish' can be executed only on an epic branch.",
                                        "Please switch to an epic branch first or run in interactive mode.",
                                        "'git checkout BRANCH' to switch to the epic branch",
                                        "'mvn flow:epic-finish' to run in interactive mode"));
                        getLog().info("Finishing epic on selected epic branch: " + epicBranchLocalName);
                    } else {
                        epicBranchLocalName = currentBranch;
                        getLog().info("Finishing epic on current epic branch: " + epicBranchLocalName);
                    }
                }
                if (!isOnEpicBranch) {
                    gitEnsureLocalBranchIsUpToDateIfExists(epicBranchLocalName, new GitFlowFailureInfo(
                            "Remote and local epic branches '" + epicBranchLocalName + "' diverge.",
                            "Rebase or merge the changes in local epic branch '" + epicBranchLocalName + "' first.",
                            "'git rebase'"));
                } else {
                    gitEnsureCurrentLocalBranchIsUpToDate(
                            new GitFlowFailureInfo("Remote and local epic branches '{0}' diverge.",
                                    "Rebase or merge the changes in local epic branch '{0}' first.", "'git rebase'"));
                }
                
                baseBranch = gitEpicBranchBaseBranch(epicBranchLocalName);
                getMavenLog().info("Base branch of epic branch is '" + baseBranch + "'");
                gitEnsureLocalBranchIsUpToDateIfExists(baseBranch, new GitFlowFailureInfo(
                        "Remote and local base branches '" + baseBranch + "' diverge.",
                        "Rebase the changes in local branch '" + baseBranch
                                + "' and then include these changes in the epic branch '" + epicBranchLocalName
                                + "' in order to proceed.",
                        "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base branch '"
                                + baseBranch + "'",
                        "'git checkout " + epicBranchLocalName
                                + "' and 'mvn flow:epic-update' to include these changes in the epic branch '"
                                + epicBranchLocalName + "'"));
                if (!hasCommitsExceptVersionChangeCommitOnEpicBranch(epicBranchLocalName, baseBranch)) {
                    throw new GitFlowFailureException(
                            "There are no real changes in epic branch '" + epicBranchLocalName + "'.",
                            "Delete the epic branch or commit some changes first.",
                            "'mvn flow:epic-abort' to delete the epic branch",
                            "'git add' and 'git commit' to commit some changes into epic branch and "
                                    + "'mvn flow:epic-finish' to run the epic finish again");
                }
                if (!gitIsAncestorBranch(baseBranch, epicBranchLocalName)) {
                    boolean confirmed = getPrompter().promptConfirmation(
                            "Base branch '" + baseBranch + "' has changes that are not yet included in epic branch '"
                                    + epicBranchLocalName + "'. If you continue it will be tryed to merge the changes. "
                                    + "But it is strongly recomended to run 'mvn flow:epic-update' first and then "
                                    + "run 'mvn flow:epic-finish' again. Are you sure you want to continue?",
                            false, false);
                    if (!confirmed) {
                        throw new GitFlowFailureException("Base branch '" + baseBranch
                                + "' has changes that are not yet included in epic branch '" + epicBranchLocalName + "'.",
                                "Merge the changes into epic branch first in order to proceed.",
                                "'mvn flow:epic-update' to merge the changes into epic branch");
                    }
                }
                if (!isOnEpicBranch) {
                    getMavenLog().info("Switching to epic branch '" + epicBranchLocalName + "'");
                    gitCheckout(epicBranchLocalName);
                }

                if (!isSkipTestProject()) {
                    getMavenLog().info("Testing epic project before performing epic finish...");
                    mvnCleanVerify();
                }

                String epicVersion = getCurrentProjectVersion();
                getLog().info("Project version on epic branch: " + epicVersion);

                gitCheckout(baseBranch);

                String baseVersion = getCurrentProjectVersion();
                getLog().info("Project version on base branch: " + baseVersion);

                if (versionlessMode.needsVersionChangeCommit() && !tychoBuild) {
                    if (!epicVersion.equals(baseVersion)) {
                        getLog().info("Reverting the project version on epic branch to the version on base branch.");
                        gitCheckout(epicBranchLocalName);
                        String issueNumber = getEpicIssueNumber(epicBranchLocalName);
                        String epicFinishMessage = substituteWithIssueNumber(commitMessages.getEpicFinishMessage(),
                                issueNumber);
                        getMavenLog().info("Setting base version '" + baseVersion + "' for project on epic branch...");
                        mvnSetVersions(baseVersion, GitFlowAction.EPIC_FINISH, null, baseBranch);
                        gitCommit(epicFinishMessage);

                        gitCheckout(baseBranch);
                    } else {
                        getLog().info("Project version on epic branch is same as project version on base branch. "
                                + "Version update not needed.");
                    }
                }

                getMavenLog().info("Merging (" + (allowFF ? "--ff" : "--no-ff") + ") epic branch '" + epicBranchLocalName
                        + "' into base branch '" + baseBranch + "'...");
                try {
                    gitMerge(epicBranchLocalName, !allowFF);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Epic finish process paused to resolve merge conflicts");
                    throw new GitFlowFailureException(ex,
                            "Automatic merge failed.\n" + createMergeConflictDetails(baseBranch, epicBranchLocalName, ex),
                            "Fix the merge conflicts and mark them as resolved by using 'git add'. After that, run "
                                    + "'mvn flow:epic-finish' again.\nDo NOT run 'git merge --continue'.",
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
                baseBranch = gitCurrentBranch();
                getMavenLog().info("Continue merging epic branch into base branch...");
                try {
                    gitCommitMerge();
                } catch (MojoFailureException exc) {
                    getMavenLog().info("Epic finish process paused to resolve merge conflicts");
                    throw new GitFlowFailureException(exc,
                            "There are unresolved conflicts after merge.\n"
                                    + createMergeConflictDetails(baseBranch, epicBranchLocalName, exc),
                            "Fix the merge conflicts and mark them as resolved by using 'git add'. "
                                    + "After that, run 'mvn flow:epic-finish' again.\n"
                                    + "Do NOT run 'git merge --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:epic-finish' to continue epic finish process");
                }
            }
        } else {
            getMavenLog().info("Restart after failed project installation on base branch detected");
            baseBranch = tmpBaseBranch;
            checkUncommittedChanges();
        }

        if (isInstallProject()) {
            getMavenLog().info("Installing the project on base branch '" + baseBranch + "'...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Epic finish process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(baseBranch, "breakpoint", "epicFinish.cleanInstall");
                gitSetBranchLocalConfig(baseBranch, "breakpointEpicBranch", epicBranchLocalName);
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        FailureInfoHelper.installProjectFailure(GOAL, baseBranch, "epic finish", reason));
            }
        }
        gitRemoveBranchLocalConfig(baseBranch, "breakpoint");
        gitRemoveBranchLocalConfig(baseBranch, "breakpointEpicBranch");

        // first push modified branches
        if (pushRemote) {
            getMavenLog().info("Pushing base branch '" + baseBranch + "' to remote repository");
            gitPush(baseBranch, false, false);
        }

        // then delete if wanted
        if (!keepEpicBranch) {
            getMavenLog().info("Removing local epic branch '" + epicBranchLocalName + "'");
            gitBranchDeleteForce(epicBranchLocalName);

            if (pushRemote) {
                getMavenLog().info("Removing remote epic branch '" + epicBranchLocalName + "'");
                gitBranchDeleteRemote(epicBranchLocalName);
            }
            String epicName = epicBranchLocalName.substring(gitFlowConfig.getEpicBranchPrefix().length());
            gitRemoveAllBranchCentralConfigsForBranch(epicBranchLocalName, "epic '" + epicName + "' finished");
        }
        getMavenLog().info("Epic finish process finished");
    }

    @Override
    protected Boolean getIndividualInstallProjectConfig() {
        return installProjectOnEpicFinish;
    }
    
    @Override
    protected boolean getSkipTestProjectConfig() {
        return skipTestProject;
    }

    @Override
    protected Boolean getIndividualSkipTestProjectConfig() {
        return skipTestProjectOnEpicFinish;
    }
    
}
