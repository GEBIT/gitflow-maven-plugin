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
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature update mojo. Will either rebase the feature branch on
 * the current development branch or merge the development branch into the
 * feature branch.
 *
 * @author Erwin Tratar
 * @since 1.3.1
 */
@Mojo(name = "feature-rebase", aggregator = true)
public class GitFlowFeatureRebaseMojo extends AbstractGitFlowMojo {

    /**
     * Controls whether a merge of the development branch instead of a rebase on
     * the development branch is performed.
     *
     * @since 1.3.0
     */
    @Parameter(property = "updateWithMerge", defaultValue = "false")
    private boolean updateWithMerge = false;

    /**
     * This property applies mainly to <code>feature-finish</code>, but if it is
     * set a merge at this point would make a later rebase impossible. So we use
     * this property to decide wheter a warning needs to be issued.
     *
     * @since 1.3.0
     */
    @Parameter(property = "rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

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
        String featureBranchName = updateWithMerge ? gitMergeIntoFeatureBranchInProcess() : gitRebaseBranchInProcess();
        if (featureBranchName == null) {
            // check uncommitted changes
            checkUncommittedChanges();

            List<String> branches = gitAllBranches(gitFlowConfig.getFeatureBranchPrefix());
            if (branches.isEmpty()) {
                throw new GitFlowFailureException("There are no feature branches in your repository.",
                        "Please start a feature first.", "'mvn flow:feature-start'");
            }
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
                        "Choose feature branch to rebase", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:feature-rebase' can be executed only on a feature branch.",
                                "Please switch to a feature branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the feature branch",
                                "'mvn flow:feature-rebase' to run in interactive mode"));
                gitEnsureLocalBranchIsUpToDateIfExists(featureBranchName, new GitFlowFailureInfo(
                        "Remote and local feature branches '" + featureBranchName + "' diverge.",
                        "Rebase or merge the changes in local feature branch '" + featureBranchName + "' first.",
                        "'git rebase'"));
                // git checkout feature/...
                gitCheckout(featureBranchName);
            } else {
                featureBranchName = currentBranch;
                gitEnsureCurrentLocalBranchIsUpToDate(
                        new GitFlowFailureInfo("Remote and local feature branches '{0}' diverge.",
                                "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));
            }

            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);

            gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                    new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                            "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                            "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base branch '"
                                    + baseBranch + "'"));

            if (updateWithMerge && rebaseWithoutVersionChange) {
                try {
                    final String reply = prompter.prompt(
                            "Updating is configured for merges, a later rebase will not be possible. Continue? (yes/no)",
                            "no");
                    if (reply == null || !reply.toLowerCase().equals("yes")) {
                        return;
                    }
                } catch (PrompterException e) {
                    getLog().error(e);
                    return;
                }
            }

            // merge in development
            try {
                gitMerge(baseBranch, !updateWithMerge, true);
            } catch (MojoFailureException ex) {
                // rebase conflict on first commit?
                final String featureStartMessage = substituteInMessage(commitMessages.getFeatureStartMessage(),
                        featureBranchName);
                String problem;
                String solutionProposal;
                if (updateWithMerge) {
                    problem = "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage());
                    solutionProposal = "Fix the merge conflicts and mark them as resolved. After that, run "
                            + "'mvn flow:feature-rebase' again. Do NOT run 'git merge --continue'.";
                } else {
                    problem = "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage());
                    solutionProposal = "Fix the rebase conflicts and mark them as resolved. After that, run "
                            + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.";
                }
                if (ex.getMessage().contains("Patch failed at 0001 " + featureStartMessage)) {
                    String featureName = featureBranchName.replaceFirst(gitFlowConfig.getFeatureBranchPrefix(), "");
                    String featureIssue = extractIssueNumberFromFeatureName(featureName);
                    // try automatic rebase
                    gitRebaseFeatureCommit(featureIssue);

                    // continue rebase
                    try {
                        gitRebaseContinue();
                    } catch (MojoFailureException exc) {
                        throw new GitFlowFailureException(exc, problem, solutionProposal,
                                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                        + "conflicts as resolved",
                                "'mvn flow:feature-rebase' to continue feature rebase process");
                    }
                } else {
                    throw new GitFlowFailureException(ex, problem, solutionProposal,
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process");
                }
            }
        } else {
            if (updateWithMerge) {
                // continue with commit
                gitCommitMerge();
            } else {
                // continue with the rebase
                gitRebaseContinue();
            }
        }

        if (installProject) {
            // mvn clean install
            mvnCleanInstall();
        }

        if (pushRemote) {
            if (!updateWithMerge && deleteRemoteBranchOnRebase) {
                // delete remote branch to not run into non-fast-forward error
                gitBranchDeleteRemote(featureBranchName);
            }
            gitPush(featureBranchName, false, true);
        }
    }
}
