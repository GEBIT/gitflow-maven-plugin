//
// GitFlowFeatureResetToRemoteMojo.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Reset the local feature branch to the state of remote branch.
 * <p>
 * This goal is useful if multiple developers are working on same feature. In
 * order to update the local feature branch after an developer has rebased the
 * feature branch, all other developers can run this goal.
 * <p>
 * Requirement: all developers have pushed all local changes on feature branch
 * before rebase.
 * <p>
 * Example:
 * 
 * <pre>
 * mvn flow:feature-reset-to-remote [-DbranchName=feature/XYZ-123] [-DstashChanges=true]
 * </pre>
 * 
 * @author Volodja
 * @since 2.2.1
 */
@Mojo(name = GitFlowFeatureResetToRemoteMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureResetToRemoteMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-reset-to-remote";

    /**
     * The feature branch to be reset to remote state.
     */
    @Parameter(property = "branchName", readonly = true)
    protected String branchName;

    /**
     * Whether to stash not committed local changes on feature branch before
     * reset and unstash them afterwards. If <code>false</code>, then all not
     * committed changes will be discarded.
     */
    @Parameter(property = "stashChanges", readonly = true)
    protected Boolean stashChanges;

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature reset to remote state process");
        checkCentralBranchConfig();
        String featureBranchLocalName = gitRebaseFeatureBranchInProcess();
        if (featureBranchLocalName != null) {
            throw new GitFlowFailureException(
                    "A rebase of the feature branch '" + featureBranchLocalName
                            + "' is in process. Cannot reset feature to remote state now.",
                    "Finish or abort rebase process first in order to proceed.",
                    "'mvn flow:feature-rebase-abort' to abort feature rebase process");
        } else if (gitMergeInProcess()) {
            throw new GitFlowFailureException(
                    "A merge into the current branch is in process. Cannot reset feature to remote state now.",
                    "Finish or abort merge process first in order to proceed.",
                    "'mvn flow:feature-rebase-abort' to abort feature merge process");
        }

        boolean isOnFeatureBranch = false;
        String currentBranch = gitCurrentBranch();

        if (StringUtils.isNotEmpty(branchName)) {
            featureBranchLocalName = gitLocalRef(branchName);
            if (!isFeatureBranch(featureBranchLocalName)) {
                throw new GitFlowFailureException(
                        "Branch '" + branchName + "' defined in 'branchName' property is not a feature branch.",
                        "Please define a feature branch in order to proceed.");
            }
            isOnFeatureBranch = featureBranchLocalName.equals(currentBranch);
            getLog().info("Reseting feature to remote state on specified feature branch: " + featureBranchLocalName);
        } else {
            List<String> branches = gitAllFeatureBranches();
            if (branches.isEmpty()) {
                throw new GitFlowFailureException("There are no feature branches in your repository.", null);
            }
            // is the current branch a feature branch?
            isOnFeatureBranch = branches.contains(currentBranch);
            if (!isOnFeatureBranch) {
                featureBranchLocalName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                        "Choose feature branch to reset to remote state", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:feature-reset-to-remote' can be executed only on a "
                                        + "feature branch.",
                                "Please switch to a feature branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the feature branch",
                                "'mvn flow:feature-reset-to-remote' to run in interactive mode"));
                getLog().info("Reseting feature to remote state on selected feature branch: " + featureBranchLocalName);
            } else {
                featureBranchLocalName = currentBranch;
                getLog().info("Reseting feature to remote state on current feature branch: " + featureBranchLocalName);
            }
        }
        String featureBranchRemoteName = gitLocalToRemoteRef(featureBranchLocalName);
        gitFetchOnce();
        if (gitIsSameCommit(featureBranchRemoteName, featureBranchLocalName)) {
            getMavenLog().info(
                    "No changes on remote feature branch '" + featureBranchRemoteName + "' found. Nothing to reset.");
            getMavenLog().info("Feature reset to remote state process finished");
            return;
        } else if (gitIsAncestorBranch(featureBranchLocalName, featureBranchRemoteName)) {
            getMavenLog().info("Remote feature branch contains all local feature commits. "
                    + "Local feature branch can be fast forwarded.");
        } else if (gitIsAncestorBranch(featureBranchRemoteName, featureBranchLocalName)) {
            if (!getPrompter().promptConfirmation(
                    "Either you have commits on feature branch that were not yet pushed or remote feature branch was "
                            + "reset to an older state.\nIf you continue these commits will be discarded. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("You have aborted feature-reset-to-remote process.", null);
            }
        } else {
            if (!getPrompter().promptConfirmation("Feature branch seems to be rebased remotely.\n"
                    + "If you continue all commits that were not pushed before rebase will be discarded. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("You have aborted feature-reset-to-remote process.", null);
            }
        }

        if (isOnFeatureBranch) {
            boolean stashNeeded = false;
            boolean hasUncommittedChanges = executeGitHasUncommitted();
            if (hasUncommittedChanges) {
                if (stashChanges != null) {
                    stashNeeded = stashChanges;
                } else {
                    String answer = getPrompter().promptSelection("You have some uncommitted files.\n"
                            + "Select whether you want to (s)tash the changes before reaset and unstash them afterwards"
                            + " or to (d)iscarded the local changes or to (a)bort the reset process.",
                            new String[] { "s", "d", "a" }, "s");
                    if ("s".equalsIgnoreCase(answer)) {
                        stashNeeded = true;
                    } else if ("d".equalsIgnoreCase(answer)) {
                        stashNeeded = false;
                    } else {
                        throw new GitFlowFailureException("You have aborted feature-reset-to-remote process.", null);
                    }
                }
            }
            String initialCommit = null;
            if (stashNeeded) {
                initialCommit = getCurrentCommit();
                gitStash("stashed by gitflow before feature reset ("
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ")");
                getMavenLog().info("Uncommitted changes stashed before reset.");
            }
            gitResetHard("refs/remotes/" + featureBranchRemoteName);
            if (stashNeeded) {
                if (gitStashApply()) {
                    gitStashDrop();
                } else {
                    getLog().info("Stashed changes couldn't be applied.");
                    gitResetHard();
                    throw new GitFlowFailureException(
                            "Local feature branch was reset to remote state but changes stashed before reset couldn't "
                                    + "be applied afterwards.",
                            "Apply stashed changes manually and resolve conflicts or reset local feature branch to "
                                    + "initial state.",
                            "'git stash apply' to apply stashed changes",
                            "'git stash drop' to remove the stash after applying",
                            "'git reset --hard " + initialCommit + "' to reset local feature branch to initial state");
                }
                getMavenLog().info("Stashed uncommitted changes applied after reset.");
            } else if (hasUncommittedChanges) {
                getMavenLog().info("Uncommitted changes were discarded.");
            }
        } else {
            gitUpdateRef(featureBranchLocalName, "refs/remotes/" + featureBranchRemoteName);
        }
        getMavenLog().info("Feature reset to remote state process finished");
    }

}
