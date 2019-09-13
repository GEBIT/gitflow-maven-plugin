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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort the implementation of a feature.
 * <p>
 * Selects a feature branch for abortion, switches to the development branch and
 * deletes the feature branch.
 *
 * @author Erwin Tratar
 * @see GitFlowFeatureStartMojo
 * @since 1.3.1
 */
@Mojo(name = GitFlowFeatureAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureAbortMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-abort";

    /**
     * The feature branch to be aborted.
     *
     * @since 2.2.0
     */
    @Parameter(property = "branchName", readonly = true)
    protected String branchName;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check if rebase in process
        getMavenLog().info("Starting feature abort process");
        checkCentralBranchConfig();
        String featureBranchLocalName = gitRebaseFeatureBranchInProcess();
        if (featureBranchLocalName != null) {
            throw new GitFlowFailureException(
                    "A rebase of the feature branch '" + featureBranchLocalName
                            + "' is in process. Cannot abort feature now.",
                    "Finish rebase process first in order to proceed.");
        } else if (gitMergeInProcess()) {
            throw new GitFlowFailureException(
                    "A merge into the current branch is in process. Cannot abort feature now.",
                    "Finish merge process first in order to proceed.");
        }

        boolean isOnFeatureBranch = false;
        BranchRef featureBranch;
        String currentBranch = gitCurrentBranch();

        if (StringUtils.isNotEmpty(branchName)) {
            featureBranchLocalName = gitLocalRef(branchName);
            if (!isFeatureBranch(featureBranchLocalName)) {
                throw new GitFlowFailureException(
                        "Branch '" + branchName + "' defined in 'branchName' property is not a feature branch.",
                        "Please define a feature branch in order to proceed.");
            }
            isOnFeatureBranch = featureBranchLocalName.equals(currentBranch);
            getLog().info("Aborting feature on specified feature branch: " + featureBranchLocalName);
            featureBranch = recognizeRef(branchName,
                    createBranchNotExistingError("Feature branch '" + branchName + "' defined in 'branchName' property",
                            "Please define an existing feature branch in order to proceed."));
        } else {
            List<String> branches = gitAllFeatureBranches();
            if (branches.isEmpty()) {
                throw new GitFlowFailureException("There are no feature branches in your repository.", null);
            }
            // is the current branch a feature branch?
            isOnFeatureBranch = branches.contains(currentBranch);
            if (!isOnFeatureBranch) {
                featureBranchLocalName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                        "Choose feature branch to abort", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:feature-abort' can be executed only on a feature branch.",
                                "Please switch to a feature branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the feature branch",
                                "'mvn flow:feature-abort' to run in interactive mode"));
                getLog().info("Aborting feature on selected feature branch: " + featureBranchLocalName);
                featureBranch = preferLocalRef(featureBranchLocalName);
            } else {
                featureBranchLocalName = currentBranch;
                getLog().info("Aborting feature on current feature branch: " + featureBranchLocalName);
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
                featureBranch = localRef(featureBranchLocalName);
            }
        }
        if (hasCommitsExceptVersionChangeCommitOnFeatureBranch(featureBranch)) {
            if (!getPrompter().promptConfirmation("You have commits on the feature branch.\n"
                    + "If you continue all these feature commits will be discarded. Continue?", false, true)) {
                throw new GitFlowFailureException("Feature abort process aborted by user.", null);
            }
        }
        if (isOnFeatureBranch) {
            String baseBranch = gitFeatureBranchBaseBranch(featureBranchLocalName);
            gitResetHard();
            getMavenLog().info("Switching to base branch '" + baseBranch + "'");
            gitCheckout(baseBranch);
        }

        if (gitBranchExists(featureBranchLocalName)) {
            getMavenLog().info("Removing local feature branch '" + featureBranchLocalName + "'");
            // git branch -D feature/...
            gitBranchDeleteForce(featureBranchLocalName);
        }
        if (pushRemote) {
            getMavenLog().info("Removing remote feature branch '" + featureBranchLocalName + "'");
            // delete the remote branch
            gitBranchDeleteRemote(featureBranchLocalName);
        }
        String featureName = featureBranchLocalName.substring(gitFlowConfig.getFeatureBranchPrefix().length());
        gitRemoveAllBranchCentralConfigsForBranch(featureBranchLocalName, "feature '" + featureName + "' aborted");
        getMavenLog().info("Feature abort process finished");
    }
}
