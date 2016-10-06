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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature update mojo. Will either rebase the feature branch on the current development branch or merge
 * the development branch into the feature branch.
 * 
 * @author Erwin Tratar
 * @since 1.3.1
 */
@Mojo(name = "feature-rebase", aggregator = true)
public class GitFlowFeatureRebaseMojo extends AbstractGitFlowMojo {

    /**
     * Controls whether a merge of the development branch instead of a rebase on the development branch is performed.
     * @since 1.3.0
     */
    @Parameter(property = "updateWithMerge", defaultValue = "false")
    private boolean updateWithMerge = false;

    /**
     * This property applies mainly to <code>feature-finish</code>, but if it is set a merge at this point would
     * make a later rebase impossible. So we use this property to decide wheter a warning needs to be issued.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String featureBranchName = updateWithMerge ? gitMergeBranchInProcess() : gitRebaseBranchInProcess(); 
            if (featureBranchName == null) {
                // check uncommitted changes
                checkUncommittedChanges();

                // git for-each-ref --format='%(refname:short)' refs/heads/feature/*
                final String featureBranches = gitFindBranches(
                        gitFlowConfig.getFeatureBranchPrefix(), false);

                if (StringUtils.isBlank(featureBranches)) {
                    throw new MojoFailureException("There are no feature branches.");
                }

                // fetch and check remote
                if (fetchRemote) {
                    gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
                }

                final String[] branches = featureBranches.split("\\r?\\n");

                List<String> numberedList = new ArrayList<String>();
                StringBuilder str = new StringBuilder("Feature branches:")
                        .append(LS);
                for (int i = 0; i < branches.length; i++) {
                    str.append((i + 1) + ". " + branches[i] + LS);
                    numberedList.add(String.valueOf(i + 1));
                }
                str.append("Choose feature branch to update");

                String featureNumber = null;
                try {
                    while (StringUtils.isBlank(featureNumber)) {
                        featureNumber = prompter.prompt(str.toString(),
                                numberedList);
                    }
                } catch (PrompterException e) {
                    getLog().error(e);
                }

                if (featureNumber != null) {
                    int num = Integer.parseInt(featureNumber);
                    featureBranchName = branches[num - 1];
                }

                if (StringUtils.isBlank(featureBranchName)) {
                    throw new MojoFailureException(
                            "Feature branch name to finish is blank.");
                }

                // git checkout feature/...
                gitCheckout(featureBranchName);

                if (updateWithMerge && rebaseWithoutVersionChange) {
                    try {
                        final String reply = prompter.prompt("Updating is configured for merges, a later rebase will not be possible. Continue? (yes/no)", "no");
                        if (reply == null || !reply.toLowerCase().equals("yes")) {
                            return;
                        }
                    } catch (PrompterException e) {
                        getLog().error(e);
                        return;
                    }
                }

                // merge in development
                gitMerge(gitFlowConfig.getDevelopmentBranch(), !updateWithMerge, true);
            } else {
                if (updateWithMerge) {
                    // continue with commit
                    gitCommitMerge();
                } else {
                    // continue with the rebase
                    gitRebaseContinue();
                }
            }

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }

            if (pushRemote) {
                if (!updateWithMerge) {
                    // delete remote branch to not run into non-fast-forward error
                    gitBranchDeleteRemote(featureBranchName);
                }
                gitPush(gitFlowConfig.getDevelopmentBranch(), false);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
