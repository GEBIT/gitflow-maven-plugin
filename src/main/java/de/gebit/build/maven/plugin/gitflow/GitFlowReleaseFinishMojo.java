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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow release finish mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "release-finish", aggregator = true)
public class GitFlowReleaseFinishMojo extends AbstractGitFlowMojo {

    /** Whether to skip tagging the release in Git. */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /** Whether to keep release branch after finish. */
    @Parameter(property = "keepBranch", defaultValue = "false")
    private boolean keepBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     * 
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to skip calling Maven release goals before releasing.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "skipDeployProject", defaultValue = "false")
    private boolean skipDeployProject = false;

    /**
     * Whether to rebase branch or merge. If <code>true</code> then rebase will
     * be performed.
     * 
     * @since 1.2.3
     */
    @Parameter(property = "releaseRebase", defaultValue = "false")
    private boolean releaseRebase = false;

    /**
     * Whether to use <code>--no-ff</code> option when merging.
     * 
     * @since 1.2.3
     */
    @Parameter(property = "releaseMergeNoFF", defaultValue = "true")
    private boolean releaseMergeNoFF = true;

    /**
     * Goals to perform on release, before tagging and pushing. A useful combination is <code>deploy site</code>. You
     * may specifify multiple entries, they are perfored 
     * 
     * @since 1.3.0
     * @since 1.3.9 you can specify multiple entries
     */
    @Parameter(property = "releaseGoals", defaultValue = "${releaseGoals}")
    private String[] releaseGoals;

    /**
     * Version to set for the next development iteration. If not specified you
     * will be asked for the version (in interactive mode), in batch mode the
     * default will be used (current version with stripped SNAPSHOT incremented
     * and SNAPSHOT added).
     * 
     * @since 1.3.10
     */
    @Parameter(property = "developmentVersion", required = false)
    private String developmentVersion;

    /**
     * When you are releasing using a CI infrastructure the actual deployment might be suppressed until the task
     * is finished (to make sure every module is deployable). But at this point your checkout is already in the state
     * for the next development version. Enable this option to checkout the release commit after finishing, which will
     * result in a detached HEAD (you are on no branch then).
     * 
     * Note that this option implies installProject=false, as otherwise the build artifacts could not be preserved.
     * 
     * @since 1.3.11
     */
    @Parameter(property = "detachReleaseCommit", required = false, defaultValue = "false")
    private boolean detachReleaseCommit; 

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // check uncommitted changes
            checkUncommittedChanges();

            // check snapshots dependencies
            if (!allowSnapshots) {
                checkSnapshotDependencies();
            }

            // git for-each-ref --format='%(refname:short)' refs/heads/release/*
            final String releaseBranch = gitFindBranches(
                    gitFlowConfig.getReleaseBranchPrefix(), false).trim();

            if (StringUtils.isBlank(releaseBranch)) {
                throw new MojoFailureException("There is no release branch.");
            } else if (StringUtils.countMatches(releaseBranch,
                    gitFlowConfig.getReleaseBranchPrefix()) > 1) {
                throw new MojoFailureException(
                        "More than one release branch exists. Cannot finish release.");
            }

            // fetch and check remote
            String branch = getDevelopmentBranchForRelease();
            boolean releaseOnSupportBranch = branch.startsWith(gitFlowConfig.getSupportBranchPrefix());  
            if (fetchRemote) {
                gitFetchRemoteAndCompare(branch);
                if (!releaseOnSupportBranch && !gitFlowConfig.isNoProduction()) {
                    gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
                }
            }

            if (!skipTestProject) {
                if (!releaseOnSupportBranch) {
                    // git checkout release/...
                    gitCheckout(releaseBranch);
                }
                // mvn clean test
                mvnCleanTest();
            }

            // perform the release goals
            if (!skipDeployProject && releaseGoals != null) {
                for (String goals : releaseGoals) {
                    mvnGoals(goals);
                }
            }

            // git checkout master
            gitCheckout(releaseOnSupportBranch || gitFlowConfig.isNoProduction() ? 
        	    branch : gitFlowConfig.getProductionBranch());

            gitMerge(releaseBranch, releaseRebase, releaseMergeNoFF);

            String releaseCommit = getCurrentCommit();

            String nextSnapshotVersion = null;
            if (developmentVersion == null) {
                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();
    
                if (!skipTag) {
                    String tagVersion = currentVersion;
                    if (tychoBuild && ArtifactUtils.isSnapshot(currentVersion)) {
                        tagVersion = currentVersion.replace("-"
                                + Artifact.SNAPSHOT_VERSION, "");
                    }
    
                    // git tag -a ...
                    gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion,
                            commitMessages.getTagReleaseMessage());
                }
    
                // get next snapshot version
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                            currentVersion);
                    nextSnapshotVersion = versionInfo.getNextVersion()
                            .getSnapshotVersionString();
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
            } else {
                nextSnapshotVersion = developmentVersion;
            }

            if (StringUtils.isBlank(nextSnapshotVersion)) {
                throw new MojoFailureException(
                        "Next snapshot version is blank.");
            }

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(nextSnapshotVersion);

            // git commit -a -m updating for next development version
            gitCommit(commitMessages.getReleaseFinishMessage());

            if (!detachReleaseCommit && installProject) {
                // mvn clean install
                mvnCleanInstall();
            }

            if (!keepBranch) {
                // git branch -d release/...
                gitBranchDelete(releaseBranch);
            }

            if (pushRemote) {
                if (!releaseOnSupportBranch && !gitFlowConfig.isNoProduction()) {
                    gitPush(gitFlowConfig.getProductionBranch(), !skipTag);
                }
                gitPush(branch, !skipTag);
            }
            if (detachReleaseCommit) {
                // make sure we leave the workspace in the state as released
                gitCheckout(releaseCommit);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
