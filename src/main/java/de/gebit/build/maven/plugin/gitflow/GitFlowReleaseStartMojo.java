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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Start a new release of your project.
 * <p>
 * Creates a release branch, updates the versions in all pom.xml and pushes it to
 * the remote.
 * <p>
 * After pushing nobody else can start a second release process. You may
 * now perform additional release related changes (e.g. updating some
 * documents...) before finishing. You can always abort a release (no harm
 * done...) by calling the <code>flow:release-abort</code> goal.
 *
 * @see GitFlowReleaseFinishMojo
 * @see GitFlowReleaseAbortMojo
 */
@Mojo(name = GitFlowReleaseStartMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowReleaseStartMojo extends AbstractGitFlowReleaseMojo {

    static final String GOAL = "release-start";

    /**
     * Whether to use the same name of the release branch for every release.
     * Default is <code>false</code>, i.e. project version will be added to
     * release branch prefix. <br/>
     * <br/>
     *
     * Note: By itself the default releaseBranchPrefix is not a valid branch
     * name. You must change it when setting sameBranchName to <code>true</code>
     * .
     *
     * @since 1.2.0
     */
    @Parameter(property = "sameBranchName", defaultValue = "false")
    private boolean sameBranchName = false;

    /**
     * Version to set for the release. If not specified you will be asked for
     * the version (in interactive mode), in batch mode the default will be used
     * (current version with stripped SNAPSHOT).
     *
     * @since 1.3.10
     */
    @Parameter(property = "releaseVersion", readonly = true)
    private String releaseVersion;

    /**
     * A release branch can be pushed to the remote to prevent concurrent
     * releases. The default is <code>false</code>.
     *
     * @since 1.5.0
     */
    @Parameter(property = "flow.pushReleaseBranch", defaultValue = "false")
    private boolean pushReleaseBranch;

    /**
     * Whether to clean-up possibly failed or not finished release before starting a
     * new release.
     *
     * @since 2.1.8
     */
    @Parameter(property = "flow.cleanupReleaseBeforeStart", defaultValue = "false")
    private boolean cleanupReleaseBeforeStart;

    /**
     * The base branch which release branch should be started on.
     *
     * @since 2.2.0
     */
    @Parameter(property = "baseBranch", readonly = true)
    protected String baseBranch;

    /**
     * The base commit which release branch should be started on. Also a tag
     * name can be used as base commit. If <code>baseCommit</code> is not on
     * current branch, a <code>baseBranch</code> that contains the base commit
     * must be specified too.
     *
     * @since 2.2.0
     */
    @Parameter(property = "baseCommit", readonly = true)
    protected String baseCommit;
    
    /**
     * Whether to call Maven install goal after release start. By default the
     * value of <code>installProject</code> parameter
     * (<code>flow.installProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.installProjectOnReleaseStart")
    private Boolean installProjectOnReleaseStart;
    
    /**
     * Maven goals (separated by space) to be used after release start. By
     * default the value of <code>installProjectGoals</code> parameter
     * (<code>flow.installProjectGoals</code> property) is used.
     *
     * @since 2.3.1
     */
    @Parameter(property = "flow.installProjectGoalsOnReleaseStart")
    private String installProjectGoalsOnReleaseStart;

    @Override
    protected boolean isSkipDeployProject() {
        throw new IllegalStateException("release-start does not deploy the project.");
    }

    @Override
    protected boolean isSkipTag() {
        throw new IllegalStateException("release-start does not tag the project.");
    }

    @Override
    protected boolean isKeepBranch() {
        throw new IllegalStateException("release-start does not delete the release branch project.");
    }

    @Override
    protected boolean isReleaseMergeNoFF() {
        throw new IllegalStateException("release-start does not commit the release project.");
    }

    @Override
    protected boolean isReleaseMergeProductionNoFF() {
        throw new IllegalStateException("release-start does not commit the release project.");
    }

    @Override
    protected boolean isDetachReleaseCommit() {
        throw new IllegalStateException("release-start does not commit the release project.");
    }

    @Override
    protected boolean isSameBranchName() {
        return sameBranchName;
    }

    @Override
    protected String[] getReleaseGoals() {
        throw new IllegalStateException("release-start does not build the release.");
    }

    @Override
    protected String getDeployReplacement() {
        throw new IllegalStateException("release-start does not build the release.");
    }

    @Override
    protected String getReleaseVersion() {
        return releaseVersion;
    }

    @Override
    protected boolean isPushReleaseBranch() {
        return pushReleaseBranch;
    }

    @Override
    protected String getDevelopmentVersion() {
        throw new IllegalStateException("release-start does not set the next development version project.");
    }

    @Override
    protected boolean isAllowSameVersion() {
        throw new IllegalStateException("release-start does not use property allowSameVersion.");
    }

    @Override
    protected String getBaseBranch() {
        return baseBranch;
    }

    @Override
    protected String getBaseCommit() {
        return baseCommit;
    }
    
    @Override
    protected String getBranchName() {
        return null;
    }

    @Override
    protected boolean isCleanupReleaseBeforeStart() {
        return cleanupReleaseBeforeStart;
    }

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting release start process");
        checkCentralBranchConfig();
        abortNotFinishedReleaseIfNeeded();
        checkUncommittedChanges();

        releaseStart();
        getMavenLog().info("Release start process finished");
    }
    
    @Override
    protected Boolean getIndividualInstallProjectConfig() {
        return installProjectOnReleaseStart;
    }
    
    @Override
    protected String getIndividualInstallProjectGoals() {
        return installProjectGoalsOnReleaseStart;
    }
}
