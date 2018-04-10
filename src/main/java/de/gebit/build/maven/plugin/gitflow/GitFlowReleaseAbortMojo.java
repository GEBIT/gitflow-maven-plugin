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

import java.util.List;

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
        String releaseBranch;
        String currentBranch = gitCurrentBranch();
        if (isReleaseBranch(currentBranch)) {
            releaseBranch = currentBranch;
            String developmentBranch = gitGetBranchConfig(releaseBranch, "development");
            if (StringUtils.isBlank(developmentBranch)) {
                developmentBranch = gitFlowConfig.getDevelopmentBranch();
            }
            if (!gitLocalOrRemoteBranchesExist(developmentBranch)) {
                if (!gitFlowConfig.getDevelopmentBranch().equals(developmentBranch)) {
                    developmentBranch = gitFlowConfig.getDevelopmentBranch();
                    if (!gitLocalOrRemoteBranchesExist(developmentBranch)) {
                        developmentBranch = null;
                    }
                } else {
                    developmentBranch = null;
                }
            }
            if (developmentBranch != null) {
                if (executeGitHasUncommitted()) {
                    boolean confirmed = getPrompter().promptConfirmation(
                            "You have some uncommitted files. If you continue any changes will be discarded. Continue?",
                            false, true);
                    if (!confirmed) {
                        throw new GitFlowFailureException(
                                "You have aborted release-abort process because of uncommitted files.",
                                "Commit or discard local changes in order to proceed.",
                                "'git add' and 'git commit' to commit your changes",
                                "'git reset --hard' to throw away your changes");
                    }
                    gitResetHard();
                }
                gitCheckout(developmentBranch);
            } else {
                throw new GitFlowFailureException(
                        "No development branch found for current release branch. Cannot abort release.\n"
                                + "This indicates a severe error condition on your branches.",
                        "Please configure correct development branch for the current release branch or consult a "
                                + "gitflow expert on how to fix this.",
                        "'git config branch." + releaseBranch
                                + ".development [development branch name]' to configure correct development branch");
            }
        } else {
            List<String> releaseBranches = gitAllBranches(gitFlowConfig.getReleaseBranchPrefix());
            if (releaseBranches.isEmpty()) {
                throw new GitFlowFailureException("There are no release branches in your repository.", null);
            }
            if (releaseBranches.size() > 1) {
                throw new GitFlowFailureException(
                        "More than one release branch exists. Cannot abort release from non-release branch.",
                        "Please switch to a release branch first in order to proceed.",
                        "'git checkout BRANCH' to switch to the release branch");
            }
            releaseBranch = releaseBranches.get(0);
        }

        if (gitBranchExists(releaseBranch)) {
            // git branch -D release/...
            gitBranchDeleteForce(releaseBranch);
        }

        if (pushRemote) {
            // delete the remote branch
            gitBranchDeleteRemote(releaseBranch);
        }
    }
}
