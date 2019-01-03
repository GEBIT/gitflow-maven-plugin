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
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort the implementation of a feature.
 * <p>
 * Selects a feature branch for abortion, switches to the development branch and deletes the feature branch.
 *
 * @author Erwin Tratar
 * @see GitFlowFeatureStartMojo
 * @since 1.3.1
 */
@Mojo(name = GitFlowFeatureAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureAbortMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-abort";

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check if rebase in process
        getMavenLog().info("Starting feature abort process");
        checkCentralBranchConfig();
        String featureBranchName = gitRebaseFeatureBranchInProcess();
        if (featureBranchName != null) {
            throw new GitFlowFailureException(
                    "A rebase of the feature branch '" + featureBranchName
                            + "' is in process. Cannot abort feature now.",
                    "Finish rebase process first in order to proceed.");
        } else if (gitMergeInProcess()) {
            throw new GitFlowFailureException(
                    "A merge into the current branch is in process. Cannot abort feature now.",
                    "Finish merge process first in order to proceed.");
        }

        List<String> branches = gitAllFeatureBranches();
        if (branches.isEmpty()) {
            throw new GitFlowFailureException("There are no feature branches in your repository.", null);
        }
        // is the current branch a feature branch?
        String currentBranch = gitCurrentBranch();
        boolean isOnFeatureBranch = branches.contains(currentBranch);
        if (!isOnFeatureBranch) {
            featureBranchName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                    "Choose feature branch to abort", branches,
                    new GitFlowFailureInfo(
                            "In non-interactive mode 'mvn flow:feature-abort' can be executed only on a feature branch.",
                            "Please switch to a feature branch first or run in interactive mode.",
                            "'git checkout INTERNAL' to switch to the feature branch",
                            "'mvn flow:feature-abort' to run in interactive mode"));
            getLog().info("Aborting feature on selected feature branch: " + featureBranchName);
        } else {
            featureBranchName = currentBranch;
            getLog().info("Aborting feature on current feature branch: " + featureBranchName);
            if (executeGitHasUncommitted()) {
                boolean confirmed = getPrompter().promptConfirmation(
                        "You have some uncommitted files. If you continue any changes will be discarded. Continue?",
                        false,
                        new GitFlowFailureInfo("You have some uncommitted files.",
                                "Commit or discard local changes in order to proceed or run in interactive mode.",
                                "'git add' and 'git commit' to commit your changes",
                                "'git reset --hard' to throw away your changes",
                                "'mvn flow:feature-abort' to run in interactive mode"));
                if (!confirmed) {
                    throw new GitFlowFailureException("You have aborted feature-abort because of uncommitted files.",
                            "Commit or discard local changes in order to proceed.",
                            "'git add' and 'git commit' to commit your changes",
                            "'git reset --hard' to throw away your changes");
                }
            }
            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
            gitResetHard();
            getMavenLog().info("Switching to base branch '" + baseBranch + "'");
            gitCheckout(baseBranch);
        }

        if (gitBranchExists(featureBranchName)) {
            getMavenLog().info("Removing local feature branch '" + featureBranchName + "'");
            // git branch -D feature/...
            gitBranchDeleteForce(featureBranchName);
        }
        if (pushRemote) {
            getMavenLog().info("Removing remote feature branch '" + featureBranchName + "'");
            // delete the remote branch
            gitBranchDeleteRemote(featureBranchName);
        }
        String featureName = featureBranchName.substring(gitFlowConfig.getFeatureBranchPrefix().length());
        gitRemoveAllBranchCentralConfigsForBranch(featureBranchName, "feature '" + featureName + "' aborted");
        getMavenLog().info("Feature abort process finished");
    }
}
