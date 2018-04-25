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
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
public class GitFlowFeatureRebaseMojo extends AbstractGitFlowFeatureMojo {

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
        getLog().info("Starting feature rebase process.");
        checkCentralBranchConfig();
        boolean confirmedUpdateWithMerge = updateWithMerge;
        String featureBranchName = gitRebaseFeatureBranchInProcess();
        if (featureBranchName == null) {
            featureBranchName = gitMergeIntoFeatureBranchInProcess();
            if (featureBranchName != null) {
                confirmedUpdateWithMerge = true;
            }
        } else {
            confirmedUpdateWithMerge = false;
        }
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
                        "Choose feature branch to rebase", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:feature-rebase' can be executed only on a feature branch.",
                                "Please switch to a feature branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the feature branch",
                                "'mvn flow:feature-rebase' to run in interactive mode"));
                getLog().info("Rebasing feature on selected feature branch: " + featureBranchName);
                gitEnsureLocalBranchIsUpToDateIfExists(featureBranchName, new GitFlowFailureInfo(
                        "Remote and local feature branches '" + featureBranchName + "' diverge.",
                        "Rebase or merge the changes in local feature branch '" + featureBranchName + "' first.",
                        "'git rebase'"));
                // git checkout feature/...
                gitCheckout(featureBranchName);
            } else {
                featureBranchName = currentBranch;
                getLog().info("Rebasing feature on current feature branch: " + featureBranchName);
                gitEnsureCurrentLocalBranchIsUpToDate(
                        new GitFlowFailureInfo("Remote and local feature branches '{0}' diverge.",
                                "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));
            }

            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
            // use integration branch?
            String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
            gitEnsureLocalBranchIsUpToDateIfExists(integrationBranch,
                    new GitFlowFailureInfo(
                            "Local and remote integration branches '" + integrationBranch
                                    + "' diverge, this indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"));
            gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                    new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                            "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                            "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                    + "branch '" + baseBranch + "'"));
            if (gitBranchExists(integrationBranch)) {
                boolean useIntegrationBranch = true;
                if (!Objects.equals(getCurrentCommit(integrationBranch), getCurrentCommit(baseBranch))) {
                    useIntegrationBranch = getPrompter().promptConfirmation("The current commit on " + baseBranch
                            + " is not integrated. Rebase the feature branch on top of the last integrated commit ("
                            + integrationBranch + ")?", true, true);
                }
                if (useIntegrationBranch) {
                    if (!gitIsAncestorBranch(integrationBranch, baseBranch)) {
                        throw new GitFlowFailureException(
                                "Integration branch '" + integrationBranch + "' is ahead of base branch '" + baseBranch
                                        + "', this indicates a severe error condition on your branches.",
                                " Please consult a gitflow expert on how to fix this!");
                    }

                    getLog().info(
                            "Using integration branch '" + integrationBranch + "' as rebase point for feature branch.");
                    baseBranch = integrationBranch;
                }
            }
            if (pushRemote) {
                gitEnsureLocalAndRemoteBranchesAreSynchronized(baseBranch, new GitFlowFailureInfo(
                        "Local base branch '" + baseBranch + "' is ahead of remote branch. Pushing of the rebased "
                                + "feature branch will create an inconsistent state in remote repository.",
                        "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to false in "
                                + "order to avoid inconsistent state in remote repository."),
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                        + "branch '" + baseBranch + "'"),
                        new GitFlowFailureInfo(
                                "Base branch '" + baseBranch + "' doesn't exist remotely. Pushing of the rebased "
                                        + "feature branch will create an inconsistent state in remote repository.",
                                "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to "
                                        + "false in order to avoid inconsistent state in remote repository."));
            }
            if (updateWithMerge && rebaseWithoutVersionChange) {
                String answer = getPrompter().promptSelection(
                        "Updating is configured for merges, a later rebase will not be possible. "
                                + "Select if you want to proceed with (m)erge or you want to use (r)ebase instead or (a)bort "
                                + "the process.",
                        new String[] { "m", "r", "a" }, "a",
                        new GitFlowFailureInfo(
                                "Updating is configured for merges, a later rebase will not be possible.",
                                "Run feature-rebase in interactive mode",
                                "'mvn flow:feature-rebase' to run in interactive mode"));
                if ("m".equalsIgnoreCase(answer)) {
                    confirmedUpdateWithMerge = true;
                } else if ("r".equalsIgnoreCase(answer)) {
                    confirmedUpdateWithMerge = false;
                } else {
                    throw new GitFlowFailureException("Feature rebase aborted by user.", null);
                }
            }

            if (confirmedUpdateWithMerge) {
                // merge development into feature
                try {
                    gitMerge(baseBranch, true);
                } catch (MojoFailureException ex) {
                    throw new GitFlowFailureException(ex,
                            "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                            "Fix the merge conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase' again. Do NOT run 'git merge --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process");
                }
            } else {
                // rebase feature on top of development
                try {
                    gitRebase(baseBranch);
                } catch (MojoFailureException ex) {
                    // rebase conflict on first commit?
                    String featureStartMessage = getFeatureStartCommitMessage(featureBranchName);
                    if (featureStartMessage != null
                            && ex.getMessage().contains("Patch failed at 0001 " + featureStartMessage)) {
                        getLog().info(
                                "Rebase failed on feature version change commit. Trying automatically to resolve conflict.");
                        // try automatic rebase
                        gitResolveConflictOnSetFeatureVersionCommit(featureBranchName, baseBranch);
                        getLog().info("Continue rebase.");
                        // continue rebase
                        try {
                            gitRebaseContinue();
                        } catch (MojoFailureException exc) {
                            throw new GitFlowFailureException(exc,
                                    "Automatic rebase failed.\nGit error message:\n"
                                            + StringUtils.trim(ex.getMessage()),
                                    "Fix the rebase conflicts and mark them as resolved. After that, run "
                                            + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.",
                                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                            + "conflicts as resolved",
                                    "'mvn flow:feature-rebase' to continue feature rebase process");
                        }
                    } else {
                        throw new GitFlowFailureException(ex,
                                "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                                "Fix the rebase conflicts and mark them as resolved. After that, run "
                                        + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.",
                                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                        + "conflicts as resolved",
                                "'mvn flow:feature-rebase' to continue feature rebase process");
                    }
                }
            }
        } else {
            if (confirmedUpdateWithMerge) {
                // continue with commit
                if (!getPrompter().promptConfirmation(
                        "You have a merge in process on your current branch. If you run 'mvn flow:feature-rebase' before "
                                + "and merge had conflicts you can continue. In other case it is better to clarify the "
                                + "reason of merge in process. Continue?",
                        true, true)) {
                    throw new GitFlowFailureException("Continuation of feature rebase aborted by user.", null);
                }
                try {
                    gitCommitMerge();
                } catch (MojoFailureException exc) {
                    throw new GitFlowFailureException(exc,
                            "There are unresolved conflicts after merge.\nGit error message:\n"
                                    + StringUtils.trim(exc.getMessage()),
                            "Fix the merge conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase' again. Do NOT run 'git merge --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process");
                }
            } else {
                // continue with the rebase
                if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                        + "If you run 'mvn flow:feature-rebase' before and rebase had conflicts you can "
                        + "continue. In other case it is better to clarify the reason of rebase in process. "
                        + "Continue?", true, true)) {
                    throw new GitFlowFailureException("Continuation of feature rebase aborted by user.", null);
                }
                try {
                    gitRebaseContinue();
                } catch (MojoFailureException exc) {
                    throw new GitFlowFailureException(exc,
                            "There are unresolved conflicts after rebase.\nGit error message:\n"
                                    + StringUtils.trim(exc.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process");
                }
            }
        }

        if (installProject) {
            // mvn clean install
            mvnCleanInstall();
        }

        if (pushRemote) {
            if (!confirmedUpdateWithMerge && deleteRemoteBranchOnRebase) {
                getLog().info("Deleting remote feature branch to not run into non-fast-forward error");
                // delete remote branch to not run into non-fast-forward error
                gitBranchDeleteRemote(featureBranchName);
            }
            gitPush(featureBranchName, false, true);
        }
        getLog().info("Feature rebase process finished.");
    }

    /**
     * Update initial version commit to new version on development branch.
     *
     * @param featureBranchName
     *            the feature branch name
     * @param baseBranchName
     *            the base branch name
     */
    private void gitResolveConflictOnSetFeatureVersionCommit(String featureBranchName, String baseBranchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Using (checkout) project version from base branch '" + baseBranchName + "'");
        gitCheckoutOurs();
        String currentVersion = getCurrentProjectVersion();
        getLog().info("Project version: " + currentVersion);
        String version = currentVersion;
        if (isEpicBranch(baseBranchName)) {
            version = removeEpicIssueFromVersion(version, baseBranchName);
            getLog().info("Removed epic issue number from project version: " + version);
        }
        String featureIssue = getFeatureIssueNumber(featureBranchName);
        getLog().info("Feature issue number read from central branch config: " + featureIssue);
        version = insertSuffixInVersion(version, featureIssue);
        getLog().info("Added feature issue number to project version: " + version);
        if (!currentVersion.equals(version)) {
            mvnSetVersions(version);
            gitAddWithUpdateIndex();
        } else {
            getLog().info("New project version for feature is same as project version from base branch. "
                    + "Version update not needed.");
        }
    }

    private String removeEpicIssueFromVersion(String version, String epicBranch)
            throws MojoFailureException, CommandLineException {
        String epicIssueNumber = getEpicIssueNumber(epicBranch);
        if (version.contains("-" + epicIssueNumber)) {
            return version.replaceFirst("-" + epicIssueNumber, "");
        }
        return version;
    }
}
