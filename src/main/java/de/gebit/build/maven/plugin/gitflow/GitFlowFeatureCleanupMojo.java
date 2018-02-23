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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature rebase cleanup mojo. Will find out the matching development branch and start a rebase --interactive
 * to allow you to reorder/squash/reword your commits.
 *
 * @author Erwin Tratar
 * @since 1.5.11
 */
@Mojo(name = "feature-rebase-cleanup", aggregator = true)
public class GitFlowFeatureCleanupMojo extends AbstractGitFlowMojo {

    /**
     * Controls whether a merge of the development branch instead of a rebase on the development branch is performed.
     * @since 1.3.0
     */
    @Parameter(property = "updateWithMerge", defaultValue = "false")
    private boolean updateWithMerge = false;

    /**
     * If fast forward pushes on feature branches are not allowed, the remote branch is deleted before pushing
     * the rebased branch.
     *
     * @since 1.5.11
     */
    @Parameter(property = "deleteRemoteBranchOnRebase", defaultValue = "false")
    private boolean deleteRemoteBranchOnRebase = false;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String featureBranchName = gitRebaseBranchInProcess();
            if (featureBranchName == null) {
                // check uncommitted changes
                checkUncommittedChanges();

                // git for-each-ref --format='%(refname:short)' refs/heads/feature/*
                final String featureBranches = gitFindBranches(
                        gitFlowConfig.getFeatureBranchPrefix(), false);

                if (StringUtils.isBlank(featureBranches)) {
                    throw new MojoFailureException("There are no feature branches.");
                }

                final String[] branches = featureBranches.split("\\r?\\n");

                // is the current branch a feature branch?
                String currentBranch = gitCurrentBranch();

                List<String> numberedList = new ArrayList<String>();
                StringBuilder str = new StringBuilder("Feature branches:")
                        .append(LS);
                for (int i = 0; i < branches.length; i++) {
                    str.append((i + 1) + ". " + branches[i] + LS);
                    numberedList.add(String.valueOf(i + 1));
                    if (branches[i].equals(currentBranch)) {
                        // we're on a feature branch, no need to ask
                        featureBranchName = currentBranch;
                        getLog().info("Current feature branch: " + featureBranchName);
                        break;
                    }
                }

                if (featureBranchName == null || StringUtils.isBlank(featureBranchName)) {
                    str.append("Choose feature branch to clean up");

                    String featureNumber = null;
                    try {
                        while (StringUtils.isBlank(featureNumber)) {
                            featureNumber = prompter.prompt(str.toString(),
                                    numberedList);
                        }
                    } catch (PrompterException e) {
                        getLog().error(e);
                    }

                    if (featureNumber != null) {
                        int num = Integer.parseInt(featureNumber);
                        featureBranchName = branches[num - 1];
                    }

                    if (StringUtils.isBlank(featureBranchName)) {
                        throw new MojoFailureException(
                                "Feature branch name to clean up is blank.");
                    }

                    // git checkout feature/...
                    gitCheckout(featureBranchName);
                }

                // fetch and check remote
                String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
                String baseCommit = gitFeatureBranchBaseCommit(featureBranchName, baseBranch);

                getLog().info("Rebasing upon " + baseBranch + " at " + baseCommit);

                if (fetchRemote) {
                    // fetch and compare both feature and development branch
                    gitFetchRemoteAndCompare(featureBranchName);
                    gitFetchRemoteAndResetIfNecessary(baseBranch);
                }

                String rebaseCommit = baseCommit;
                String firstCommitOnBranch = gitVersionChangeCommitOnBranch(featureBranchName, baseCommit);
                if (firstCommitOnBranch != null) {
                    rebaseCommit = firstCommitOnBranch;
                }

                if (!gitRebaseInteractive(rebaseCommit)) {
                    getLog().info("The rebase is paused, perform your changes and call flow:feature-rebase-cleanup again to continue.");
                    System.exit(1);
                }

            } else {
                if (updateWithMerge) {
                    // continue with commit
                    getLog().error("A merge is already in process, cannot cleanup now.");
                    return;
                } else {
                    // continue with the rebase
                    getLog().info("A rebase is in process. Assume the interactive rebase is resumed and continue...");
                    gitRebaseContinue();
                }
            }

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }

            if (pushRemote) {
                // delete remote branch to not run into non-fast-forward error
                if (deleteRemoteBranchOnRebase) {
                    gitBranchDeleteRemote(featureBranchName);
                }
                gitPush(featureBranchName, false, true);
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while executing external command.", e);
        }
    }
}
