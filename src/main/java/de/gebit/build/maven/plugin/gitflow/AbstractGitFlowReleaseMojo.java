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
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Common release steps that are used for release-start, release-finish and release
 * 
 * @author Erwin
 * @since 1.4.2
 */
public abstract class AbstractGitFlowReleaseMojo extends AbstractGitFlowMojo {

    protected abstract boolean isSkipTestProject();
    protected abstract boolean isSkipDeployProject();
    protected abstract boolean isSkipTag();
    protected abstract boolean isKeepBranch();
    protected abstract boolean isReleaseRebase();
    protected abstract boolean isReleaseMergeNoFF();
    protected abstract boolean isReleaseMergeProductionNoFF();
    protected abstract boolean isDetachReleaseCommit();
    protected abstract boolean isSameBranchName();
    
    protected abstract boolean isPushReleaseBranch();
    
    protected abstract String[] getReleaseGoals();
    protected abstract String getReleaseVersion();
    protected abstract String getDevelopmentVersion();

    protected boolean isInstallProject() {
        return installProject;
    }

    protected boolean releaseStart() throws MojoExecutionException, MojoFailureException, CommandLineException {
        // check snapshots dependencies
        if (!allowSnapshots) {
            checkSnapshotDependencies();
        }

        String currentBranch = gitCurrentBranch();
        if (currentBranch.startsWith(gitFlowConfig.getReleaseBranchPrefix())) {
            throw new MojoFailureException("Current branch '" + currentBranch + "' is already a release branch.");
        }
        boolean releaseOnSupportBranch = currentBranch.startsWith(gitFlowConfig.getSupportBranchPrefix()); 
        if (fetchRemote) {
            gitFetchRemoteAndCompare(currentBranch);
            if (!releaseOnSupportBranch && !gitFlowConfig.isNoProduction()) {
                // check the production branch, too
                gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
            }
        }

        // get current project version from pom in the current (development or maintenance) branch
        final String currentVersion = getCurrentProjectVersion();

        String version = null;
        if (getReleaseVersion() == null) {
            String defaultVersion = null;
            if (tychoBuild) {
                defaultVersion = currentVersion;
            } else {
                // get default release version
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    defaultVersion = versionInfo.getReleaseVersionString();
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
            }

            if (defaultVersion == null) {
                throw new MojoFailureException("Cannot get default project version.");
            }

            if (settings.isInteractiveMode()) {
                try {
                    version = prompter.prompt("What is release version? [" + defaultVersion + "]");
                } catch (PrompterException e) {
                    getLog().error(e);
                }
            }

            if (StringUtils.isBlank(version)) {
                version = defaultVersion;
            }
        } else {
            version = getReleaseVersion();
        }

        // actually create the release branch now
        String releaseBranchName = gitFlowConfig.getReleaseBranchPrefix();
        if (!isSameBranchName()) {
            releaseBranchName += version;
        }

        // release branch must not yet exist
        if (StringUtils.isNotBlank(gitFindBranch(releaseBranchName))) {
            throw new MojoFailureException("Release branch '" + releaseBranchName 
                    + "' already exists. Cannot start release.");
        }

        // git checkout -b release/... develop to create the release branch
        gitCreateAndCheckout(releaseBranchName, releaseOnSupportBranch 
                ? currentBranch : gitFlowConfig.getDevelopmentBranch());

        // execute if version changed
        if (!version.equals(currentVersion)) {
            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(version);

            // git commit -a -m updating versions for release
            gitCommit(commitMessages.getReleaseStartMessage());
        }

        if (pushRemote && isPushReleaseBranch()) {
            // push the release branch to the remote
            gitPush(gitCurrentBranch(), false);
        }
        if (isInstallProject()) {
            // mvn clean install
            mvnCleanInstall();
        }

        return releaseOnSupportBranch;
    }

    protected void releaseFinish(boolean releaseOnSupportBranch) throws MojoExecutionException, MojoFailureException, CommandLineException {
        // fetch and check remote
        String developmentBranch = getDevelopmentBranchForRelease();

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
                    goals = goals.replaceAll("(?:^|\\s+)deploy(?:$|\\s+)", " ").trim();
                    if (goals.isEmpty()) {
                        continue;
                    }
                }
                mvnGoals(goals);
            }
        }

        // if we're on a release branch merge it now to maintenance or production.
        String targetBranch = releaseOnSupportBranch || gitFlowConfig.isNoProduction() 
                ? developmentBranch : gitFlowConfig.getProductionBranch(); 
        gitCheckout(targetBranch);

        // merge the release branch in the target branch
        getLog().info("Merging release branch to " + targetBranch);
        if (gitFlowConfig.isNoProduction() || !targetBranch.equals(gitFlowConfig.getProductionBranch())) {
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

            // and merge back the target, which has the merged release branch, never rebase
            gitMerge(targetBranch, false, isReleaseMergeNoFF());
        }

        // if there are any changes in the remote development branch, we need to merge them now
        gitFetchRemoteAndMergeIfNecessary(developmentBranch);

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
            throw new MojoFailureException(
                    "Next snapshot version is blank.");
        }

        // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
        mvnSetVersions(nextSnapshotVersion);

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
            if (!releaseOnSupportBranch && !gitFlowConfig.isNoProduction()) {
                gitPush(gitFlowConfig.getProductionBranch(), !isSkipTag());
            }
            gitPush(developmentBranch, !isSkipTag());
        }
        if (isDetachReleaseCommit()) {
            // make sure we leave the workspace in the state as released
            gitCheckout(releaseCommit);
        }
    }

}
