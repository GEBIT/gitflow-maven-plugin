/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature rebase cleanup mojo. Will find out the matching
 * development branch and start a rebase --interactive to allow you to
 * reorder/squash/reword your commits.
 *
 * @author Erwin Tratar
 * @since 1.5.11
 */
@Mojo(name = "feature-rebase-cleanup", aggregator = true)
public class GitFlowFeatureCleanupMojo extends AbstractGitFlowFeatureMojo {

    private static final GitFlowFailureInfo ERROR_REBASE_CONFLICTS = new GitFlowFailureInfo(
            "Automatic rebase after interaction failed beacause of conflicts.",
            "Fix the rebase conflicts and mark them as resolved. After that, run "
                    + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
            "'git status' to check the conflicts, resolve the conflicts and "
                    + "'git add' to mark conflicts as resolved",
            "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
            "'git rebase --abort' to abort feature clean up process");

    private static final GitFlowFailureInfo ERROR_REBASE_PAUSED = new GitFlowFailureInfo(
            "Interactive rebase is paused.",
            "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                    + "Do NOT run 'git rebase --continue'.",
            "'git status' to check the conflicts",
            "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
            "'git rebase --abort' to abort feature clean up process");

    /**
     * Controls whether a merge of the development branch instead of a rebase on
     * the development branch is performed.
     *
     * @since 1.3.0
     */
    @Parameter(property = "updateWithMerge", defaultValue = "false")
    private boolean updateWithMerge = false;

    /**
     * If fast forward pushes on feature branches are not allowed, the remote
     * branch is deleted before pushing the rebased branch.
     *
     * @since 1.5.11
     */
    @Parameter(property = "deleteRemoteBranchOnRebase", defaultValue = "false")
    private boolean deleteRemoteBranchOnRebase = false;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature clean-up process");
        checkCentralBranchConfig();
        if (!settings.isInteractiveMode()) {
            throw new GitFlowFailureException(
                    "'mvn flow:feature-rebase-cleanup' can be executed only in interactive mode.",
                    "Please run in interactive mode.", "'mvn flow:feature-rebase-cleanup' to run in interactive mode");
        }
        String featureBranchName = gitInteractiveRebaseFeatureBranchInProcess();
        boolean continueOnCleanInstall = false;
        if (featureBranchName == null) {
            String currentBranch = gitCurrentBranch();
            if (isFeatureBranch(currentBranch)) {
                String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("featureCleanup.cleanInstall".equals(breakpoint)) {
                        continueOnCleanInstall = true;
                        featureBranchName = currentBranch;
                    }
                }
            }
        }
        if (!continueOnCleanInstall) {
            if (featureBranchName == null) {
                // check uncommitted changes
                checkUncommittedChanges();

                List<String> branches = gitAllFeatureBranches();
                if (branches.isEmpty()) {
                    throw new GitFlowFailureException("There are no feature branches in your repository.",
                            "Please start a feature first.", "'mvn flow:feature-start'");
                }
                String currentBranch = gitCurrentBranch();
                boolean isOnFeatureBranch = branches.contains(currentBranch);
                if (!isOnFeatureBranch) {
                    featureBranchName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                            "Choose feature branch to clean up", branches);
                    getLog().info("Cleaning up feature on selected feature branch: " + featureBranchName);
                    gitEnsureLocalBranchIsUpToDateIfExists(featureBranchName, new GitFlowFailureInfo(
                            "Remote and local feature branches '" + featureBranchName + "' diverge.",
                            "Rebase or merge the changes in local feature branch '" + featureBranchName + "' first.",
                            "'git rebase'"));
                    getMavenLog().info("Switching to feature branch '" + featureBranchName + "'");
                    gitCheckout(featureBranchName);
                } else {
                    featureBranchName = currentBranch;
                    getLog().info("Cleaning up feature on current feature branch: " + featureBranchName);
                    gitEnsureCurrentLocalBranchIsUpToDate(new GitFlowFailureInfo(
                            "Remote and local feature branches '{0}' diverge.",
                            "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));
                }

                getMavenLog().info("Determining start commit for rebase clean-up");
                String baseCommit = gitFeatureBranchBaseCommit(featureBranchName);
                getLog().info("Base commit (start point) of feature branch: " + baseCommit);
                String versionChangeCommitOnBranch = gitVersionChangeCommitOnFeatureBranch(featureBranchName,
                        baseCommit);
                String rebaseCommit;
                if (versionChangeCommitOnBranch != null) {
                    rebaseCommit = versionChangeCommitOnBranch;
                    getLog().info("First commit on feature branch is version change commit. "
                            + "Exclude it from interactive cleanup rebase.");
                } else {
                    rebaseCommit = baseCommit;
                    getLog().info("First commit on feature branch is not a version change commit. "
                            + "Use all feature commits while interactive cleanup rebase.");
                }

                getMavenLog().info("Starting interactive rebase...");
                InteractiveRebaseStatus rebaseStatus = gitRebaseInteractive(rebaseCommit);
                if (rebaseStatus == InteractiveRebaseStatus.PAUSED) {
                    getMavenLog().info("Feature clean-up process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ERROR_REBASE_PAUSED);
                } else if (rebaseStatus == InteractiveRebaseStatus.CONFLICT) {
                    getMavenLog().info("Feature clean-up process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ERROR_REBASE_CONFLICTS);
                }

            } else {
                if (!getPrompter()
                        .promptConfirmation("You have an interactive rebase in process on your current branch. "
                                + "If you run 'mvn flow:feature-rebase-cleanup' before and rebase was paused or had conflicts you "
                                + "can continue. In other case it is better to clarify the reason of rebase in process. "
                                + "Continue?", true, true)) {
                    throw new GitFlowFailureException("Continuation of feature clean up aborted by user.", null);
                }
                getMavenLog().info("Continue interactive rebase...");
                InteractiveRebaseResult rebaseResult = gitInteractiveRebaseContinue();
                switch (rebaseResult.getStatus()) {
                case PAUSED:
                    getMavenLog().info("Feature clean-up process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ERROR_REBASE_PAUSED);
                case CONFLICT:
                    getMavenLog().info("Feature clean-up process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ERROR_REBASE_CONFLICTS);
                case UNRESOLVED_CONFLICT:
                    getMavenLog().info("Feature clean-up process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(
                            "There are unresolved conflicts after rebase.\nGit error message:\n"
                                    + rebaseResult.getGitMessage(),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                    + "resolved",
                            "'mvn flow:feature-rebase-cleanup' to continue feature clean up process");
                case SUCCESS:
                default:
                    break;
                }
            }
        } else {
            getMavenLog().info("Restart after failed clean and install of feature project detected");
            checkUncommittedChanges();
        }
        if (installProject) {
            getMavenLog().info("Cleaning and installing feature project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog()
                        .info("Feature clean-up process paused on failed clean and install to fix project problems");
                gitSetBranchLocalConfig(featureBranchName, "breakpoint", "featureCleanup.cleanInstall");
                throw new GitFlowFailureException(e,
                        "Failed to execute 'mvn clean install' on the project on feature branch after cleanup.",
                        "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                                + "'mvn flow:feature-rebase-cleanup' again in order to continue.\n"
                                + "Do NOT push the feature branch!");
            }
        }
        gitRemoveBranchLocalConfig(featureBranchName, "breakpoint");

        if (pushRemote) {
            // delete remote branch to not run into non-fast-forward error
            if (deleteRemoteBranchOnRebase) {
                getMavenLog().info("Deleting remote feature branch to not run into non-fast-forward error");
                gitBranchDeleteRemote(featureBranchName);
            }
            getMavenLog().info("Pushing (forced) feature branch '" + featureBranchName + "' to remote repository");
            gitPush(featureBranchName, false, true);
        }
        getMavenLog().info("Feature clean-up process finished");
    }

}