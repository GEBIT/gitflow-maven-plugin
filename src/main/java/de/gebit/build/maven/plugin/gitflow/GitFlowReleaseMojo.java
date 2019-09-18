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
 * Start a new release of your project and finish it in a single step. See these
 * goals for documentation.
 * <p>
 * This process cannot be reverted or aborted!
 *
 * @see GitFlowReleaseStartMojo
 * @see GitFlowReleaseFinishMojo
 * @since 1.2.0
 */
@Mojo(name = GitFlowReleaseMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowReleaseMojo extends AbstractGitFlowReleaseMojo {

    static final String GOAL = "release";

    /** Whether to skip tagging the release in Git. */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /**
     * Whether to skip calling Maven test goal before releasing.
     *
     * @since 1.0.5
     */
    @Parameter(property = "flow.skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to skip calling Maven deploy if it is part of the release goals.
     *
     * @since 1.3.0
     * @since 1.4.1
     */
    @Parameter(property = "flow.skipDeployProject", defaultValue = "false")
    private boolean skipDeployProject = false;

    /**
     * Whether to use <code>--no-ff</code> option when merging.
     *
     * @since 1.2.3
     */
    @Parameter(property = "flow.releaseMergeNoFF", defaultValue = "true")
    private boolean releaseMergeNoFF = true;

    /**
     * Whether to use <code>--no-ff</code> option when merging the release branch to
     * production.
     *
     * @since 1.5.0
     */
    @Parameter(property = "flow.releaseMergeProductionNoFF", defaultValue = "true")
    private boolean releaseMergeProductionNoFF = true;

    /**
     * Goals to perform on release, before tagging and pushing. A useful combination
     * is <code>deploy site</code>. You may specifify multiple entries, they are
     * perfored
     *
     * @since 1.3.0
     * @since 1.3.9 you can specify multiple entries
     */
    @Parameter(property = "flow.releaseGoals")
    private String[] releaseGoals;

    /**
     * When {@link #skipDeployProject} is activated the invocation of 'deploy' in
     * {@link #releaseGoals} is suppressed. You can specify a replacement goal that
     * is substituted here (the default is empty).
     *
     * @since 1.5.10
     */
    @Parameter(property = "deployReplacement")
    private String deployReplacement;

    /**
     * Version to set for the next development iteration. If not specified you will
     * be asked for the version (in interactive mode), in batch mode the default
     * will be used (current version with stripped SNAPSHOT incremented and SNAPSHOT
     * added).
     *
     * @since 1.3.10
     */
    @Parameter(property = "developmentVersion", readonly = true)
    private String developmentVersion;

    /**
     * Version to set for the release. If not specified you will be asked for the
     * version (in interactive mode), in batch mode the default will be used
     * (current version with stripped SNAPSHOT).
     *
     * @since 1.3.10
     */
    @Parameter(property = "releaseVersion", readonly = true)
    private String releaseVersion;

    /**
     * When you are releasing using a CI infrastructure the actual deployment might
     * be suppressed until the task is finished (to make sure every module is
     * deployable). But at this point your checkout is already in the state for the
     * next development version. Enable this option to checkout the release commit
     * after finishing, which will result in a detached HEAD (you are on no branch
     * then).
     *
     * Note that this option implies installProject=false, as otherwise the build
     * artifacts could not be preserved.
     *
     * @since 1.3.11
     */
    @Parameter(property = "detachReleaseCommit", defaultValue = "false")
    private boolean detachReleaseCommit;

    /**
     * Whether to keep hotfix branch after finish.
     *
     * @since 1.5.0
     */
    @Parameter(property = "keepBranch", defaultValue = "false")
    private boolean keepBranch = false;

    /**
     * Whether to use the same name of the release branch for every release. Default
     * is <code>false</code>, i.e. project version will be added to release branch
     * prefix. <br/>
     * <br/>
     *
     * Note: By itself the default releaseBranchPrefix is not a valid branch name.
     * You must change it when setting sameBranchName to <code>true</code> .
     *
     * @since 1.5.0
     */
    @Parameter(property = "sameBranchName", defaultValue = "false")
    private boolean sameBranchName = false;

    /**
     * Explicitly allow to have the next development version same as release
     * version.
     *
     * @since 2.1.5
     */
    @Parameter(property = "flow.allowSameVersion", defaultValue = "false")
    private boolean allowSameVersion;

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

    @Override
    protected boolean isSkipTestProject() {
        return skipTestProject;
    }

    @Override
    protected boolean isSkipDeployProject() {
        return skipDeployProject;
    }

    @Override
    protected boolean isSkipTag() {
        return skipTag;
    }

    @Override
    protected boolean isKeepBranch() {
        return keepBranch;
    }

    @Override
    protected boolean isReleaseMergeNoFF() {
        return releaseMergeNoFF;
    }

    @Override
    protected boolean isReleaseMergeProductionNoFF() {
        return releaseMergeProductionNoFF;
    }

    @Override
    protected boolean isDetachReleaseCommit() {
        return detachReleaseCommit;
    }

    @Override
    protected boolean isSameBranchName() {
        return sameBranchName;
    }

    @Override
    protected String[] getReleaseGoals() {
        return releaseGoals;
    }

    @Override
    protected String getDeployReplacement() {
        return deployReplacement;
    }

    @Override
    protected String getReleaseVersion() {
        return releaseVersion;
    }

    @Override
    protected String getDevelopmentVersion() {
        return developmentVersion;
    }

    /**
     * release does not create a release branch.
     */
    @Override
    protected boolean isPushReleaseBranch() {
        return false;
    }

    /**
     * release does not need install as it will go straight to testing
     */
    @Override
    protected boolean isInstallProjectOnStart() {
        return false;
    }

    @Override
    protected boolean isAllowSameVersion() {
        return allowSameVersion;
    }

    @Override
    protected String getBaseBranch() {
        return baseBranch;
    }
    
    @Override
    protected String getBranchName() {
        throw new IllegalStateException("release does not use property branchName.");
    }

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting release process");
        checkCentralBranchConfig();
        abortNotFinishedReleaseIfNeeded(cleanupReleaseBeforeStart);
        String currentBranch = gitCurrentBranch();
        if (!continueReleaseFinishIfInstallProjectPaused(currentBranch)
                && !continueReleaseFinishIfMergeInProcess(currentBranch)) {
            // check uncommitted changes
            checkUncommittedChanges();

            // perform start and finish in one step
            String developmentBranch = releaseStart();
            releaseFinish(developmentBranch);
            getMavenLog().info("Release process finished");
        }
    }
}
