//
// AbstractGitFlowReleaseMojo.java
//
// Copyright (C) 2017
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Common release steps that are used for release-start, release-finish and
 * release
 *
 * @author Erwin
 * @since 1.4.2
 */
public abstract class AbstractGitFlowReleaseMojo extends AbstractGitFlowMojo {

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isSkipTestProject();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isSkipDeployProject();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isSkipTag();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isKeepBranch();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isReleaseMergeNoFF();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isReleaseMergeProductionNoFF();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isDetachReleaseCommit();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isSameBranchName();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isPushReleaseBranch();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract String[] getReleaseGoals();

    /**
     * Get a replacement for the 'deploy' goal/phase when
     * {@link #isSkipDeployProject()} is activated.
     *
     * @since 1.5.10
     */
    protected abstract String getDeployReplacement();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract String getReleaseVersion();

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract String getDevelopmentVersion();

    /**
     * The name of maven goal that is being executed currently.
     */
    protected abstract String getCurrentGoal();

    protected boolean isInstallProject() {
        return installProject;
    }

    /**
     * Perfom the steps to start a release. Create a release branch and sets the
     * version
     *
     * @return <code>true</code> if the release is on a support/maintenance
     *         branch
     * @throws MojoExecutionException
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean releaseStart() throws MojoFailureException, CommandLineException {
        // check snapshots dependencies
        if (!allowSnapshots && hasProjectSnapshotDependencies()) {
            throw new GitFlowFailureException(
                    "There are some SNAPSHOT dependencies in the project. Release cannot be started.",
                    "Change the dependencies or ignore with parameter 'allowSnapshots'.");
        }

        String currentBranch = gitCurrentBranch();
        if (!isDevelopmentBranch(currentBranch) && !isMaintenanceBranch(currentBranch)) {
            throw new GitFlowFailureException(
                    "Release can be started only on development branch '" + gitFlowConfig.getDevelopmentBranch()
                            + "' or on a maintenance branch.",
                    "Please switch to the development branch '" + gitFlowConfig.getDevelopmentBranch()
                            + "' or to a maintenance branch first in order to proceed.");
        }

        boolean releaseOnMaintenanceBranch = isMaintenanceBranch(currentBranch);

        gitAssertRemoteBranchNotAheadOfLocalBranche(currentBranch,
                new GitFlowFailureInfo("Remote branch '" + currentBranch + "' is ahead of the local branch.",
                        "Either pull changes on remote branch into local branch or reset the changes on remote "
                                + "branch in order to proceed.",
                        "'git pull' to pull remote changes into local branch"),
                new GitFlowFailureInfo("Remote and local branches '" + currentBranch + "' diverge.",
                        "Either rebase/merge the changes into local branch '" + currentBranch
                                + "' or reset the changes on remote branch in order to proceed.",
                        "'git rebase'"));

        if (!gitFlowConfig.isNoProduction()) {
            // check the production branch, too
            String productionBranch = gitFlowConfig.getProductionBranch();
            if (releaseOnMaintenanceBranch) {
                // derive production branch from maintenance branch
                productionBranch = productionBranch + "-" + currentBranch;
            }
            gitEnsureLocalBranchIsUpToDateIfExists(productionBranch,
                    new GitFlowFailureInfo(
                            "Remote and local production branches '" + productionBranch + "' diverge. "
                                    + "This indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"));
        }

        // get current project version from pom in the current (development or
        // maintenance) branch
        final String currentVersion = getCurrentProjectVersion();

        String version = getReleaseVersion();
        if (version == null) {
            getLog().info("Property 'releaseVersion' not provided. Trying to calculate it from project version.");
            getLog().info("Project version: " + currentVersion);
            String defaultVersion;
            // get default release version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                defaultVersion = versionInfo.getReleaseVersionString();
                getLog().info("Calculated releaseVersion: " + defaultVersion);
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
                throw new GitFlowFailureException(
                        "Failed to calculate release version. The project version '" + currentVersion
                                + "' can't be parsed.",
                        "Check the version of the project or run 'mvn flow:" + getCurrentGoal()
                                + "' with specified parameter 'releaseVersion'.",
                        "'mvn flow:" + getCurrentGoal() + " -DreleaseVersion=X.Y.Z' to predefine release version");
            }
            version = getPrompter().promptValue("What is the release version?", defaultVersion);
            if (!settings.isInteractiveMode()) {
                getLog().info("Using calculated releaseVersion for release branch in batch mode.");
            }
        }
        if (tychoBuild) {
            // make sure we have an OSGi conforming version
            try {
                version = makeValidTychoVersion(version);
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }
        }
        getLog().info("Using releaseVersion: " + version);

        // actually create the release branch now
        String releaseBranchName = gitFlowConfig.getReleaseBranchPrefix();
        if (!isSameBranchName()) {
            releaseBranchName += version;
        }

        if (gitBranchExists(releaseBranchName)) {
            throw new GitFlowFailureException(
                    "Release branch '" + releaseBranchName + "' already exists. Cannot start release.",
                    "Either checkout the existing release branch or start a new release with another release version.",
                    "'git checkout " + releaseBranchName + "' to checkout the release branch",
                    "'mvn flow:" + getCurrentGoal() + "' to run again and specify another release version");
        }
        if (gitRemoteBranchExists(releaseBranchName)) {
            throw new GitFlowFailureException(
                    "Remote release branch '" + releaseBranchName + "' already exists on the remote '"
                            + gitFlowConfig.getOrigin() + "'. Cannot start release.",
                    "Either checkout the existing release branch or start a new release with another release version.",
                    "'git checkout " + releaseBranchName + "' to checkout the release branch",
                    "'mvn flow:" + getCurrentGoal() + "' to run again and specify another release version");
        }

        // git checkout -b release/... develop to create the release branch
        gitCreateAndCheckout(releaseBranchName,
                releaseOnMaintenanceBranch ? currentBranch : gitFlowConfig.getDevelopmentBranch());

        // store development branch in branch config
        gitSetConfig("branch." + releaseBranchName + ".development", currentBranch);

        // execute if version changed
        if (!version.equals(currentVersion)) {
            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(version);

            // git commit -a -m updating versions for release
            gitCommit(commitMessages.getReleaseStartMessage());
        }

        if (pushRemote && isPushReleaseBranch()) {
            // push the release branch to the remote
            gitPush(gitCurrentBranch(), false, false);
        }
        if (isInstallProject()) {
            // mvn clean install
            mvnCleanInstall();
        }

        return releaseOnMaintenanceBranch;
    }

    /**
     * Perfom the steps to finish a release. Must be called on a release branch.
     * It will merge the branch either to development/production or maintenance,
     * depending on configuration and branch point.
     *
     * @param releaseOnMaintenanceBranch
     *            <code>true</code> if the release is on a support/maintenance
     *            branch
     * @throws MojoExecutionException
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void releaseFinish(String developmentBranch, boolean releaseOnMaintenanceBranch)
            throws MojoExecutionException, MojoFailureException, CommandLineException {

        // fetch and check remote
        final String releaseBranch = gitCurrentBranch();
        if (!isReleaseBranch(releaseBranch)) {
            throw new GitFlowFailureException("Current branch '" + releaseBranch + "' is not a release branch.",
                    "Please switch to the release branch that you want to finish in order to proceed.",
                    "'git checkout BRANCH' to switch to the release branch");
        }

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        String nextSnapshotVersion = getDevelopmentVersion();
        if (nextSnapshotVersion == null) {
            // get next snapshot version
            getLog().info("Property 'developmentVersion' not provided. Trying to calculate it from released project "
                    + "version.");
            getLog().info("Released project version: " + currentVersion);
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                nextSnapshotVersion = versionInfo.getNextVersion().getSnapshotVersionString();
                getLog().info("Calculated developmentVersion: " + nextSnapshotVersion);
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }
            nextSnapshotVersion = getPrompter().promptValue("What is the next development version?",
                    nextSnapshotVersion,
                    new GitFlowFailureInfo(
                            "Failed to calculate next development version. The release version '" + currentVersion
                                    + "' can't be parsed.",
                            "Run 'mvn flow:" + getCurrentGoal() + "' in interactive mode or with specified parameter "
                                    + "'developmentVersion'.",
                            "'mvn flow:" + getCurrentGoal() + " -DdevelopmentVersion=X.Y.Z-SNAPSHOT -B' to predefine "
                                    + "next development version",
                            "'mvn flow:" + getCurrentGoal() + "' to run in interactive mode"));
            if (!settings.isInteractiveMode()) {
                getLog().info("Using calculated next development version for development branch in batch mode.");
            }
        }

        if (!isSkipTestProject()) {
            // mvn clean test
            mvnCleanTest();
        }

        // perform the release goals
        if (getReleaseGoals() != null) {
            for (String goals : getReleaseGoals()) {
                if (isSkipDeployProject()) {
                    goals = goals.replaceAll("(?:^|\\s+)deploy(?:$|\\s+)",
                            StringUtils.isEmpty(getDeployReplacement()) ? " " : " " + getDeployReplacement() + " ")
                            .trim();
                    if (goals.isEmpty()) {
                        continue;
                    }
                }
                mvnGoals(goals);
            }
        }

        // we're now on the target branch for the release
        String releaseCommit = getCurrentCommit();

        // tag the release on the target branch
        if (!isSkipTag()) {
            String tagVersion = currentVersion;
            if (tychoBuild && ArtifactUtils.isSnapshot(currentVersion)) {
                tagVersion = currentVersion.replace("-" + Artifact.SNAPSHOT_VERSION, "");
            }

            // git tag -a ...
            gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion, commitMessages.getTagReleaseMessage());
        }

        String productionBranch = gitFlowConfig.getProductionBranch();
        if (releaseOnMaintenanceBranch) {
            // derive production branch from maintenance branch
            productionBranch += "-" + developmentBranch;
        }

        // if we're on a release branch merge it now to maintenance or
        // production.
        if (isUsingProductionBranch(developmentBranch, productionBranch)) {
            if (gitBranchExists(productionBranch)) {
                gitCheckout(productionBranch);
            } else {
                gitCreateAndCheckout(productionBranch, releaseBranch);
            }
            gitSetConfig("branch." + productionBranch + ".releaseCommit", releaseCommit);
            gitSetConfig("branch." + productionBranch + ".nextSnapshotVersion", nextSnapshotVersion);
            getLog().info(
                    "Merging release '" + releaseBranch + "' branch into production branch '" + productionBranch + "'");
            // merge release branch into production, never rebase
            try {
                gitMerge(releaseBranch, isReleaseMergeProductionNoFF());
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge of release branch '" + releaseBranch + "' into production branch '"
                                + productionBranch + "' failed.\nGit error message:\n"
                                + StringUtils.trim(ex.getMessage()),
                        "Either abort the release process or fix the merge conflicts, mark them as resolved and run "
                                + "'mvn flow:" + getCurrentGoal() + "' again.\nDo NOT run 'git merge --continue'!",
                        "'mvn flow:release-abort' to abort the release process",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:" + getCurrentGoal() + "' to continue release process");
            }
        }

        prepareDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                developmentBranch, releaseCommit);
    }

    private boolean isUsingProductionBranch(String developmentBranch, String productionBranch) {
        return !gitFlowConfig.isNoProduction() && !developmentBranch.equals(productionBranch);
    }

    private void prepareDevelopmentBranchAndFinilizeRelease(String nextSnapshotVersion, String releaseBranch,
            String productionBranch, String developmentBranch, String releaseCommit)
            throws MojoFailureException, CommandLineException {
        if (isUsingProductionBranch(developmentBranch, productionBranch)) {
            gitSetConfig("branch." + productionBranch + ".releaseCommit", null);
            gitSetConfig("branch." + productionBranch + ".nextSnapshotVersion", null);
        }
        gitCheckout(developmentBranch);
        gitSetConfig("branch." + developmentBranch + ".releaseCommit", releaseCommit);
        gitSetConfig("branch." + developmentBranch + ".releaseBranch", releaseBranch);
        gitSetConfig("branch." + developmentBranch + ".nextSnapshotVersion", nextSnapshotVersion);
        // if there are any changes in the remote development branch, we need to
        // merge them now
        try {
            gitEnsureCurrentLocalBranchIsUpToDateByMerging();
        } catch (MojoFailureException ex) {
            throw new GitFlowFailureException(ex,
                    "Automatic merge of remote branch into local development branch '" + developmentBranch
                            + "' failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                    "Either abort the release process or fix the merge conflicts, mark them as resolved and run "
                            + "'mvn flow:" + getCurrentGoal() + "' again.\nDo NOT run 'git merge --continue'!",
                    "'mvn flow:release-abort' to abort the release process",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                            + "resolved",
                    "'mvn flow:" + getCurrentGoal() + "' to continue release process");
        }

        mergeIntoCurrentDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                developmentBranch, releaseCommit);
    }

    private void mergeIntoCurrentDevelopmentBranchAndFinilizeRelease(String nextSnapshotVersion, String releaseBranch,
            String productionBranch, String developmentBranch, String releaseCommit)
            throws MojoFailureException, CommandLineException {
        if (isUsingProductionBranch(developmentBranch, productionBranch)) {
            getLog().info("Merging production branch '" + productionBranch + "' into development branch "
                    + developmentBranch);
            // and merge back the target, which has the merged release branch,
            // never rebase
            try {
                gitMerge(productionBranch, isReleaseMergeNoFF());
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge of production branch '" + productionBranch + "' into development branch '"
                                + developmentBranch + "' failed.\nGit error message:\n"
                                + StringUtils.trim(ex.getMessage()),
                        "Either abort the release process or fix the merge conflicts, mark them as resolved and run "
                                + "'mvn flow:" + getCurrentGoal() + "' again.\nDo NOT run 'git merge --continue'!",
                        "'mvn flow:release-abort' to abort the release process",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:" + getCurrentGoal() + "' to continue release process");
            }
        } else {
            getLog().info("Merging release '" + releaseBranch + "' branch into development branch '" + developmentBranch
                    + "'");
            // merge release branch to development, never rebase
            try {
                gitMerge(releaseBranch, isReleaseMergeNoFF());
            } catch (MojoFailureException ex) {
                throw new GitFlowFailureException(ex,
                        "Automatic merge of release branch '" + releaseBranch + "' into development branch '"
                                + developmentBranch + "' failed.\nGit error message:\n"
                                + StringUtils.trim(ex.getMessage()),
                        "Either abort the release process or fix the merge conflicts, mark them as resolved and run "
                                + "'mvn flow:" + getCurrentGoal() + "' again.\nDo NOT run 'git merge --continue'!",
                        "'mvn flow:release-abort' to abort the release process",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:" + getCurrentGoal() + "' to continue release process");
            }
        }

        finilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch, developmentBranch, releaseCommit);
    }

    private void finilizeRelease(String nextSnapshotVersion, String releaseBranch, String productionBranch,
            String developmentBranch, String releaseCommit) throws MojoFailureException, CommandLineException {
        gitSetConfig("branch." + developmentBranch + ".releaseCommit", null);
        gitSetConfig("branch." + developmentBranch + ".releaseBranch", null);
        gitSetConfig("branch." + developmentBranch + ".nextSnapshotVersion", null);

        // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
        mvnSetVersions(nextSnapshotVersion, "Next development version: ");

        // git commit -a -m updating for next development version
        gitCommit(commitMessages.getReleaseFinishMessage());

        if (!isDetachReleaseCommit() && installProject) {
            // mvn clean install
            mvnCleanInstall();
        }

        if (!isKeepBranch()) {
            // remove the release branch
            gitBranchDelete(releaseBranch);
            if (pushRemote && isPushReleaseBranch()) {
                gitBranchDeleteRemote(releaseBranch);
            }
        }

        if (pushRemote) {
            if (!gitFlowConfig.isNoProduction()) {
                gitPush(productionBranch, !isSkipTag(), false);
            }
            gitPush(developmentBranch, !isSkipTag(), false);
        }
        if (isDetachReleaseCommit()) {
            // make sure we leave the workspace in the state as released
            gitCheckout(releaseCommit);
        }
    }

    /**
     * Continue release finish if merge in process detected.
     *
     * @param currentBranch
     *            the current branch
     * @return <code>true</code> if merge in process detected and release finish
     *         continued
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean continueReleaseFinishIfMergeInProcess(String currentBranch)
            throws MojoFailureException, CommandLineException {
        String mergeFromBranch = gitMergeFromBranchIfInProcess(gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getMaintenanceBranchPrefix() + "*", gitFlowConfig.getProductionBranch(),
                gitFlowConfig.getProductionBranch() + "-" + gitFlowConfig.getMaintenanceBranchPrefix() + "*",
                gitFlowConfig.getReleaseBranchPrefix() + "*",
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getMaintenanceBranchPrefix() + "*");
        if (mergeFromBranch != null) {
            releaseFinishContinueAfterMergeConflict(currentBranch, mergeFromBranch);
            return true;
        }
        return false;
    }

    private void releaseFinishContinueAfterMergeConflict(String mergeIntoBranch, String mergeFromBranch)
            throws MojoFailureException, CommandLineException {
        String developmentBranch;
        String productionBranch;
        String releaseBranch;
        String nextSnapshotVersion;
        String releaseCommit;
        if (isReleaseBranch(mergeFromBranch)) {
            releaseBranch = mergeFromBranch;
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                releaseCommit = gitGetConfig("branch." + developmentBranch + ".releaseCommit");
                nextSnapshotVersion = gitGetConfig("branch." + developmentBranch + ".nextSnapshotVersion");
                promptAndMergeContinue();
                finilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch, developmentBranch, releaseCommit);
            } else if (isProductionBranch(mergeIntoBranch)) {
                productionBranch = mergeIntoBranch;
                developmentBranch = getDevelopmentBranchForProductionBranch(productionBranch);
                releaseCommit = gitGetConfig("branch." + productionBranch + ".releaseCommit");
                nextSnapshotVersion = gitGetConfig("branch." + productionBranch + ".nextSnapshotVersion");
                promptAndMergeContinue();
                prepareDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                        developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isProductionBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = mergeFromBranch;
                releaseCommit = gitGetConfig("branch." + developmentBranch + ".releaseCommit");
                releaseBranch = gitGetConfig("branch." + developmentBranch + ".releaseBranch");
                nextSnapshotVersion = gitGetConfig("branch." + developmentBranch + ".nextSnapshotVersion");
                promptAndMergeContinue();
                finilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch, developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isRemoteBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                releaseCommit = gitGetConfig("branch." + developmentBranch + ".releaseCommit");
                releaseBranch = gitGetConfig("branch." + developmentBranch + ".releaseBranch");
                nextSnapshotVersion = gitGetConfig("branch." + developmentBranch + ".nextSnapshotVersion");
                promptAndMergeContinue();
                mergeIntoCurrentDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch,
                        productionBranch, developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
            }
        } else {
            throw new GitFlowFailureException(
                    getFailureMessageForUnsupportedMergeConflict(mergeIntoBranch, mergeFromBranch));
        }
    }

    private void promptAndMergeContinue() throws GitFlowFailureException, CommandLineException {
        if (!getPrompter().promptConfirmation(
                "You have a merge in process on your current branch. If you run 'mvn flow:" + getCurrentGoal()
                        + "' before and merge had conflicts you can continue. "
                        + "In other case it is better to clarify the reason of merge in process. Continue?",
                true, true)) {
            throw new GitFlowFailureException("Continuation of release process aborted by user.", null);
        }
        try {
            gitCommitMerge();
        } catch (MojoFailureException exc) {
            throw new GitFlowFailureException(exc,
                    "There are unresolved conflicts after merge.\nGit error message:\n"
                            + StringUtils.trim(exc.getMessage()),
                    "Fix the merge conflicts and mark them as resolved. After that, run 'mvn flow:" + getCurrentGoal()
                            + "' again.\nDo NOT run 'git merge --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                    "'mvn flow:" + getCurrentGoal() + "' to continue release process");
        }
    }

    private GitFlowFailureInfo getFailureMessageForUnsupportedMergeConflict(String mergeIntoBranch,
            String mergeFromBranch) {
        return new GitFlowFailureInfo(
                "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                        + "'. After such a conflict can't be automatically proceeded.",
                "Please consult a gitflow expert on how to fix this!");
    }

}
