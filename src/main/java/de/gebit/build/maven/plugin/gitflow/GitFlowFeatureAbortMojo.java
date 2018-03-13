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
 * The git flow feature abort mojo.
 *
 * @author Erwin Tratar
 * @since 1.3.1
 */
@Mojo(name = "feature-abort", aggregator = true)
public class GitFlowFeatureAbortMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check if rebase in process
        String featureBranchName = gitRebaseBranchInProcess();
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
                    "Choose feature branch to abort", branches,
                    new GitFlowFailureInfo(
                            "In non-interactive mode 'mvn flow:feature-abort' can be executed only on a feature branch.",
                            "Please switch to a feature branch first or run in interactive mode.",
                            "'git checkout BRANCH' to switch to the feature branch",
                            "'mvn flow:feature-abort' to run in interactive mode"));
        } else {
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
            featureBranchName = currentBranch;
            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
            gitEnsureLocalBranchExists(baseBranch);
            gitResetHard();
            gitCheckout(baseBranch);
        }

        if (gitBranchExists(featureBranchName)) {
            // git branch -D feature/...
            gitBranchDeleteForce(featureBranchName);
        }
        if (pushRemote) {
            // delete the remote branch
            gitBranchDeleteRemote(featureBranchName);
        }
    }
}
