//
// GitFlowEpicUpdateAbortMojo.java
//
// Copyright (C) 2019
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
 * Abort paused epic update process.
 * <p>
 * Roll back the epic update process that was started during execution of
 * <code>flow:epic-update</code> and interrupted because of rebase/merge
 * conflicts or failed project build.
 * <p>
 * Example:
 * 
 * <pre>
 * mvn flow:epic-update-abort
 * </pre>
 *
 * @author Volodja
 * @see GitFlowEpicUpdateMojo
 * @since 2.1.7
 */
@Mojo(name = GitFlowEpicUpdateAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowEpicUpdateAbortMojo extends AbstractGitFlowEpicMojo {

    static final String GOAL = "epic-update-abort";

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic update abort process");
        checkCentralBranchConfig();
        boolean mergeInProgress = false;
        String epicBranch = gitRebaseEpicBranchInProcess();
        if (epicBranch == null) {
            epicBranch = gitMergeIntoEpicBranchInProcess();
            if (epicBranch != null) {
                mergeInProgress = true;
            }
        }
        if (epicBranch != null) {
            if (mergeInProgress) {
                if (!getPrompter().promptConfirmation("You have a merge in process on your current branch.\n"
                        + "Are you sure you want to abort the epic update process?", true, true)) {
                    throw new GitFlowFailureException("Aborting epic update process aborted by user.", null);
                }
                getMavenLog().info("Aborting merge in progress");
                gitMergeAbort();
                getMavenLog().info("Switching to epic branch '" + epicBranch + "'");
                gitCheckout(epicBranch);
                // git reset epic branch to initial state
                String oldEpicHEAD = gitGetBranchLocalConfig(epicBranch, "oldEpicHEAD");
                if (oldEpicHEAD != null) {
                    String currentCommit = getCurrentCommit();
                    if (!oldEpicHEAD.equals(currentCommit)) {
                        gitResetHard(oldEpicHEAD);
                    }
                }

            } else {
                if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch.\n"
                        + "Are you sure you want to abort the epic update process?", true, true)) {
                    throw new GitFlowFailureException("Aborting epic update process aborted by user.", null);
                }
                getMavenLog().info("Aborting rebase in progress");
                gitRebaseAbort();
                getMavenLog().info("Switching to epic branch '" + epicBranch + "'");
                gitCheckout(epicBranch);
                String tempEpicBranch = createTempEpicBranchName(epicBranch);
                if (gitBranchExists(tempEpicBranch)) {
                    getLog().info("Deleting temporary branch used for epic rebase.");
                    gitBranchDeleteForce(tempEpicBranch);
                }
            }
            resetBranchLocalConfigs(epicBranch);
        } else {
            boolean aborted = false;
            epicBranch = gitCurrentBranch();
            if (isEpicBranch(epicBranch)) {
                String breakpoint = gitGetBranchLocalConfig(epicBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("epicUpdate.cleanInstall".equals(breakpoint)) {
                        if (!getPrompter().promptConfirmation("You have an interrupted epic update process on your "
                                + "current branch because project installation failed after update.\n"
                                + "Are you sure you want to abort the epic update process and rollback the rebase/merge?",
                                true, true)) {
                            throw new GitFlowFailureException("Aborting epic update process aborted by user.", null);
                        }
                        BranchRefState state = gitCheckBranchReference(epicBranch);
                        if (state != BranchRefState.DIVERGE && state != BranchRefState.LOCAL_AHEAD) {
                            throw new GitFlowFailureException(
                                    "The state of current local and remote branches is unexpected for an interrupted "
                                            + "epic update process.\n"
                                            + "This indicates a severe error condition on your branches.",
                                    "Please consult a gitflow expert on how to fix this!");
                        }
                        // git reset epic branch to initial state
                        String oldEpicHEAD = gitGetBranchLocalConfig(epicBranch, "oldEpicHEAD");
                        if (oldEpicHEAD != null) {
                            String currentCommit = getCurrentCommit();
                            if (!oldEpicHEAD.equals(currentCommit)) {
                                gitResetHard(oldEpicHEAD);
                            }
                            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
                            branchConfigChanges.set(epicBranch, BranchConfigKeys.BASE_VERSION,
                                    gitGetBranchLocalConfig(epicBranch, "oldBaseVersion"));
                            branchConfigChanges.set(epicBranch, BranchConfigKeys.START_COMMIT_MESSAGE,
                                    gitGetBranchLocalConfig(epicBranch, "oldStartCommitMessage"));
                            branchConfigChanges.set(epicBranch, BranchConfigKeys.VERSION_CHANGE_COMMIT,
                                    gitGetBranchLocalConfig(epicBranch, "oldVersionChangeCommit"));
                            gitApplyBranchCentralConfigChanges(branchConfigChanges,
                                    "epic update aborted on '" + epicBranch + "'");
                            resetBranchLocalConfigs(epicBranch);
                            aborted = true;
                        } else {
                            throw new GitFlowFailureException(
                                    "Couldn't find the old HEAD commit of epic branch in local git config.\n"
                                            + "This indicates a severe error condition in the git config.",
                                    "Please consult a gitflow expert on how to fix this!");
                        }
                    }
                }
            }
            if (!aborted) {
                throw new GitFlowFailureException("No interrupted epic update process detected. Nothing to abort.",
                        null);
            }
        }
        getMavenLog().info("Epic update abort process finished");
    }

    private void resetBranchLocalConfigs(String epicBranch) throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(epicBranch, "breakpoint");
        gitRemoveBranchLocalConfig(epicBranch, "baseVersion");
        gitRemoveBranchLocalConfig(epicBranch, "newBaseVersion");
        gitRemoveBranchLocalConfig(epicBranch, "newStartCommitMessage");
        gitRemoveBranchLocalConfig(epicBranch, "newVersionChangeCommit");
        gitRemoveBranchLocalConfig(epicBranch, "oldEpicHEAD");
        gitRemoveBranchLocalConfig(epicBranch, "oldEpicVersion");
        gitRemoveBranchLocalConfig(epicBranch, "oldBaseVersion");
        gitRemoveBranchLocalConfig(epicBranch, "oldStartCommitMessage");
        gitRemoveBranchLocalConfig(epicBranch, "oldVersionChangeCommit");
    }

}
