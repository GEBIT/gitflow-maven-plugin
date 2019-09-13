//
// GitFlowEpicAbortMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abort an epic branch.
 * <p>
 * Abort the implementation of the epic. Selects an epic branch for abortion,
 * switches to development branch and deletes the epic branch.
 *
 * @author Volodymyr Medvid
 * @see GitFlowEpicStartMojo
 * @since 2.0.0
 */
@Mojo(name = GitFlowEpicAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowEpicAbortMojo extends AbstractGitFlowEpicMojo {

    static final String GOAL = "epic-abort";

    /**
     * The epic branch to be aborted.
     *
     * @since 2.2.0
     */
    @Parameter(property = "branchName", readonly = true)
    protected String branchName;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic abort process");
        checkCentralBranchConfig();
        if (gitRebaseInProcess()) {
            throw new GitFlowFailureException("A rebase of the current branch is in process. Cannot abort epic now.",
                    "Finish rebase process first in order to proceed.");
        } else if (gitMergeInProcess()) {
            throw new GitFlowFailureException("A merge into the current branch is in process. Cannot abort epic now.",
                    "Finish merge process first in order to proceed.");
        }

        boolean isOnEpicBranch = false;
        String epicBranchLocalName;
        BranchRef epicBranch;
        String currentBranch = gitCurrentBranch();
        if (StringUtils.isNotEmpty(branchName)) {
            epicBranchLocalName = gitLocalRef(branchName);
            if (!isEpicBranch(epicBranchLocalName)) {
                throw new GitFlowFailureException(
                        "Branch '" + branchName + "' defined in 'branchName' property is not an epic branch.",
                        "Please define an epic branch in order to proceed.");
            }
            isOnEpicBranch = epicBranchLocalName.equals(currentBranch);
            getLog().info("Aborting epic on specified epic branch: " + epicBranchLocalName);
            epicBranch = recognizeRef(branchName,
                    createBranchNotExistingError("Epic branch '" + branchName + "' defined in 'branchName' property",
                            "Please define an existing epic branch in order to proceed."));
        } else {
            List<String> branches = gitAllEpicBranches();
            if (branches.isEmpty()) {
                throw new GitFlowFailureException("There are no epic branches in your repository.", null);
            }
            isOnEpicBranch = branches.contains(currentBranch);
            if (!isOnEpicBranch) {
                epicBranchLocalName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                        "Choose epic branch to abort", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:epic-abort' can be executed only on an epic branch.",
                                "Please switch to an epic branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the epic branch",
                                "'mvn flow:epic-abort' to run in interactive mode"));
                getLog().info("Aborting epic on selected epic branch: " + epicBranchLocalName);
                epicBranch = preferLocalRef(epicBranchLocalName);
            } else {
                epicBranchLocalName = currentBranch;
                getLog().info("Aborting epic on current epic branch: " + epicBranchLocalName);
                if (executeGitHasUncommitted()) {
                    boolean confirmed = getPrompter().promptConfirmation(
                            "You have some uncommitted files. If you continue any changes will be discarded. Continue?",
                            false,
                            new GitFlowFailureInfo("You have some uncommitted files.",
                                    "Commit or discard local changes in order to proceed or run in interactive mode.",
                                    "'git add' and 'git commit' to commit your changes",
                                    "'git reset --hard' to throw away your changes",
                                    "'mvn flow:epic-abort' to run in interactive mode"));
                    if (!confirmed) {
                        throw new GitFlowFailureException(
                                "You have aborted epic-abort process because of uncommitted files.",
                                "Commit or discard local changes in order to proceed.",
                                "'git add' and 'git commit' to commit your changes",
                                "'git reset --hard' to throw away your changes");
                    }
                }
                epicBranch = localRef(epicBranchLocalName);
            }
        }
        
        if (hasCommitsExceptVersionChangeCommitOnEpicBranch(epicBranch)) {
            if (!getPrompter().promptConfirmation("You have commits on the epic branch.\n"
                    + "If you continue all these epic commits will be discarded. Continue?", false, true)) {
                throw new GitFlowFailureException("Epic abort process aborted by user.", null);
            }
        }
        if (isOnEpicBranch) {
            String baseBranch = gitEpicBranchBaseBranch(epicBranchLocalName);
            gitResetHard();
            getMavenLog().info("Switching to base branch '" + baseBranch + "'");
            gitCheckout(baseBranch);
        }

        if (gitBranchExists(epicBranchLocalName)) {
            getMavenLog().info("Removing local epic branch '" + epicBranchLocalName + "'");
            gitBranchDeleteForce(epicBranchLocalName);
        }
        if (pushRemote) {
            getMavenLog().info("Removing remote epic branch '" + epicBranchLocalName + "'");
            gitBranchDeleteRemote(epicBranchLocalName);
        }
        String epicName = epicBranchLocalName.substring(gitFlowConfig.getEpicBranchPrefix().length());
        gitRemoveAllBranchCentralConfigsForBranch(epicBranchLocalName, "epic '" + epicName + "' aborted");
        getMavenLog().info("Epic abort process finished");
    }

}
