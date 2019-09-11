/*
 * Copyright 2017 GEBIT Solutions GmbH
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
 * Mark the current commit as integrated.
 * <p>
 * Updates the integration branch associated with the current branch to the same
 * reference to mark a successful integration (e.g. after a build is run without
 * any errors).
 *
 * @since 1.5.10
 * @author Erwin Tratar
 */
@Mojo(name = GitFlowIntegratedMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowIntegratedMojo extends AbstractGitFlowMojo {

    static final String GOAL = "integrated";

    /**
     * Specifies an integration branch to update. If not provided a default is
     * computed using {@link GitFlowConfig#getIntegrationBranchPrefix()}.
     */
    @Parameter(property = "integrationBranch", readonly = true)
    private String integrationBranch;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting integration process");
        checkCentralBranchConfig();

        if (pushRemote) {
            gitAssertCurrentLocalBranchNotAheadOfRemoteBranche(
                    new GitFlowFailureInfo(
                            "Current local branch '{0}' is ahead of remote branch. Pushing of the integration "
                                    + "branch will create an inconsistent state in remote repository.",
                            "Push the current branch '{0}' first or set 'pushRemote' parameter to false in "
                                    + "order to avoid inconsistent state in remote repository."),
                    new GitFlowFailureInfo(
                            "Current local and remote branches '{0}' diverge. Pushing of the integration "
                                    + "branch will create an inconsistent state in remote repository.",
                            "Rebase the changes in local branch '{0}' first or set 'pushRemote' parameter to false in "
                                    + "order to avoid inconsistent state in remote repository."),
                    new GitFlowFailureInfo(
                            "Current branch '{0}' doesn't exist remotely. Pushing of the integration "
                                    + "branch will create an inconsistent state in remote repository.",
                            "Push the current branch '{0}' first or set 'pushRemote' parameter to "
                                    + "false in order to avoid inconsistent state in remote repository."));
        }
        integrationBranch = getPrompter().promptRequiredParameterValue("What is the integration branch name?",
                "integrationBranch", integrationBranch,
                gitFlowConfig.getIntegrationBranchPrefix() + gitCurrentBranch());

        getMavenLog().info("Updating the reference of integration branch '" + integrationBranch + "' to HEAD");
        gitUpdateRef(integrationBranch, "HEAD");
        if (pushRemote) {
            getMavenLog().info("Pushing integration branch '" + integrationBranch + "' to remote repository");
            gitPush(integrationBranch, false, false);
        }
        getMavenLog().info("Integration process finished");
    }
}
