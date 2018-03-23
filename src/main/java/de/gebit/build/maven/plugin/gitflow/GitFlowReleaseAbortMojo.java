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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow release abort mojo.
 *
 * @author Erwin Tratar
 * @since 1.3.1
 */
@Mojo(name = "release-abort", aggregator = true)
public class GitFlowReleaseAbortMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check uncommitted changes
        checkUncommittedChanges();

        // git for-each-ref --format='%(refname:short)' refs/heads/release/*
        final String releaseBranch = gitFindBranches(gitFlowConfig.getReleaseBranchPrefix(), false).trim();

        if (StringUtils.isBlank(releaseBranch)) {
            throw new MojoFailureException("There is no release branch.");
        } else if (StringUtils.countMatches(releaseBranch, gitFlowConfig.getReleaseBranchPrefix()) > 1) {
            throw new MojoFailureException("More than one release branch exists. Cannot abort release.");
        }

        String gitConfigName = "branch.\"" + releaseBranch + "\".development";
        String developmentBranch = gitGetConfig(gitConfigName);
        if (developmentBranch == null || developmentBranch.isEmpty()) {
            developmentBranch = gitFlowConfig.getDevelopmentBranch();
        }

        // back to the development branch
        gitCheckout(developmentBranch);

        // git branch -D release/...
        gitBranchDeleteForce(releaseBranch);

    }
}
