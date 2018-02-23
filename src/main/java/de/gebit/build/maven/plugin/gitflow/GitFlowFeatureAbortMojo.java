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
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature abort mojo.
 *
 * @author Erwin Tratar
 * @since 1.3.1
 */
@Mojo(name = "feature-abort", aggregator = true)
public class GitFlowFeatureAbortMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // check if rebase in process
            String featureBranchName = gitRebaseBranchInProcess();
            if (featureBranchName != null) {
                throw new MojoFailureException("A rebase of the feature branch is in process. Cannot abort now.");
            }

            // check uncommitted changes
            checkUncommittedChanges();

            // git for-each-ref --format='%(refname:short)' refs/heads/feature/*
            final String featureBranches = gitFindBranches(
                    gitFlowConfig.getFeatureBranchPrefix(), false);

            if (StringUtils.isBlank(featureBranches)) {
                throw new MojoFailureException("There are no feature branches.");
            }

            final String[] branches = featureBranches.split("\\r?\\n");

            // is the current branch a feature branch?
            String currentBranch = gitCurrentBranch();

            List<String> numberedList = new ArrayList<String>();
            StringBuilder str = new StringBuilder("Feature branches:")
                    .append(LS);
            for (int i = 0; i < branches.length; i++) {
                str.append((i + 1) + ". " + branches[i] + LS);
                numberedList.add(String.valueOf(i + 1));
                if (branches[i].equals(currentBranch)) {
                    // we're on a feature branch, no need to ask
                    featureBranchName = currentBranch;
                    getLog().info("Current feature branch: " + featureBranchName);
                    break;
                }
            }

            if (featureBranchName == null || StringUtils.isBlank(featureBranchName)) {
                str.append("Choose feature branch to abort");

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
            }
            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);

            // git checkout development branch
            if (fetchRemote) {
                gitFetchRemoteAndResetIfNecessary(baseBranch);
            }
            gitCheckout(baseBranch);

            // git branch -D feature/...
            gitBranchDeleteForce(featureBranchName);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while executing external command.", e);
        }
    }
}
