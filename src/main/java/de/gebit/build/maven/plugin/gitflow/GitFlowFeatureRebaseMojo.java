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

import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Rebase a feature branch on top of development (upstream) branch.
 * <p>
 * Integrates the changes from the development branch (more specifically: from
 * the branch this feature has been based upon) into the feature branch. Also
 * handles the case where the version on the development branch is incremented
 * and adapts the feature branch accordingly. If conflicts occur on rebase, you
 * can fix the conflicts and continue the rebase process by executing
 * <code>flow:feature-rebase</code> again or you can abort rebase process by
 * executing <code>flow:feature-rebase-abort</code>.
 * <p>
 * This will either need he permission to force push (replace) the feature
 * branch or to delete the feature branch on the remote before pushing to be
 * able to replace it.
 * <p>
 * The <code>-N</code> option is needed for rare cases where a module in an
 * upstream project is removed, that is still used in the current project before
 * the rebase. If you don't specify -N yo will get errors like this:
 *
 * <pre>
 * mvn flow:feature-rebase
 * [INFO] Scanning for projects...
 * [ERROR] [ERROR] Some problems were encountered while processing the POMs:
 * [ERROR] 'dependencies.dependency.version' for de.gebit.xyz:abcd:jar is missing.
 * </pre>
 * <p>
 * Example:
 * <pre>
 * mvn -N flow:feature-rebase
 * </pre>
 *
 * @author Erwin Tratar
 * @since 1.3.1
 * @see GitFlowFeatureRebaseAbortMojo
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
        getMavenLog().info("Starting feature rebase process");
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
        String currentBranch = null;
        boolean continueOnCleanInstall = false;
        if (featureBranchName == null) {
            currentBranch = gitCurrentBranch();
            if (isFeatureBranch(currentBranch)) {
                String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("featureRebase.cleanInstall".equals(breakpoint)) {
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

                if (currentBranch == null) {
                    currentBranch = gitCurrentBranch();
                }
                if (!isFeatureBranch(currentBranch)) {
                    throw new GitFlowFailureException(
                            "'mvn flow:feature-rebase' can be executed only on a feature branch.",
                            "Please switch to a feature branch first.",
                            "'git checkout BRANCH' to switch to the feature branch");
                }

                featureBranchName = currentBranch;
                getLog().info("Rebasing feature on current feature branch: " + featureBranchName);
                gitEnsureCurrentLocalBranchIsUpToDate(
                        new GitFlowFailureInfo("Remote and local feature branches '{0}' diverge.",
                                "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));

                String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
                getMavenLog().info("Base branch of feature branch is '" + baseBranch + "'");
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
                                    "Integration branch '" + integrationBranch + "' is ahead of base branch '"
                                            + baseBranch
                                            + "', this indicates a severe error condition on your branches.",
                                    " Please consult a gitflow expert on how to fix this!");
                        }

                        getMavenLog().info("Using integration branch '" + integrationBranch
                                + "' as rebase point for feature branch");
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
                            "Updating is configured for merges, a later rebase will not be possible. Select if you "
                                    + "want to proceed with (m)erge or you want to use (r)ebase instead or (a)bort "
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

                rebaseFeatureBranchOnTopOfBaseBranch(featureBranchName, baseBranch, confirmedUpdateWithMerge);
            } else {
                continueFeatureRebase(confirmedUpdateWithMerge);
            }
            finilizeFeatureRebase(featureBranchName);
        } else {
            getMavenLog().info("Restart after failed feaure project installation detected");
            checkUncommittedChanges();
        }

        if (installProject) {
            getMavenLog().info("Installing the feature project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog()
                        .info("Feature rebase process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(featureBranchName, "breakpoint", "featureRebase.cleanInstall");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        "Failed to install the project on feature branch after rebase."
                                + (reason != null ? "\nReason: " + reason : ""),
                        "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                                + "'mvn flow:feature-rebase' again in order to continue.\n"
                                + "Do NOT push the feature branch!");
            }
        }
        gitRemoveBranchLocalConfig(featureBranchName, "breakpoint");

        if (pushRemote) {
            if (!confirmedUpdateWithMerge && deleteRemoteBranchOnRebase) {
                getMavenLog().info("Deleting remote feature branch to not run into non-fast-forward error");
                gitBranchDeleteRemote(featureBranchName);
            }
            getMavenLog().info("Pushing (forced) feature branch '" + featureBranchName + "' to remote repository");
            gitPush(featureBranchName, false, true);
        }
        getMavenLog().info("Feature rebase process finished");
    }

    private void rebaseFeatureBranchOnTopOfBaseBranch(String featureBranch, String baseBranch,
            boolean confirmedUpdateWithMerge)
            throws CommandLineException, GitFlowFailureException, MojoFailureException {
        if (confirmedUpdateWithMerge) {
            // merge development into feature
            try {
                getMavenLog().info("Merging (--no-ff) base branch '" + baseBranch + "' into feature branch '"
                        + featureBranch + "'...");
                gitMerge(baseBranch, true);
            } catch (MojoFailureException ex) {
                getMavenLog().info("Feature rebase process paused to resolve merge conflicts");
                throw new GitFlowFailureException(ex,
                        "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again.\nDo NOT run 'git merge --continue'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-rebase' to continue feature rebase process",
                        "'mvn flow:feature-rebase-abort' to abort feature rebase process");
            }
        } else {
            getMavenLog().info(
                    "Rebasing feature branch '" + featureBranch + "' on top of base branch '" + baseBranch + "'...");
            // rebase feature on top of development
            String baseCommit = gitBranchPoint(baseBranch, featureBranch);
            String versionChangeCommitOnBranch = gitVersionChangeCommitOnFeatureBranch(featureBranch, baseCommit);
            if (versionChangeCommitOnBranch != null) {
                getLog().info("First commit on feature branch is version change commit. Exclude it from rebase.");
                String tempFeatureBranch = createTempFeatureBranchName(featureBranch);
                getLog().info(
                        "Creating temporary branch with new version change commit to be used for feature rebase.");
                if (gitBranchExists(tempFeatureBranch)) {
                    gitBranchDeleteForce(tempFeatureBranch);
                }
                gitCreateAndCheckout(tempFeatureBranch, baseBranch);
                String currentVersion = getCurrentProjectVersion();
                String baseVersion = currentVersion;
                gitSetBranchLocalConfig(featureBranch, "newBaseVersion", baseVersion);
                String versionChangeCommit = null;
                String version = createFeatureVersion(baseVersion, featureBranch, baseBranch);
                if (!currentVersion.equals(version)) {
                    String prevBaseVersion = gitGetBranchCentralConfig(featureBranch, BranchConfigKeys.BASE_VERSION);
                    boolean sameBaseVersion = Objects.equals(baseVersion, prevBaseVersion);
                    getMavenLog().info(
                            "- setting feature version '" + version + "' for project on branch prepared for rebase");
                    mvnSetVersions(version, "On feature branch: ", featureBranch, sameBaseVersion, featureBranch);
                    String featureStartMessage = getFeatureStartCommitMessage(featureBranch);
                    gitCommit(featureStartMessage);
                    versionChangeCommit = getCurrentCommit();
                    gitSetBranchLocalConfig(featureBranch, "newStartCommitMessage", featureStartMessage);
                    gitSetBranchLocalConfig(featureBranch, "newVersionChangeCommit", versionChangeCommit);
                } else {
                    getLog().info("Project version for feature is same as base project version. "
                            + "Version update not needed.");
                }
                try {
                    gitRebaseOnto(tempFeatureBranch, versionChangeCommitOnBranch, featureBranch);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Feature rebase process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ex,
                            "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase' again.\n"
                                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process",
                            "'mvn flow:feature-rebase-abort' to abort feature rebase process");
                }
            } else {
                getLog().info("First commit on feature branch is not a version change commit. "
                        + "Rebase the whole feature branch.");
                try {
                    gitRebase(baseBranch);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Feature rebase process paused to resolve rebase conflicts");
                    throw new GitFlowFailureException(ex,
                            "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-rebase' again.\n"
                                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-rebase' to continue feature rebase process",
                            "'mvn flow:feature-rebase-abort' to abort feature rebase process");
                }
            }
        }
    }

    private String createFeatureVersion(String baseVersion, String featureBranchName, String baseBranchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Project version: " + baseVersion);
        String version = baseVersion;
        if (isEpicBranch(baseBranchName)) {
            version = removeEpicIssueFromVersion(version, baseBranchName);
            getLog().info("Removed epic issue number from project version: " + version);
        }
        String featureIssue = getFeatureIssueNumber(featureBranchName);
        getLog().info("Feature issue number read from central branch config: " + featureIssue);
        version = insertSuffixInVersion(version, featureIssue);
        getLog().info("Added feature issue number to project version: " + version);
        return version;
    }

    private String removeEpicIssueFromVersion(String version, String epicBranch)
            throws MojoFailureException, CommandLineException {
        String epicIssueNumber = getEpicIssueNumber(epicBranch);
        if (version.contains("-" + epicIssueNumber)) {
            return version.replaceFirst("-" + epicIssueNumber, "");
        }
        return version;
    }

    private void continueFeatureRebase(boolean confirmedUpdateWithMerge)
            throws GitFlowFailureException, CommandLineException {
        if (confirmedUpdateWithMerge) {
            // continue with commit
            if (!getPrompter().promptConfirmation(
                    "You have a merge in process on your current branch. If you run 'mvn flow:feature-rebase' "
                            + "before and merge had conflicts you can continue. In other case it is better to "
                            + "clarify the reason of merge in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of feature rebase aborted by user.", null);
            }
            getMavenLog().info("Continue merging base branch into feature branch...");
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Feature rebase process paused to resolve merge conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge.\nGit error message:\n"
                                + StringUtils.trim(exc.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again.\nDo NOT run 'git merge --continue'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-rebase' to continue feature rebase process",
                        "'mvn flow:feature-rebase-abort' to abort feature rebase process");
            }
        } else {
            // continue with the rebase
            if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                    + "If you run 'mvn flow:feature-rebase' before and rebase had conflicts you can "
                    + "continue. In other case it is better to clarify the reason of rebase in process. " + "Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of feature rebase aborted by user.", null);
            }
            getMavenLog().info("Continue rebasing feature branch on top of base branch...");
            try {
                gitRebaseContinueOrSkip();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Feature rebase process paused to resolve rebase conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after rebase.\nGit error message:\n"
                                + StringUtils.trim(exc.getMessage()),
                        "Fix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-rebase' to continue feature rebase process",
                        "'mvn flow:feature-rebase-abort' to abort feature rebase process");
            }
        }
    }

    private void finilizeFeatureRebase(String featureBranch) throws MojoFailureException, CommandLineException {
        String tempFeatureBranch = createTempFeatureBranchName(featureBranch);
        if (gitBranchExists(tempFeatureBranch)) {
            getLog().info("Deleting temporary branch used for feature rebase.");
            gitBranchDeleteForce(tempFeatureBranch);
        }
        String newBaseVersion = gitGetBranchLocalConfig(featureBranch, "newBaseVersion");
        if (newBaseVersion != null) {
            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(featureBranch, BranchConfigKeys.BASE_VERSION, newBaseVersion);
            branchConfigChanges.set(featureBranch, BranchConfigKeys.START_COMMIT_MESSAGE,
                    gitGetBranchLocalConfig(featureBranch, "newStartCommitMessage"));
            branchConfigChanges.set(featureBranch, BranchConfigKeys.VERSION_CHANGE_COMMIT,
                    gitGetBranchLocalConfig(featureBranch, "newVersionChangeCommit"));
            gitApplyBranchCentralConfigChanges(branchConfigChanges, "feature rebased on '" + featureBranch + "'");
        }
        gitRemoveBranchLocalConfig(featureBranch, "newBaseVersion");
        gitRemoveBranchLocalConfig(featureBranch, "newStartCommitMessage");
        gitRemoveBranchLocalConfig(featureBranch, "newVersionChangeCommit");
    }
}
