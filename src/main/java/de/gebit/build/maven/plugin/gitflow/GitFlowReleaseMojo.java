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
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow release mojo.
 * 
 * @author Aleksandr Mashchenko
 * @since 1.2.0
 */
@Mojo(name = "release", aggregator = true)
public class GitFlowReleaseMojo extends AbstractGitFlowMojo {

    /** Whether to skip tagging the release in Git. */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /**
     * Whether to skip calling Maven test goal before releasing.
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
     * Version to set for the release. If not specified you will be asked for
     * the version (in interactive mode), in batch mode the default will be used
     * (current version with stripped SNAPSHOT).
     * 
     * @since 1.3.10
     */
    @Parameter(property = "releaseVersion", required = false)
    private String releaseVersion;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // check snapshots dependencies
            if (!allowSnapshots) {
                checkSnapshotDependencies();
            }
            
            String currentBranch = gitCurrentBranch();
            boolean releaseOnSupportBranch = currentBranch.startsWith(gitFlowConfig.getSupportBranchPrefix()); 
            if (releaseOnSupportBranch) {
                gitFetchRemoteAndCompare(currentBranch);
            } else {
                // fetch and check remote
                if (fetchRemote) {
                    gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
                    if (!gitFlowConfig.isNoProduction()) {
                        gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
                    }
                }
                
                // git for-each-ref --count=1 refs/heads/release/*
                final String releaseBranch = gitFindBranches(
                	gitFlowConfig.getReleaseBranchPrefix(), true);
                
                if (StringUtils.isNotBlank(releaseBranch)) {
                    throw new MojoFailureException(
                	    "Release branch already exists. Cannot start release.");
                }
                
                // need to be in develop to get correct project version
                // git checkout develop
                gitCheckout(gitFlowConfig.getDevelopmentBranch());
            }

            if (!skipTestProject) {
                // mvn clean test
                mvnCleanTest();
            }

            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            String version = null;
            if (releaseVersion == null) {
                String defaultVersion = null;
                if (tychoBuild) {
                    defaultVersion = currentVersion;
                } else {
                    // get default release version
                    try {
                        final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                                currentVersion);
                        defaultVersion = versionInfo.getReleaseVersionString();
                    } catch (VersionParseException e) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug(e);
                        }
                    }
                }
    
                if (defaultVersion == null) {
                    throw new MojoFailureException(
                            "Cannot get default project version.");
                }
    
                if (settings.isInteractiveMode()) {
                    try {
                        version = prompter.prompt("What is release version? ["
                                + defaultVersion + "]");
                    } catch (PrompterException e) {
                        getLog().error(e);
                    }
                }
    
                if (StringUtils.isBlank(version)) {
                    version = defaultVersion;
                }
            } else {
                version = releaseVersion;
            }

            // execute if version changed
            if (!version.equals(currentVersion)) {
                // mvn set version
                mvnSetVersions(version);

                // git commit -a -m updating versions for release
                gitCommit(commitMessages.getReleaseStartMessage());
            }

            // perform the release goals
            if (!skipDeployProject && releaseGoals != null) {
                for (String goals : releaseGoals) {
                    mvnGoals(goals);
                }
            }

            // git checkout master
            if (!releaseOnSupportBranch) {
                if (!gitFlowConfig.isNoProduction()) {
                    gitCheckout(gitFlowConfig.getProductionBranch());

                    gitMerge(gitFlowConfig.getDevelopmentBranch(), releaseRebase, releaseMergeNoFF);
                }
            }

            if (!skipTag) {
                if (tychoBuild && ArtifactUtils.isSnapshot(version)) {
                    version = version.replace("-" + Artifact.SNAPSHOT_VERSION,
                            "");
                }

                // git tag -a ...
                gitTag(gitFlowConfig.getVersionTagPrefix() + version,
                        commitMessages.getTagReleaseMessage());
            }

            // git checkout develop
            if (!gitFlowConfig.isNoProduction()) {
        	gitCheckout(gitFlowConfig.getDevelopmentBranch());
            }

            String nextSnapshotVersion = null;
            if (developmentVersion == null) {
                // get next snapshot version
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                            version);
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

            // mvn set version
            mvnSetVersions(nextSnapshotVersion);

            // git commit -a -m updating for next development version
            gitCommit(commitMessages.getReleaseFinishMessage());

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }

            if (pushRemote) {
                if (releaseOnSupportBranch) {
                    gitPush(currentBranch, !skipTag);
                } else {
                    if (!gitFlowConfig.isNoProduction()) {
                        gitPush(gitFlowConfig.getProductionBranch(), !skipTag);
                    }
                    gitPush(gitFlowConfig.getDevelopmentBranch(), !skipTag);
                }
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
