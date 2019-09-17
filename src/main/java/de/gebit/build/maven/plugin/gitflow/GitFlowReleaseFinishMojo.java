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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Finish the release process previously started with
 * <code>flow:release-start</code>.
 * <p>
 * Tags, deploys, merges to development branch, sets the new development version
 * and finally pushes to remote and deletes release branch.
 *
 * @see GitFlowReleaseStartMojo
 * @see GitFlowReleaseAbortMojo
 */
@Mojo(name = GitFlowReleaseFinishMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowReleaseFinishMojo extends AbstractGitFlowReleaseMojo {

    static final String GOAL = "release-finish";

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
     * A release branch can be pushed to the remote to prevent concurrent releases.
     * The default is <code>false</code>.
     *
     * @since 1.5.0
     */
    @Parameter(property = "flow.pushReleaseBranch", defaultValue = "false")
    private boolean pushReleaseBranch;

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
     * Explicitly allow to have the next development version same as release
     * version.
     *
     * @since 2.1.5
     */
    @Parameter(property = "flow.allowSameVersion", defaultValue = "false")
    private boolean allowSameVersion;

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
        throw new IllegalStateException("release-finish does not create the release branch.");
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
        throw new IllegalStateException("release-finish does not set the release version.");
    }

    @Override
    protected String getDevelopmentVersion() {
        return developmentVersion;
    }

    @Override
    protected boolean isPushReleaseBranch() {
        return pushReleaseBranch;
    }

    @Override
    protected boolean isAllowSameVersion() {
        return allowSameVersion;
    }

    @Override
    protected String getBaseBranch() {
        throw new IllegalStateException("release-finish does not use base branch.");
    }

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting release finish process");
        checkCentralBranchConfig();
        String currentBranch = gitCurrentBranch();
        if (!continueReleaseFinishIfInstallProjectPaused(currentBranch)
                && !continueReleaseFinishIfMergeInProcess(currentBranch)) {
            // check uncommitted changes
            checkUncommittedChanges();

            if (!isReleaseBranch(currentBranch)) {
                throw new GitFlowFailureException("Current branch '" + currentBranch + "' is not a release branch.",
                        "Please switch to the release branch that you want to finish in order to proceed.",
                        "'git checkout BRANCH' to switch to the release branch");
            }
            getMavenLog().info("Release branch to be finished is '" + currentBranch + "'");
            String developmentBranch = gitGetBranchCentralConfig(currentBranch, BranchConfigKeys.BASE_BRANCH);
            getMavenLog().info("Base branch of release branch is '" + developmentBranch + "'");
            if (StringUtils.isBlank(developmentBranch)) {
                throw new GitFlowFailureException(
                        "The release branch '" + currentBranch + "' has no development branch configured.",
                        "Please configure development branch for current release branch first in order to proceed.",
                        "'mvn flow:branch-config -DbranchName=" + currentBranch
                                + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' "
                                + "to configure development branch");
            }
            gitEnsureLocalBranchExists(developmentBranch, new GitFlowFailureInfo(
                    "The development branch '" + developmentBranch + "' configured for the current release branch '"
                            + currentBranch + "' doesn't exist.\nThis indicates either a wrong configuration for "
                            + "the release branch or a severe error condition on your branches.",
                    "Please configure correct development branch for the current release branch or consult a "
                            + "gitflow expert on how to fix this.",
                    "'mvn flow:branch-config -DbranchName=" + currentBranch
                            + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to configure "
                            + "correct development branch"));

            gitAssertRemoteBranchNotAheadOfLocalBranche(currentBranch,
                    new GitFlowFailureInfo(
                            "Remote release branch '{0}' is ahead of the local branch.\n"
                                    + "This indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"),
                    new GitFlowFailureInfo(
                            "Remote and local release branches '{0}' diverge.\n"
                                    + "This indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"));

            String productionBranch = getProductionBranchForDevelopmentBranch(developmentBranch);
            gitEnsureLocalBranchIsUpToDateIfExists(productionBranch,
                    new GitFlowFailureInfo(
                            "Remote and local production branches '{0}' diverge.\n"
                                    + "This indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"));

            releaseFinish(developmentBranch);
            getMavenLog().info("Release finish process finished");
        }
    }
}
