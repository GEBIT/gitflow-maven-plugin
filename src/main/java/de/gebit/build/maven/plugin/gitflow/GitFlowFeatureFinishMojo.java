/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature finish mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "feature-finish", aggregator = true)
public class GitFlowFeatureFinishMojo extends AbstractGitFlowMojo {

    /** Whether to keep feature branch after finish. */
    @Parameter(property = "keepFeatureBranch", defaultValue = "false")
    private boolean keepFeatureBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * You can try a rebase of the feature branch skipping the initial commit
     * that update the pom versions just before finishing a feature. The
     * operation will peform a rebase, which may not finish successfully. You
     * can make your changes and run feature-finish again in that case. <br>
     * Note: problems arise if you're modifying the poms near the version
     * number. You will need to fix those conflicts before running
     * feature-finish again, as otherwise the pom will be invalid and the
     * process cannot be started. If you cannot fix the pom into a working state
     * with the current commit you can manually issue a
     * <code>git rebase --continue</code>.
     *
     * @since 1.3.0
     */
    @Parameter(property = "rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check if rebase in process
        String baseBranch;
        String featureBranchName = gitMergeFromFeatureBranchInProcess();
        if (featureBranchName == null) {
            featureBranchName = gitRebaseBranchInProcess();
            if (featureBranchName == null) {
                // check uncommitted changes
                checkUncommittedChanges();

                List<String> branches = gitAllBranches(gitFlowConfig.getFeatureBranchPrefix());
                if (branches.isEmpty()) {
                    throw new GitFlowFailureException("There are no feature branches in your repository.",
                            "Please start a feature first.", "'mvn flow:feature-start'");
                }
                // is the current branch a feature branch?
                String currentBranch = gitCurrentBranch();
                boolean isOnFeatureBranch = false;
                for (String branch : branches) {
                    if (branch.equals(currentBranch)) {
                        // we're on a feature branch, no need to ask
                        isOnFeatureBranch = true;
                        getLog().info("Current feature branch: " + currentBranch);
                        break;
                    }
                }

                if (!isOnFeatureBranch) {
                    featureBranchName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                            "Choose feature branch to finish", branches,
                            new GitFlowFailureInfo(
                                    "In non-interactive mode 'mvn flow:feature-finish' can be executed only on a feature branch.",
                                    "Please switch to a feature branch first or run in interactive mode.",
                                    "'git checkout BRANCH' to switch to the feature branch",
                                    "'mvn flow:feature-finish' to run in interactive mode"));

                    // git checkout feature/...
                    gitEnsureLocalBranchIsUpToDateIfExists(featureBranchName, new GitFlowFailureInfo(
                            "Remote and local feature branches '" + featureBranchName + "' diverge.",
                            "Rebase or merge the changes in local feature branch '" + featureBranchName + "' first.",
                            "'git rebase'"));
                } else {
                    featureBranchName = currentBranch;
                    gitEnsureCurrentLocalBranchIsUpToDate(new GitFlowFailureInfo(
                            "Remote and local feature branches '{0}' diverge.",
                            "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));
                }
                baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
                gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '"
                                        + baseBranch + "' and then include these changes in the feature branch '"
                                        + featureBranchName + "' in order to proceed.",
                                "'git checkout " + baseBranch
                                        + "' and 'git rebase' to rebase the changes in base branch '" + baseBranch
                                        + "'",
                                "'git checkout " + featureBranchName
                                        + "' and 'mvn flow:feature-rebase' to include these changes in the feature branch '"
                                        + featureBranchName + "'"));
                if (!hasCommitsExceptVersionChangeCommitOnBranch(featureBranchName, baseBranch)) {
                    throw new GitFlowFailureException(
                            "There are no real changes in feature branch '" + featureBranchName + "'.",
                            "Delete the feature branch or commit some changes first.",
                            "'mvn flow:feature-abort' to delete the feature branch",
                            "'git add' and 'git commit' to commit some changes into feature branch and "
                                    + "'mvn flow:feature-finish' to run the feature finish again");
                }
                if (!gitIsAncestorBranch(baseBranch, featureBranchName)) {
                    boolean confirmed = getPrompter().promptConfirmation(
                            "Base branch '" + baseBranch + "' has changes that are not yet included in feature branch "
                                    + "'" + featureBranchName
                                    + "'. If you continue it will be tryed to merge the changes. "
                                    + "But it is strongly recomended to run 'mvn flow:feature-rebase' first and then "
                                    + "run 'mvn flow:feature-finish' again. Are you sure want to continue?",
                            false, false);
                    if (!confirmed) {
                        throw new GitFlowFailureException(
                                "Base branch '" + baseBranch
                                        + "' has changes that are not yet included in feature branch '"
                                        + featureBranchName + "'.",
                                "Rebase the feature branch first in order to proceed.",
                                "'mvn flow:feature-rebase' to rebase the feature branch");
                    }
                }
                if (!isOnFeatureBranch) {
                    gitCheckout(featureBranchName);
                }

                if (!skipTestProject) {
                    // mvn clean test
                    mvnCleanTest();
                }

                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();

                // git checkout develop after fetch and check remote
                gitCheckout(baseBranch);
                boolean rebased = false;
                if (rebaseWithoutVersionChange) {
                    String branchPoint = gitBranchPoint(featureBranchName, baseBranch);
                    String firstCommitOnBranch = gitVersionChangeCommitOnBranch(featureBranchName, branchPoint);
                    getLog().debug(
                            "branch point is " + branchPoint + ", version change commit is " + firstCommitOnBranch);
                    if (firstCommitOnBranch != null) {
                        rebased = gitTryRebaseWithoutVersionChange(featureBranchName, branchPoint, firstCommitOnBranch);
                    }
                }
                if (!rebased) {
                    // rebase not configured or not possible, then manually
                    // revert the version
                    gitCheckout(featureBranchName);
                    String featureName = featureBranchName.replaceFirst(gitFlowConfig.getFeatureBranchPrefix(), "");
                    if (currentVersion.contains("-" + featureName)) {
                        final String version = currentVersion.replaceFirst("-" + featureName, "");
                        // mvn versions:set -DnewVersion=...
                        // -DgenerateBackupPoms=false
                        mvnSetVersions(version);

                        String featureFinishMessage = substituteInMessage(commitMessages.getFeatureFinishMessage(),
                                featureBranchName);
                        // git commit -a -m updating versions for development
                        // branch
                        gitCommit(featureFinishMessage);
                    }
                }
            } else {
                // continue with the rebase
                try {
                    gitRebaseContinue();
                } catch (MojoFailureException exc) {
                    throw new GitFlowFailureException(exc,
                            "There are unresolved conflicts after rebase.\nGit error message:\n"
                                    + StringUtils.trim(exc.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. "
                                    + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git rebase --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                            "'mvn flow:feature-finish' to continue feature finish process");
                }
            }

            // git checkout develop
            baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
            gitCheckout(baseBranch);

            // git merge --no-ff feature/...
            try {
                gitMergeNoff(featureBranchName);
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process");
            }
        } else {
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge.\nGit error message:\n"
                                + StringUtils.trim(exc.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. "
                                + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process");
            }
            baseBranch = gitCurrentBranch();
        }

        if (installProject) {
            // mvn clean install
            mvnCleanInstall();
        }

        if (!keepFeatureBranch) {
            // git branch -D feature/...
            gitBranchDeleteForce(featureBranchName);

            // delete the remote branch
            if (pushRemote) {
                gitBranchDeleteRemote(featureBranchName);
            }
        }

        if (pushRemote) {
            gitPush(baseBranch, false, false);
        }
    }

}
