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
    protected abstract boolean isReleaseRebase();

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
    protected boolean releaseStart() throws MojoExecutionException, MojoFailureException, CommandLineException {
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
        if (StringUtils.isBlank(releaseBranch)) {
            throw new MojoFailureException("There is no release branch.");
        } else if (!releaseBranch.startsWith(gitFlowConfig.getReleaseBranchPrefix())) {
            throw new MojoFailureException("Current branch '" + releaseBranch + "' is not a release branch.");
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

        String productionBranch = gitFlowConfig.getProductionBranch();
        if (releaseOnMaintenanceBranch) {
            // derive production branch from maintenance branch
            productionBranch = productionBranch + "-" + gitFlowConfig.getMaintenanceBranchPrefix()
                    + developmentBranch.substring(gitFlowConfig.getMaintenanceBranchPrefix().length());
        }

        // if we're on a release branch merge it now to maintenance or
        // production.
        String targetBranch = gitFlowConfig.isNoProduction() ? developmentBranch : productionBranch;
        if (gitBranchExists(targetBranch)) {
            gitCheckout(targetBranch);
        } else {
            gitCreateAndCheckout(targetBranch, developmentBranch);
        }

        // merge the release branch in the target branch
        getLog().info("Merging release branch to " + targetBranch);
        if (gitFlowConfig.isNoProduction() || !targetBranch.equals(productionBranch)) {
            // merge release branch to development
            gitMerge(releaseBranch, isReleaseRebase(), isReleaseMergeNoFF());
        } else {
            // merge release branch to production, never rebase
            gitMerge(releaseBranch, false, isReleaseMergeProductionNoFF());
        }

        // we're now on the target branch for the release
        String releaseCommit = getCurrentCommit();

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        // tag the release on the target branch
        if (!isSkipTag()) {
            String tagVersion = currentVersion;
            if (tychoBuild && ArtifactUtils.isSnapshot(currentVersion)) {
                tagVersion = currentVersion.replace("-" + Artifact.SNAPSHOT_VERSION, "");
            }

            // git tag -a ...
            gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion, commitMessages.getTagReleaseMessage());
        }

        // go back to development to start new SNAPSHOT
        if (!developmentBranch.equals(targetBranch)) {
            // back to the development branch
            gitCheckout(developmentBranch);

            // and merge back the target, which has the merged release branch,
            // never rebase
            gitMerge(targetBranch, false, isReleaseMergeNoFF());
        }

        // if there are any changes in the remote development branch, we need to
        // merge them now
        gitFetchRemoteAndMergeIfNecessary(developmentBranch, false);

        String nextSnapshotVersion = null;
        if (getDevelopmentVersion() == null) {
            // get next snapshot version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                nextSnapshotVersion = versionInfo.getNextVersion().getSnapshotVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }
        } else {
            nextSnapshotVersion = getDevelopmentVersion();
        }

        if (StringUtils.isBlank(nextSnapshotVersion)) {
            throw new MojoFailureException("Next snapshot version is blank.");
        }

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

}
