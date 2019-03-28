//
// AbstractGitFlowReleaseMojo.java
//
// Copyright (C) 2017
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

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

    private static final String BREAKPOINT_RELEASE_START = "releaseStart.cleanInstall";

    private static final String BREAKPOINT_RELEASE_FINISH = "releaseFinish.cleanInstall";

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

    /**
     * The mojo provides this flag from a configuration property.
     */
    protected abstract boolean isAllowSameVersion();

    protected boolean isInstallProject() {
        return installProject;
    }

    /**
     * Perfom the steps to start a release. Create a release branch and sets the
     * version
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void releaseStart() throws MojoFailureException, CommandLineException {
        String currentBranch = gitCurrentBranch();
        String releaseBranchName = null;
        if (isReleaseBranch(currentBranch)) {
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null && BREAKPOINT_RELEASE_START.equals(breakpoint)) {
                releaseBranchName = currentBranch;
            }
        }
        String developmentBranch;
        if (releaseBranchName == null) {
            // check snapshots dependencies
            if (!allowSnapshots && hasProjectSnapshotDependencies()) {
                throw new GitFlowFailureException(
                        "There are some SNAPSHOT dependencies in the project. Release cannot be started.",
                        "Change the dependencies or ignore with parameter 'allowSnapshots'.");
            }

            if (!isDevelopmentBranch(currentBranch) && !isMaintenanceBranch(currentBranch)) {
                throw new GitFlowFailureException(
                        "Release can be started only on development branch '" + gitFlowConfig.getDevelopmentBranch()
                                + "' or on a maintenance branch.",
                        "Please switch to the development branch '" + gitFlowConfig.getDevelopmentBranch()
                                + "' or to a maintenance branch first in order to proceed.");
            }
            developmentBranch = currentBranch;
            getMavenLog().info("Base branch for release is '" + developmentBranch + "'");
            gitAssertRemoteBranchNotAheadOfLocalBranche(developmentBranch,
                    new GitFlowFailureInfo("Remote branch '" + developmentBranch + "' is ahead of the local branch.",
                            "Either pull changes on remote branch into local branch or reset the changes on remote "
                                    + "branch in order to proceed.",
                            "'git pull' to pull remote changes into local branch"),
                    new GitFlowFailureInfo("Remote and local branches '" + developmentBranch + "' diverge.",
                            "Either rebase/merge the changes into local branch '" + developmentBranch
                                    + "' or reset the changes on remote branch in order to proceed.",
                            "'git rebase'"));

            String productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
            if (isUsingProductionBranch(developmentBranch, productionBranch)) {
                // check the production branch, too
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
            getMavenLog().info("Using releaseVersion: " + version);

            // actually create the release branch now
            releaseBranchName = gitFlowConfig.getReleaseBranchPrefix();
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

            String developmentCommitRef = getCurrentCommit();

            getMavenLog().info("Creating release branch '" + releaseBranchName + "'");
            gitCreateAndCheckout(releaseBranchName, developmentBranch);

            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(releaseBranchName, BranchConfigKeys.BRANCH_TYPE, BranchType.RELEASE.getType());
            branchConfigChanges.set(releaseBranchName, BranchConfigKeys.BASE_BRANCH, developmentBranch);
            branchConfigChanges.set(releaseBranchName, BranchConfigKeys.RELEASE_DEVELOPMENT_SAVEPOINT,
                    developmentCommitRef);

            // store development branch in branch config
            if (isUsingProductionBranch(developmentBranch, productionBranch)) {
                if (gitBranchExists(productionBranch)) {
                    String productionCommitRef = getCurrentCommit(productionBranch);
                    branchConfigChanges.set(releaseBranchName, BranchConfigKeys.RELEASE_PRODUCTION_SAVEPOINT,
                            productionCommitRef);
                }
            }

            if (!version.equals(currentVersion)) {
                getMavenLog().info("Setting release version '" + version + "' for project on release branch...");
                mvnSetVersions(version, GitFlowAction.RELEASE_START, "On release branch: ");
                gitCommit(commitMessages.getReleaseStartMessage());
            }

            if (pushRemote && isPushReleaseBranch()) {
                getMavenLog().info("Pushing release branch '" + releaseBranchName + "' to remote repository");
                gitPush(gitCurrentBranch(), false, false, true);
            }

            gitApplyBranchCentralConfigChanges(branchConfigChanges, "release '" + releaseBranchName + "' started");
        } else {
            getMavenLog().info("Restart after failed release project installation detected");
            developmentBranch = gitGetBranchCentralConfig(releaseBranchName, BranchConfigKeys.BASE_BRANCH);
            if (developmentBranch == null) {
                throw new GitFlowFailureException(
                        "Base branch for release branch '" + releaseBranchName
                                + "' couldn't be found in central branch config.",
                        "Please consult a gitflow expert on how to fix this!");
            }
        }

        if (isInstallProject()) {
            getMavenLog().info("Installing the release project...");
            gitSetBranchLocalConfig(releaseBranchName, "breakpoint", BREAKPOINT_RELEASE_START);
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Release process paused on failed project installation after release start to fix "
                        + "project problems");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e, FailureInfoHelper.installProjectFailure(getCurrentGoal(),
                        releaseBranchName, "release start", reason));
            }
        }
        gitRemoveBranchLocalConfig(releaseBranchName, "breakpoint");
    }

    /**
     * Perfom the steps to finish a release. Must be called on a release branch. It
     * will merge the branch either to development/production or maintenance,
     * depending on configuration and branch point.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void releaseFinish(String developmentBranch)
            throws MojoExecutionException, MojoFailureException, CommandLineException {

        // fetch and check remote
        final String releaseBranch = gitCurrentBranch();
        if (!isReleaseBranch(releaseBranch)) {
            throw new GitFlowFailureException("Current branch '" + releaseBranch + "' is not a release branch.",
                    "Please switch to the release branch that you want to finish in order to proceed.",
                    "'git checkout INTERNAL' to switch to the release branch");
        }

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        String nextSnapshotVersion = getDevelopmentVersion();
        if (nextSnapshotVersion == null && isAllowSameVersion()) {
            // next development version = release version
            nextSnapshotVersion = currentVersion;
        }
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

        if (!isAllowSameVersion() && currentVersion.equals(nextSnapshotVersion)) {
            throw new GitFlowFailureException(
                    "Failed to finish release process because the next develompent version is same as release version.",
                    "Run 'mvn flow:" + getCurrentGoal() + "' and define a development version different from release "
                            + "version.\nOr use property '-Dflow.allowSameVersion=true' to explicitly allow same "
                            + "versions.",
                    "'mvn flow:" + getCurrentGoal() + " -DdevelopmentVersion=X.Y.Z-SNAPSHOT' to predefine next "
                            + "development version different from the release version",
                    "'mvn flow:" + getCurrentGoal()
                            + " -Dflow.allowSameVersion=true' to explicitly allow same versions");
        }

        if (!isSkipTestProject()) {
            getMavenLog().info("Testing the release project...");
            mvnCleanVerify();
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
                getMavenLog().info("Executing release goals [" + goals + "]...");
                mvnGoals(goals);
            }
        }

        getMavenLog().info("Fetching branches from remote repository");
        gitFetchForced();
        // we're now on the target branch for the release
        String releaseCommit = getCurrentCommit();
        gitSetBranchLocalConfig(releaseBranch, "releaseCommit", releaseCommit);
        gitSetBranchLocalConfig(releaseBranch, "nextSnapshotVersion", nextSnapshotVersion);

        // tag the release on the target branch
        if (!isSkipTag()) {
            String tagVersion = currentVersion;
            if (tychoBuild && ArtifactUtils.isSnapshot(currentVersion)) {
                tagVersion = currentVersion.replace("-" + Artifact.SNAPSHOT_VERSION, "");
            }
            String releaseTag = gitFlowConfig.getVersionTagPrefix() + tagVersion;
            getMavenLog().info("Creating release tag '" + releaseTag + "'");
            gitTag(releaseTag, commitMessages.getTagReleaseMessage());
            gitSetBranchLocalConfig(releaseBranch, "releaseTag", releaseTag);
        }

        String productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
        // if we're on a release branch merge it now to maintenance or
        // production.
        if (isUsingProductionBranch(developmentBranch, productionBranch)) {
            if (gitBranchExists(productionBranch)) {
                gitCheckout(productionBranch);
            } else {
                getMavenLog().info("Creating production branch '" + productionBranch + "'");
                gitCreateAndCheckout(productionBranch, releaseBranch);
            }
            // merge release branch into production, never rebase
            try {
                getMavenLog().info("Merging release branch '" + releaseBranch + "' into production branch '"
                        + productionBranch + "'");
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

    private void prepareDevelopmentBranchAndFinilizeRelease(String nextSnapshotVersion, String releaseBranch,
            String productionBranch, String developmentBranch, String releaseCommit)
            throws MojoFailureException, CommandLineException {
        gitCheckout(developmentBranch);
        gitSetBranchLocalConfig(developmentBranch, "releaseBranch", releaseBranch);
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
            // and merge back the target, which has the merged release branch,
            // never rebase
            try {
                getMavenLog().info("Merging production branch '" + productionBranch + "' into development branch "
                        + developmentBranch);
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
            // merge release branch to development, never rebase
            try {
                getMavenLog().info("Merging release branch '" + releaseBranch + "' into development branch '"
                        + developmentBranch + "'");
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

        setDevelopmentVersionAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch, developmentBranch,
                releaseCommit);
    }

    private void setDevelopmentVersionAndFinilizeRelease(String nextSnapshotVersion, String releaseBranch,
            String productionBranch, String developmentBranch, String releaseCommit)
            throws MojoFailureException, CommandLineException {
        String currentVersion = getCurrentProjectVersion();
        if (!nextSnapshotVersion.equals(currentVersion)) {
            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            getMavenLog().info("Setting next development version '" + nextSnapshotVersion
                    + "' for project on development branch...");
            mvnSetVersions(nextSnapshotVersion, GitFlowAction.RELEASE_FINISH, "Next development version: ");

            // git commit -a -m updating for next development version
            gitCommit(commitMessages.getReleaseFinishMessage());
        }

        finilizeRelease(releaseBranch, productionBranch, developmentBranch, releaseCommit);
    }

    private void finilizeRelease(String releaseBranch, String productionBranch, String developmentBranch,
            String releaseCommit) throws MojoFailureException, CommandLineException {
        if (!isDetachReleaseCommit() && installProject) {
            getMavenLog().info("Installing the project...");
            gitSetBranchLocalConfig(developmentBranch, "breakpoint", BREAKPOINT_RELEASE_FINISH);
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Release process paused on failed project installation after release finish to fix "
                        + "project problems");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e, FailureInfoHelper.installProjectFailure(getCurrentGoal(),
                        developmentBranch, "release finish", reason));
            }
        }
        gitRemoveBranchLocalConfig(developmentBranch, "breakpoint");
        gitRemoveBranchLocalConfig(developmentBranch, "releaseBranch");

        gitRemoveBranchLocalConfig(releaseBranch, "releaseTag");
        gitRemoveBranchLocalConfig(releaseBranch, "releaseCommit");
        gitRemoveBranchLocalConfig(releaseBranch, "nextSnapshotVersion");

        // first push modified branches
        if (pushRemote) {
            if (isUsingProductionBranch(developmentBranch, productionBranch)) {
                getMavenLog().info("Pushing production branch '" + productionBranch + "' to remote repository");
                gitPush(productionBranch, !isSkipTag(), false);
            }
            getMavenLog().info("Pushing development branch '" + developmentBranch + "' to remote repository");
            gitPush(developmentBranch, !isSkipTag(), false);
        }
        if (isDetachReleaseCommit()) {
            // make sure we leave the workspace in the state as released
            getMavenLog().info("Checking out release commit");
            gitCheckout(releaseCommit);
        }

        // then delete if wanted
        if (!isKeepBranch()) {
            // remove the release branch
            getMavenLog().info("Removing local release branch '" + releaseBranch + "'");
            gitBranchDeleteForce(releaseBranch);
            if (pushRemote && isPushReleaseBranch()) {
                getMavenLog().info("Removing remote release branch '" + releaseBranch + "'");
                gitBranchDeleteRemote(releaseBranch);
            }
            gitRemoveAllBranchCentralConfigsForBranch(releaseBranch, "release '" + releaseBranch + "' finished");
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
            getMavenLog().info("Merge in progress detected. Continue release finish process.");
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
                releaseCommit = gitGetBranchLocalConfig(releaseBranch, "releaseCommit");
                nextSnapshotVersion = gitGetBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
                promptAndMergeContinue();
                setDevelopmentVersionAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                        developmentBranch, releaseCommit);
            } else if (isProductionBranch(mergeIntoBranch)) {
                productionBranch = mergeIntoBranch;
                developmentBranch = getDevelopmentBranchForProductionBranch(productionBranch);
                releaseCommit = gitGetBranchLocalConfig(releaseBranch, "releaseCommit");
                nextSnapshotVersion = gitGetBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
                promptAndMergeContinue();
                prepareDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                        developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflictWhileRealeasing(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isProductionBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = mergeFromBranch;
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
                releaseCommit = gitGetBranchLocalConfig(releaseBranch, "releaseCommit");
                nextSnapshotVersion = gitGetBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
                promptAndMergeContinue();
                setDevelopmentVersionAndFinilizeRelease(nextSnapshotVersion, releaseBranch, productionBranch,
                        developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflictWhileRealeasing(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isRemoteBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
                releaseCommit = gitGetBranchLocalConfig(releaseBranch, "releaseCommit");
                nextSnapshotVersion = gitGetBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
                promptAndMergeContinue();
                mergeIntoCurrentDevelopmentBranchAndFinilizeRelease(nextSnapshotVersion, releaseBranch,
                        productionBranch, developmentBranch, releaseCommit);
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflictWhileRealeasing(mergeIntoBranch, mergeFromBranch));
            }
        } else {
            throw new GitFlowFailureException(
                    getFailureMessageForUnsupportedMergeConflictWhileRealeasing(mergeIntoBranch, mergeFromBranch));
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

    private GitFlowFailureInfo getFailureMessageForUnsupportedMergeConflictWhileRealeasing(String mergeIntoBranch,
            String mergeFromBranch) {
        return new GitFlowFailureInfo(
                "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                        + "'. After such a conflict release can't be automatically proceeded.",
                "Please consult a gitflow expert on how to fix this!");
    }

    protected boolean continueReleaseFinishIfInstallProjectPaused(String currentBranch)
            throws MojoFailureException, CommandLineException {
        if (isDevelopmentBranch(currentBranch) || isMaintenanceBranch(currentBranch)) {
            String developmentBranch = currentBranch;
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null && BREAKPOINT_RELEASE_FINISH.equals(breakpoint)) {
                String productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                String releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
                String releaseCommit = gitGetBranchLocalConfig(releaseBranch, "releaseCommit");
                finilizeRelease(releaseBranch, productionBranch, developmentBranch, releaseCommit);
                return true;
            }
        }
        return false;
    }

    // release abort stuff

    /**
     * Perfom the steps to start a release. Create a release branch and sets the
     * version
     *
     * @param cleanupBeforeStart
     *            whether to clean-up possibly failed release before starting new
     *            release.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void abortNotFinishedReleaseIfNeeded(boolean cleanupBeforeStart)
            throws MojoFailureException, CommandLineException {
        if (cleanupBeforeStart && isOnNotFinishedRelease()) {
            getMavenLog().info("Not finished release detected. Trying to abort it before starting new release.");
            abortRelease();
        }
    }

    private boolean isOnNotFinishedRelease() throws MojoFailureException, CommandLineException {
        String currentBranch = gitCurrentBranchOrCommit();
        if (isReleaseBranch(currentBranch)) {
            return true;
        }
        if (isDevelopmentBranch(currentBranch) || isMaintenanceBranch(currentBranch)) {
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null && breakpoint.equals(BREAKPOINT_RELEASE_FINISH)) {
                return true;
            }
        }
        if (getMergeInProcessSourceBranch() != null) {
            return true;
        }
        return false;
    }

    protected String getMergeInProcessSourceBranch() throws MojoFailureException, CommandLineException {
        return gitMergeFromBranchIfInProcess(gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getMaintenanceBranchPrefix() + "*", gitFlowConfig.getProductionBranch(),
                gitFlowConfig.getProductionBranch() + "-" + gitFlowConfig.getMaintenanceBranchPrefix() + "*",
                gitFlowConfig.getReleaseBranchPrefix() + "*",
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getDevelopmentBranch(),
                gitFlowConfig.getOrigin() + "/" + gitFlowConfig.getMaintenanceBranchPrefix() + "*");
    }

    /**
     * @throws MojoFailureException
     * @throws CommandLineException
     * @throws GitFlowFailureException
     */
    protected void abortRelease() throws MojoFailureException, CommandLineException {
        String currentBranch = gitCurrentBranch();
        String releaseBranch = abortReleaseInProcessWithReleaseFinishPause(currentBranch);
        if (releaseBranch == null) {
            releaseBranch = abortReleaseWithConflictsIfMergeInProcess(currentBranch);
        }
        String developmentBranch;
        if (releaseBranch == null) {
            if (isReleaseBranch(currentBranch)) {
                releaseBranch = currentBranch;
                developmentBranch = gitGetBranchCentralConfig(releaseBranch, BranchConfigKeys.BASE_BRANCH);
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
        String mergeFromBranch = getMergeInProcessSourceBranch();
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
                        getFailureMessageForUnsupportedMergeConflictWhileAborting(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isProductionBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = mergeFromBranch;
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflictWhileAborting(mergeIntoBranch, mergeFromBranch));
            }
        } else if (isRemoteBranch(mergeFromBranch)) {
            if (isDevelopmentBranch(mergeIntoBranch) || isMaintenanceBranch(mergeIntoBranch)) {
                developmentBranch = mergeIntoBranch;
                productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
            } else {
                throw new GitFlowFailureException(
                        getFailureMessageForUnsupportedMergeConflictWhileAborting(mergeIntoBranch, mergeFromBranch));
            }
        } else {
            throw new GitFlowFailureException(
                    getFailureMessageForUnsupportedMergeConflictWhileAborting(mergeIntoBranch, mergeFromBranch));
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

    private String abortReleaseInProcessWithReleaseFinishPause(String currentBranch)
            throws MojoFailureException, CommandLineException {
        if (isDevelopmentBranch(currentBranch) || isMaintenanceBranch(currentBranch)) {
            String developmentBranch = currentBranch;
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null && BREAKPOINT_RELEASE_FINISH.equals(breakpoint)) {
                String productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
                String releaseBranch = gitGetBranchLocalConfig(developmentBranch, "releaseBranch");
                String developmentCommitRef = gitGetBranchCentralConfig(releaseBranch,
                        BranchConfigKeys.RELEASE_DEVELOPMENT_SAVEPOINT);
                if (StringUtils.isBlank(developmentCommitRef)) {
                    throw new GitFlowFailureException(
                            "Reset point for development branch couldn't be found in git config.\n"
                                    + "Release can't be automatically aborted.",
                            "Please consult a gitflow expert on how to fix this!");
                }
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
                } else if (isUsingProductionBranch(developmentBranch, productionBranch)
                        && gitBranchExists(productionBranch)) {
                    // production branch was newly created -> can be deleted on abort
                    gitBranchDeleteForce(productionBranch);
                }
                removeBranchConfigs(releaseBranch, developmentBranch);
                return releaseBranch;
            }
        }
        return null;
    }

    private void removeBranchConfigs(String releaseBranch, String developmentBranch)
            throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(releaseBranch, "releaseTag");
        gitRemoveBranchLocalConfig(releaseBranch, "releaseCommit");
        gitRemoveBranchLocalConfig(releaseBranch, "nextSnapshotVersion");
        gitRemoveBranchLocalConfig(developmentBranch, "releaseBranch");
        gitRemoveBranchLocalConfig(developmentBranch, "breakpoint");
    }

    private GitFlowFailureInfo getFailureMessageForUnsupportedMergeConflictWhileAborting(String mergeIntoBranch,
            String mergeFromBranch) {
        return new GitFlowFailureInfo(
                "There is a conflict of merging branch '" + mergeFromBranch + "' into branch '" + mergeIntoBranch
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }

}
