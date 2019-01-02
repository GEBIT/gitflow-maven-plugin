//
// GitFlowFeatureRebaseAbortMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort a rebase or marge in process that was started during execution of
 * <code>flow:feature-rebase</code>.
 *
 * @author Volodymyr Medvid
 * @see GitFlowFeatureRebaseMojo
 * @since 2.0.1
 */
@Mojo(name = GitFlowFeatureRebaseAbortMojo.GOAL, aggregator = true)
public class GitFlowFeatureRebaseAbortMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-rebase-abort";

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature rebase abort process");
        checkCentralBranchConfig();
        boolean mergeInProgress = false;
        String featureBranch = gitRebaseFeatureBranchInProcess();
        if (featureBranch == null) {
            featureBranch = gitMergeIntoFeatureBranchInProcess();
            if (featureBranch != null) {
                mergeInProgress = true;
            }
        }
        if (featureBranch == null) {
            throw new GitFlowFailureException(
                    "No rebase of feature branch or merge into feature branch detected. Nothing to abort.", null);
        }
        if (mergeInProgress) {
            if (!getPrompter().promptConfirmation("You have a merge in process on your current branch. "
                    + "Are you sure you want to abort the feature rebase process?", true, true)) {
                throw new GitFlowFailureException("Aborting feature rebase process aborted by user.", null);
            }
            getMavenLog().info("Aborting merge in progress");
            gitMergeAbort();
            getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
            gitCheckout(featureBranch);
            gitRemoveBranchLocalConfig(featureBranch, "oldFeatureVersion");
            gitRemoveBranchLocalConfig(featureBranch, "breakpoint");
        } else {
            if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                    + "Are you sure you want to abort the feature rebase process?", true, true)) {
                throw new GitFlowFailureException("Aborting feature rebase process aborted by user.", null);
            }
            getMavenLog().info("Aborting rebase in progress");
            gitRebaseAbort();
            getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
            gitCheckout(featureBranch);
            gitRemoveBranchLocalConfig(featureBranch, "newBaseVersion");
            gitRemoveBranchLocalConfig(featureBranch, "newStartCommitMessage");
            gitRemoveBranchLocalConfig(featureBranch, "newVersionChangeCommit");
            gitRemoveBranchLocalConfig(featureBranch, "oldFeatureVersion");
            gitRemoveBranchLocalConfig(featureBranch, "breakpoint");
            gitRemoveBranchLocalConfig(featureBranch, "rebasedWithoutVersionChangeCommit");
            String tempFeatureBranch = createTempFeatureBranchName(featureBranch);
            if (gitBranchExists(tempFeatureBranch)) {
                getLog().info("Deleting temporary branch used for feature rebase.");
                gitBranchDeleteForce(tempFeatureBranch);
            }
        }
        getMavenLog().info("Feature rebase abort process finished");
    }

}
