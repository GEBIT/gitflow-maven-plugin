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
@Mojo(name = GitFlowFeatureRebaseAbortMojo.GOAL, aggregator = true, threadSafe = true)
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
        if (featureBranch != null) {
            if (mergeInProgress) {
                if (!getPrompter().promptConfirmation("You have a merge in process on your current branch. "
                        + "Are you sure you want to abort the feature rebase process?", true, true)) {
                    throw new GitFlowFailureException("Aborting feature rebase process aborted by user.", null);
                }
                getMavenLog().info("Aborting merge in progress");
                gitMergeAbort();
                getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
                gitCheckout(featureBranch);
            } else {
                if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                        + "Are you sure you want to abort the feature rebase process?", true, true)) {
                    throw new GitFlowFailureException("Aborting feature rebase process aborted by user.", null);
                }
                getMavenLog().info("Aborting rebase in progress");
                gitRebaseAbort();
                getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
                gitCheckout(featureBranch);
                String tempFeatureBranch = createTempFeatureBranchName(featureBranch);
                if (gitBranchExists(tempFeatureBranch)) {
                    getLog().info("Deleting temporary branch used for feature rebase.");
                    gitBranchDeleteForce(tempFeatureBranch);
                }
            }
            resetBranchLocalConfigs(featureBranch);
        } else {
            boolean aborted = false;
            featureBranch = gitCurrentBranch();
            if (isFeatureBranch(featureBranch)) {
                String breakpoint = gitGetBranchLocalConfig(featureBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("featureRebase.cleanInstall".equals(breakpoint)) {
                        if (!getPrompter().promptConfirmation("You have an interrupted feature rebase process on your "
                                + "current branch because project installation failed after rebase.\n"
                                + "Are you sure you want to abort the feature rebase process and rollback the rebase?",
                                true, true)) {
                            throw new GitFlowFailureException("Aborting feature rebase process aborted by user.", null);
                        }
                        BranchRefState state = gitCheckBranchReference(featureBranch);
                        if (state != BranchRefState.DIVERGE && state != BranchRefState.LOCAL_AHEAD) {
                            throw new GitFlowFailureException(
                                    "The state of current local and remote branches is unexpected for an interrupted "
                                            + "feature rebase process.\n"
                                            + "This indicates a severe error condition on your branches.",
                                    "Please consult a gitflow expert on how to fix this!");
                        }
                        String oldFeatureHEAD = gitGetBranchLocalConfig(featureBranch, "oldFeatureHEAD");
                        if (oldFeatureHEAD != null) {
                            String currentCommit = getCurrentCommit();
                            if (!oldFeatureHEAD.equals(currentCommit)) {
                                gitResetHard(oldFeatureHEAD);
                            }
                            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
                            branchConfigChanges.set(featureBranch, BranchConfigKeys.BASE_VERSION,
                                    gitGetBranchLocalConfig(featureBranch, "oldBaseVersion"));
                            branchConfigChanges.set(featureBranch, BranchConfigKeys.START_COMMIT_MESSAGE,
                                    gitGetBranchLocalConfig(featureBranch, "oldStartCommitMessage"));
                            branchConfigChanges.set(featureBranch, BranchConfigKeys.VERSION_CHANGE_COMMIT,
                                    gitGetBranchLocalConfig(featureBranch, "oldVersionChangeCommit"));
                            gitApplyBranchCentralConfigChanges(branchConfigChanges,
                                    "feature rebase aborted on '" + featureBranch + "'");
                            resetBranchLocalConfigs(featureBranch);
                            aborted = true;
                        } else {
                            throw new GitFlowFailureException(
                                    "Couldn't find the old HEAD commit of feature branch in local git config.\n"
                                            + "This indicates a severe error condition in the git config.",
                                    "Please consult a gitflow expert on how to fix this!");
                        }
                    }
                }
            }
            if (!aborted) {
                throw new GitFlowFailureException("No interrupted feature rebase process detected. Nothing to abort.",
                        null);
            }
        }
        getMavenLog().info("Feature rebase abort process finished");
    }

    private void resetBranchLocalConfigs(String featureBranch) throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(featureBranch, "breakpoint");
        gitRemoveBranchLocalConfig(featureBranch, "newBaseVersion");
        gitRemoveBranchLocalConfig(featureBranch, "newStartCommitMessage");
        gitRemoveBranchLocalConfig(featureBranch, "newVersionChangeCommit");
        gitRemoveBranchLocalConfig(featureBranch, "rebasedWithoutVersionChangeCommit");
        gitRemoveBranchLocalConfig(featureBranch, "oldFeatureHEAD");
        gitRemoveBranchLocalConfig(featureBranch, "oldFeatureVersion");
        gitRemoveBranchLocalConfig(featureBranch, "oldBaseVersion");
        gitRemoveBranchLocalConfig(featureBranch, "oldStartCommitMessage");
        gitRemoveBranchLocalConfig(featureBranch, "oldVersionChangeCommit");
    }

}
