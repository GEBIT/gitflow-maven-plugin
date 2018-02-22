/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
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
 * The git flow feature finish mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "feature-finish", aggregator = true)
public class GitFlowFeatureFinishMojo extends AbstractGitFlowMojo {

    /** Whether to keep feature branch after finish. */
    @Parameter(property = "keepFeatureBranch", defaultValue = "false")
    private boolean keepFeatureBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * Whether to squash feature branch commits into a single commit upon
     * merging.
     *
     * @since 1.2.3
     */
    @Parameter(property = "featureSquash", defaultValue = "false")
    private boolean featureSquash = false;

    /**
     * You can try a rebase of the feature branch skipping the initial commit
     * that update the pom versions just before finishing a feature. The
     * operation will peform a rebase, which may not finish successfully. You
     * can make your changes and run feature-finish again in that case. <br>
     * Note: problems arise if you're modifying the poms near the version
     * number. You will need to fix those conflicts before running
     * feature-finish again, as otherwise the pom will be invalid and the
     * process cannot be started. If you cannot fix the pom into a working state
     * with the current commit you can manually issue a
     * <code>git rebase --continue</code>.
     *
     * @since 1.3.0
     */
    @Parameter(property = "rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // check if rebase in process
            String featureBranchName = gitRebaseBranchInProcess();
            if (featureBranchName == null) {
                // check uncommitted changes
                checkUncommittedChanges();

                // git for-each-ref --format='%(refname:short)'
                // refs/heads/feature/*
                final String featureBranches = gitFindBranches(gitFlowConfig.getFeatureBranchPrefix(), false);

                if (StringUtils.isBlank(featureBranches)) {
                    throw new MojoFailureException("There are no feature branches.");
                }

                final String[] branches = featureBranches.split("\\r?\\n");

                // is the current branch a feature branch?
                String currentBranch = gitCurrentBranch();

                for (int i = 0; i < branches.length; i++) {
                    if (branches[i].equals(currentBranch)) {
                        // we're on a feature branch, no need to ask
                        featureBranchName = currentBranch;
                        getLog().info("Current feature branch: " + featureBranchName);
                        break;
                    }
                }

                if (StringUtils.isBlank(featureBranchName)) {
                    featureBranchName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                            "Choose feature branch to finish", branches,
                            "Feature finish in batch mode can be executed only from feature branch. "
                                    + "Please switch to the feature branch you want to finish or execute feature finish"
                                    + " in interactive mode.");

                    // git checkout feature/...
                    if (fetchRemote) {
                        gitFetchRemoteAndResetIfNecessary(featureBranchName);
                    }
                    gitCheckout(featureBranchName);
                }

                // fetch and check remote feature branch
                if (fetchRemote) {
                    gitFetchRemoteAndCompare(featureBranchName);
                }

                final String featureFinishMessage = substituteInMessage(commitMessages.getFeatureFinishMessage(),
                        featureBranchName);

                if (!skipTestProject) {
                    // mvn clean test
                    mvnCleanTest();
                }

                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();

                final String featureName = featureBranchName.replaceFirst(gitFlowConfig.getFeatureBranchPrefix(), "");

                // git checkout develop after fetch and check remote
                String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
                if (fetchRemote) {
                    gitFetchRemoteAndCompare(baseBranch);
                }
                gitCheckout(baseBranch);
                boolean rebased = false;
                if (rebaseWithoutVersionChange) {
                    String branchPoint = gitBranchPoint(featureBranchName, baseBranch);
                    String firstCommitOnBranch = gitVersionChangeCommitOnBranch(featureBranchName, branchPoint);
                    getLog().debug(
                            "branch point is " + branchPoint + ", version change commit is " + firstCommitOnBranch);
                    if (firstCommitOnBranch != null) {
                        rebased = gitTryRebaseWithoutVersionChange(featureBranchName, branchPoint, firstCommitOnBranch);
                    }
                }
                if (!rebased) {
                    // rebase not configured or not possible, then manually
                    // revert the version
                    gitCheckout(featureBranchName);
                    if (currentVersion.contains("-" + featureName)) {
                        final String version = currentVersion.replaceFirst("-" + featureName, "");
                        // mvn versions:set -DnewVersion=...
                        // -DgenerateBackupPoms=false
                        mvnSetVersions(version);

                        // git commit -a -m updating versions for development
                        // branch
                        gitCommit(featureFinishMessage);
                    }
                }
            } else {
                // continue with the rebase
                gitRebaseContinue();
            }

            // git checkout develop
            String baseBranch = gitFeatureBranchBaseBranch(featureBranchName);
            gitCheckout(baseBranch);

            if (featureSquash) {
                // git merge --squash feature/...
                gitMergeSquash(featureBranchName);
                gitCommit(featureBranchName);
            } else {
                // git merge --no-ff feature/...
                gitMergeNoff(featureBranchName);
            }

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }

            if (!keepFeatureBranch) {
                // git branch -D feature/...
                gitBranchDeleteForce(featureBranchName);

                // delete the remote branch
                if (pushRemote) {
                    gitBranchDeleteRemote(featureBranchName);
                }
            }

            if (pushRemote) {
                gitPush(baseBranch, false, false);
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
