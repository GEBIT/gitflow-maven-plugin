//
// AbstractGitFlowEpicMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.AbstractGitFlowMojo.BranchRef;
import de.gebit.build.maven.plugin.gitflow.AbstractGitFlowMojo.CommitRef;

/**
 * Abstract implementation for all epic mojos.
 *
 * @author Volodymyr Medvid
 * @since 2.0.0
 */
public abstract class AbstractGitFlowEpicMojo extends AbstractGitFlowMojo {

    /**
     * Return the commit message for version change commit (first commit) of
     * epic branch if used on epic start and stored in central branch config.
     *
     * @param epicBranch
     *            the name of the epic branch
     * @return version change commit message or <code>null</code> if version
     *         change was not commited on epic start
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String getEpicStartCommitMessage(String epicBranch) throws MojoFailureException, CommandLineException {
        return gitGetBranchCentralConfig(epicBranch, BranchConfigKeys.START_COMMIT_MESSAGE);
    }

    /**
     * Checks whether a merge is in process by checking MERGE_HEAD file and that
     * the MERGE_HEAD points an epic branch.
     *
     * @return the name of the epic branch that is being merged into current
     *         branch or <code>null</code> if no merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeFromEpicBranchInProcess() throws MojoFailureException, CommandLineException {
        String mergeHeadName = gitGetMergeHeadIfExists();
        if (mergeHeadName == null) {
            return null;
        }
        return getEpicBranchName(mergeHeadName);
    }

    private String getEpicBranchName(String mergeHeadName) throws CommandLineException, MojoFailureException {
        String epicBranch = gitGetBranchNameFromMergeHeadIfStartsWith(mergeHeadName,
                gitFlowConfig.getEpicBranchPrefix());
        if (epicBranch == null) {
            throw new MojoFailureException("Merging branch is not an epic branch: " + mergeHeadName);
        }
        return epicBranch;
    }

    protected List<String> gitAllEpicBranches() throws MojoFailureException, CommandLineException {
        return gitAllBranches(gitFlowConfig.getEpicBranchPrefix());
    }

    /**
     * Get the name of the base branch for passed epic branch.
     *
     * @param epicBranch
     *            epic branch name
     * @return name of the base branch even if it doesn't exist
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    private String gitEpicBranchBaseBranchName(String epicBranch) throws MojoFailureException, CommandLineException {
        String baseBranch = gitGetBranchBaseBranch(epicBranch);
        if (baseBranch == null) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Failed to find base branch for epic branch '" + epicBranch + "' in central branch config.\n"
                                + "This indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException(
                        "Failed to find base branch for epic branch '" + epicBranch + "' in central branch config.",
                        "Set 'fetchRemote' parameter to true in order to search for base branch also in remote "
                                + "repository.");
            }
        }
        getLog().info("Epic branch '" + epicBranch + "' is based on branch '" + baseBranch + "'.");
        return baseBranch;
    }

    /**
     * Get the base branch of an epic branch. Throws
     * {@link GitFlowFailureException} if base branch doesn't exist or can't be
     * determined.
     *
     * @param epicBranch
     *            the name of the epic branch
     * @return base branch that exists locally
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    protected String gitEpicBranchBaseBranch(String epicBranch) throws MojoFailureException, CommandLineException {
        String baseBranch = gitEpicBranchBaseBranchName(epicBranch);
        gitEnsureLocalBranchExists(baseBranch, createBranchNotExistingSevereError(
                "Base branch '" + baseBranch + "' for epic branch '" + epicBranch + "'"));
        return baseBranch;
    }
    
    protected CommitRef gitEpicBranchBaseCommit(BranchRef epicBranch)
            throws MojoFailureException, CommandLineException {
        BranchRef baseBranch = getEpicBaseBranch(epicBranch);
        return gitBranchPoint(baseBranch, epicBranch);
    }

    private BranchRef getEpicBaseBranch(BranchRef epicBranch) throws MojoFailureException, CommandLineException {
        String baseBranchName = gitEpicBranchBaseBranchName(epicBranch.getLocalName());
        return preferRemoteRef(baseBranchName, createBranchNotExistingSevereError(
                "Base branch '" + baseBranchName + "' for epic branch '" + epicBranch.getLocalName() + "'"));
    }

    protected boolean hasCommitsExceptVersionChangeCommitOnEpicBranch(String epicBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint(epicBranch, baseBranch);
        int commits = gitGetDistanceToAncestor(epicBranch, branchPoint);
        if (commits == 0) {
            return false;
        } else if (commits == 1) {
            return StringUtils.isBlank(gitVersionChangeCommitOnEpicBranch(epicBranch, branchPoint));
        } else {
            return true;
        }
    }

    protected boolean hasCommitsExceptVersionChangeCommitOnEpicBranch(BranchRef epicBranch)
            throws MojoFailureException, CommandLineException {
        CommitRef branchPoint = gitEpicBranchBaseCommit(epicBranch);
        int commits = gitGetDistanceToAncestor(epicBranch, branchPoint);
        if (commits == 0) {
            return false;
        } else if (commits == 1) {
            return gitVersionChangeCommitOnEpicBranch(epicBranch, branchPoint) == null;
        } else {
            return true;
        }
    }

    protected String gitVersionChangeCommitOnEpicBranch(String epicBranch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        String firstCommitOnBranch = gitFirstCommitOnBranch(epicBranch, branchPoint);
        String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
        String epicStartMessage = getEpicStartCommitMessage(epicBranch);
        if (epicStartMessage == null || !firstCommitMessage.contains(epicStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit.");
            }
            return null;
        }
        return firstCommitOnBranch;
    }
    
    protected CommitRef gitVersionChangeCommitOnEpicBranch(BranchRef epicBranch, CommitRef branchPoint)
            throws MojoFailureException, CommandLineException {
        CommitRef firstCommitOnBranch = gitFirstCommitOnBranch(epicBranch, branchPoint);
        String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
        String epicStartMessage = getEpicStartCommitMessage(epicBranch.getLocalName());
        if (epicStartMessage == null || !firstCommitMessage.contains(epicStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit.");
            }
            return null;
        }
        return firstCommitOnBranch;
    }

    /**
     * Checks whether a merge is in progress by checking MERGE_HEAD file and the
     * current branch is an epic branch.
     *
     * @return the name of the current (epic) branch or <code>null</code> if no
     *         merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeIntoEpicBranchInProcess() throws MojoFailureException, CommandLineException {
        if (!gitMergeInProcess()) {
            return null;
        }
        String currentBranchName = gitCurrentBranch();
        if (StringUtils.isBlank(currentBranchName)) {
            throw new MojoFailureException("Failed to obtain current branch name.");
        }
        if (!currentBranchName.startsWith(gitFlowConfig.getEpicBranchPrefix())) {
            throw new MojoFailureException("Merge target branch is not an epic branch: " + currentBranchName);
        }
        return currentBranchName;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     *
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitRebaseEpicBranchInProcess() throws MojoFailureException, CommandLineException {
        final String tempBranchName = gitRebaseBranchInProcess();
        if (tempBranchName != null && !tempBranchName.startsWith(gitFlowConfig.getEpicBranchPrefix())) {
            throw new MojoFailureException("Rebasing branch is not an epic branch: " + tempBranchName);
        }
        return tempBranchName;
    }

    /**
     * Create a name of temporary epic branch for passed epic branch.
     *
     * @param epicBranchName
     *            the name of epic branch
     * @return the name of temporary epic branch
     */
    protected String createTempEpicBranchName(String epicBranchName) {
        return "tmp-" + epicBranchName;
    }

}
