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
 * The git flow release start mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "release-start", aggregator = true)
public class GitFlowReleaseStartMojo extends AbstractGitFlowReleaseMojo {

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
    @Parameter(property = "releaseVersion", required = false)
    private String releaseVersion;

    /**
     * A release branch can be pushed to the remote to prevent concurrent releases. The default is <code>false</code>.
     * 
     * @since 1.5.0
     */
    @Parameter(property = "pushReleaseBranch", required = false)
    private boolean pushReleaseBranch;

    @Override
    protected boolean isSkipTestProject() {
        throw new IllegalStateException("release-start does not test the project.");
    }

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
    protected boolean isReleaseRebase() {
        throw new IllegalStateException("release-start does not test the project.");
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

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            releaseStart();
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
