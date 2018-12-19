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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort a release previously started with <code>flow:release-start</code>.
 * <p>
 * Switches back to development branch and deletes the release branch.
 *
 * @see GitFlowReleaseStartMojo
 * @see GitFlowReleaseAbortMojo
 * @since 1.3.1
 */
@Mojo(name = GitFlowReleaseAbortMojo.GOAL, aggregator = true)
public class GitFlowReleaseAbortMojo extends AbstractGitFlowMojo {

    static final String GOAL = "release-abort";

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        checkCentralBranchConfig();
        String currentBranch = gitCurrentBranch();
        String releaseBranch = abortReleaseWithConflictsIfMergeInProcess(currentBranch);
        if (releaseBranch == null) {
            if (isReleaseBranch(currentBranch)) {
                releaseBranch = currentBranch;
                String developmentBranch = gitGetBranchCentralConfig(releaseBranch, BranchConfigKeys.BASE_BRANCH);
                if (StringUtils.isBlank(developmentBranch)) {
                    developmentBranch = gitFlowConfig.getDevelopmentBranch();
                }
                if (!gitLocalOrRemoteBranchesExist(developmentBranch)) {
                    if (!gitFlowConfig.getDevelopmentBranch().equals(developmentBranch)) {
                        developmentBranch = gitFlowConfig.getDevelopmentBranch();
                        if (!gitLocalOrRemoteBranchesExist(developmentBranch)) {
                            developmentBranch = null;
                        }
                    } else {
                        developmentBranch = null;
                    }
                }
                if (developmentBranch != null) {
                    if (executeGitHasUncommitted()) {
                        boolean confirmed = getPrompter().promptConfirmation(
                                "You have some uncommitted files. If you continue any changes will be discarded. Continue?",
                                false, true);
                        if (!confirmed) {
                            throw new GitFlowFailureException(
                                    "You have aborted release-abort process because of uncommitted files.",
                                    "Commit or discard local changes in order to proceed.",
                                    "'git add' and 'git commit' to commit your changes",
                                    "'git reset --hard' to throw away your changes");
                        }
                        gitResetHard();
                    }
                    gitCheckout(developmentBranch);
                } else {
                    throw new GitFlowFailureException(
                            "No development branch found for current release branch. Cannot abort release.\n"
                                    + "This indicates a severe error condition on your branches.",
                            "Please configure correct development branch for the current release branch or consult a "
                                    + "gitflow expert on how to fix this.",
                            "'mvn flow:branch-config -DbranchName=" + releaseBranch
                                    + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to "
                                    + "configure correct development branch");
                }
            } else {
                List<String> releaseBranches = gitAllBranches(gitFlowConfig.getReleaseBranchPrefix());
                if (releaseBranches.isEmpty()) {
                    throw new GitFlowFailureException("There are no release branches in your repository.", null);
                }
                if (releaseBranches.size() > 1) {
                    throw new GitFlowFailureException(
                            "More than one release branch exists. Cannot abort release from non-release branch.",
                            "Please switch to a release branch first in order to proceed.",
                            "'git checkout INTERNAL' to switch to the release branch");
                }
                releaseBranch = releaseBranches.get(0);
            }
        }

        if (gitBranchExists(releaseBranch)) {
            gitBranchDeleteForce(releaseBranch);
        }

        if (pushRemote) {
            gitBranchDeleteRemote(releaseBranch);
        }
        gitRemoveAllBranchCentralConfigsForBranch(releaseBranch, "release '" + releaseBranch + "' aborted");
    }

    private String abortReleaseWithConflictsIfMergeInProcess(String currentBranch)
            throws MojoFailureException, CommandLineException {
        String mergeFromBranch = gitMergeFromBranchIfInProcess(gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getMaintenanceBranchPrefix() + "*", gitFlowConfig.getProductionBranch(),
                gitFlowConfig.getProductionBranch() + "-" + gitFlowConfig.getMaintenanceBranchPrefix() + "*",
                gitFlowConfig.getReleaseBranchPrefix() + "*",
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getMaintenanceBranchPrefix() + "*");
        if (mergeFromBranch != null) {
            return abortReleaseWithMergeConflicts(currentBranch, mergeFromBranch);
        }
        return null;
    }

    private String abortReleaseWithMergeConflicts(String mergeIntoBranch, String mergeFromBranch)
            throws MojoFailureException, CommandLineException {
        String developmentBranch;
        String productionBranch;
        String releaseBranch;
        if (isReleaseBranch(mergeFromBranch)) {
            releaseBranch = mergeFromBranch;
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
            } else if (isProductionBranch(mergeIntoBranch)) {
                productionBranch = mergeIntoBranch;
                developmentBranch = getDevelopmentBranchForProductionBranch(productionBranch);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isProductionBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = mergeFromBranch;
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isRemoteBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else {
            throw new GitFlowFailureException(
                    getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
        }
        if (!getPrompter().promptConfirmation(
                "You have a merge in process on your current branch.\n"
                        + "If you run 'mvn flow:release' or 'mvn flow:release-finish' before and merge had conflicts "
                        + "and now you want to abort this release then you can continue.\n"
                        + "In other case it is better to clarify the reason of merge in process. Continue?",
                true, true)) {
            throw new GitFlowFailureException("Continuation of release abort process aborted by user.", null);
        }
        if (StringUtils.isBlank(releaseBranch)) {
            throw new GitFlowFailureException(
                    "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                            + "'.\nInformation about release branch couldn't be found in git config.\n"
                            + "Release can't be automatically aborted.",
                    "Please consult a gitflow expert on how to fix this!");
        }
        String developmentCommitRef = gitGetBranchCentralConfig(releaseBranch,
                BranchConfigKeys.RELEASE_DEVELOPMENT_SAVEPOINT);
        if (StringUtils.isBlank(developmentCommitRef)) {
            throw new GitFlowFailureException(
                    "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                            + "'.\nReset point for development branch couldn't be found in git config.\n"
                            + "Release can't be automatically aborted.",
                    "Please consult a gitflow expert on how to fix this!");
        }
        gitMergeAbort();
        String tagName = gitGetBranchLocalConfig(releaseBranch, "releaseTag");
        if (StringUtils.isNotBlank(tagName) && gitTagExists(tagName)) {
            gitRemoveLocalTag(tagName);
        }
        gitUpdateRef(developmentBranch, developmentCommitRef);
        if (developmentBranch.equals(gitCurrentBranchOrCommit())) {
            gitResetHard();
        }
        gitCheckout(developmentBranch);
        String productionCommitRef = gitGetBranchCentralConfig(releaseBranch,
                BranchConfigKeys.RELEASE_PRODUCTION_SAVEPOINT);
        if (StringUtils.isNotBlank(productionCommitRef)) {
            gitUpdateRef(productionBranch, productionCommitRef);
        } else if (isUsingProductionBranch(developmentBranch, productionBranch) && gitBranchExists(productionBranch)) {
            // production branch was newly created -> can be deleted on abort
            gitBranchDeleteForce(productionBranch);
        }
        removeBranchConfigs(releaseBranch, developmentBranch);
        return releaseBranch;
    }

    private void removeBranchConfigs(String releaseBranch, String developmentBranch)
            throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(releaseBranch, "releaseTag");
        gitRemoveBranchLocalConfig(releaseBranch, "releaseCommit");
        gitRemoveBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
        gitRemoveBranchLocalConfig(developmentBranch, "releaseBranch");
    }

    private GitFlowFailureInfo getFailureMessageForUnsupportedMergeConflict(String mergeIntoBranch,
            String mergeFromBranch) {
        return new GitFlowFailureInfo(
                "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }
}
