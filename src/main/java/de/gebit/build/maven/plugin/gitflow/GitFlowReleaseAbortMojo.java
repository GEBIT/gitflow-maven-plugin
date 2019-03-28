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
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort a release previously started with <code>flow:release-start</code>.
 * <p>
 * Switches back to development branch and deletes the release branch.
 *
 * @see GitFlowReleaseStartMojo
 * @see GitFlowReleaseAbortMojo
 * @since 1.3.1
 */
@Mojo(name = GitFlowReleaseAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowReleaseAbortMojo extends AbstractGitFlowReleaseMojo {

    static final String GOAL = "release-abort";

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        checkCentralBranchConfig();
        abortRelease();
    }

    @Override
    protected boolean isSkipTestProject() {
        throw new IllegalStateException("release-abort does not test the project.");
    }

    @Override
    protected boolean isSkipDeployProject() {
        throw new IllegalStateException("release-abort does not deploy the project.");
    }

    @Override
    protected boolean isSkipTag() {
        throw new IllegalStateException("release-abort does not tag the project.");
    }

    @Override
    protected boolean isKeepBranch() {
        throw new IllegalStateException("release-abort does not delete the release branch project.");
    }

    @Override
    protected boolean isReleaseMergeNoFF() {
        throw new IllegalStateException("release-abort does not commit the release project.");
    }

    @Override
    protected boolean isReleaseMergeProductionNoFF() {
        throw new IllegalStateException("release-abort does not commit the release project.");
    }

    @Override
    protected boolean isDetachReleaseCommit() {
        throw new IllegalStateException("release-abort does not commit the release project.");
    }

    @Override
    protected boolean isSameBranchName() {
        throw new IllegalStateException("release-abort does not create the release branch.");
    }

    @Override
    protected String[] getReleaseGoals() {
        throw new IllegalStateException("release-abort does not build the release.");
    }

    @Override
    protected String getDeployReplacement() {
        throw new IllegalStateException("release-abort does not build the release.");
    }

    @Override
    protected String getReleaseVersion() {
        throw new IllegalStateException("release-abort does not set release version.");
    }

    @Override
    protected boolean isPushReleaseBranch() {
        throw new IllegalStateException("release-abort does not push release branch.");
    }

    @Override
    protected String getDevelopmentVersion() {
        throw new IllegalStateException("release-abort does not set the next development version project.");
    }

    @Override
    protected boolean isAllowSameVersion() {
        throw new IllegalStateException("release-abort does not use property allowSameVersion.");
    }

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }
}
