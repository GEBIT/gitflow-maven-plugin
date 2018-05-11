//
// AbstractGitFlowFeatureMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abstract implementation for all feature mojos.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
public abstract class AbstractGitFlowFeatureMojo extends AbstractGitFlowMojo {

    /**
     * A regex pattern that a new feature name must match. It is also used to
     * extract a "key" from a branch name which can be referred to as
     * <code>@key</code> in commit messages. The extraction will be performed
     * using the first matching group (if present). You will need this if your
     * commit messages need to refer to e.g. an issue tracker key.
     *
     * @since 1.3.0
     */
    @Parameter(property = "featureNamePattern", required = false)
    protected String featureNamePattern;

    /**
     * Get the first commit on the branch, which is the version change commit
     *
     * @param featureBranch
     *            feature branch name
     * @param branchPoint
     *            commit ID of the common ancestor with the development branch
     */
    protected String gitVersionChangeCommitOnFeatureBranch(String featureBranch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        String firstCommitOnBranch = gitFirstCommitOnBranch(featureBranch, branchPoint);
        String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
        String featureStartMessage = getFeatureStartCommitMessage(featureBranch);
        if (featureStartMessage == null || !firstCommitMessage.contains(featureStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit.");
            }
            return null;
        }
        return firstCommitOnBranch;
    }

    /**
     * Return the commit message for version change commit (first commit) of
     * feature branch if used on feature start and stored in central branch
     * config.
     *
     * @param featureBranch
     *            the name of the feature branch
     * @return version change commit message or <code>null</code> if version
     *         change was not commited on feature start
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String getFeatureStartCommitMessage(String featureBranch)
            throws MojoFailureException, CommandLineException {
        return gitGetBranchCentralConfig(featureBranch, BranchConfigKeys.START_COMMIT_MESSAGE);
    }

    /**
     * Return issue number of the feature parsed from feature name on feature
     * start and stored in central branch config.
     *
     * @param featureBranch
     *            the name of the feature branch
     * @return the feature issue number or <code>null</code> if issue number
     *         can't be find in central branch config
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String getFeatureIssueNumber(String featureBranch) throws MojoFailureException, CommandLineException {
        return gitGetBranchCentralConfig(featureBranch, BranchConfigKeys.ISSUE_NUMBER);
    }

    protected boolean hasCommitsExceptVersionChangeCommitOnFeatureBranch(String featureBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint(featureBranch, baseBranch);
        int commits = gitGetDistanceToAncestor(featureBranch, branchPoint);
        if (commits == 0) {
            return false;
        } else if (commits == 1) {
            return StringUtils.isBlank(gitVersionChangeCommitOnFeatureBranch(featureBranch, branchPoint));
        } else {
            return true;
        }
    }

    protected List<String> gitAllFeatureBranches() throws MojoFailureException, CommandLineException {
        return gitAllBranches(gitFlowConfig.getFeatureBranchPrefix());
    }

    /**
     * Get the base branch of a feature branch. Throws
     * {@link GitFlowFailureException} if base branch doesn't exist or can't be
     * determined.
     *
     * @param featureBranch
     *            feature branch name
     * @return base branch that exists locally
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    protected String gitFeatureBranchBaseBranch(String featureBranch)
            throws MojoFailureException, CommandLineException {
        String baseBranch = gitFeatureBranchBaseBranchName(featureBranch);
        GitFlowFailureInfo baseBranchNotExistingErrorMessage;
        if (fetchRemote) {
            baseBranchNotExistingErrorMessage = new GitFlowFailureInfo(
                    "Base branch '" + baseBranch + "' for feature branch '" + featureBranch
                            + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                    "Please consult a gitflow expert on how to fix this!");
        } else {
            baseBranchNotExistingErrorMessage = new GitFlowFailureInfo(
                    "Base branch '" + baseBranch + "' for feature branch '" + featureBranch
                            + "' doesn't exist locally.",
                    "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
        }
        gitEnsureLocalBranchExists(baseBranch, baseBranchNotExistingErrorMessage);
        return baseBranch;
    }

    /**
     * Get the name of the base branch for passed feature branch.
     *
     * @param featureBranch
     *            feature branch name
     * @return name of the base branch even if it doesn't exist
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    private String gitFeatureBranchBaseBranchName(String featureBranch)
            throws MojoFailureException, CommandLineException {
        String baseBranch = gitGetBranchBaseBranch(featureBranch);
        if (baseBranch == null) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch
                                + "' in central branch config.\nThis indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch
                                + "' in central branch config.",
                        "Set 'fetchRemote' parameter to true in order to search for base branch also in remote "
                                + "repository.");
            }
        }
        getLog().info("Feature branch '" + featureBranch + "' is based on branch '" + baseBranch + "'.");
        return baseBranch;
    }

    /**
     * Get the base commit (branch point) for passed feature branch.
     *
     * @param featureBranch
     *            the name of the feature branch
     * @return the base commit for feature branch
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitFeatureBranchBaseCommit(String featureBranch)
            throws MojoFailureException, CommandLineException {
        String baseBranch = gitFeatureBranchBaseBranchName(featureBranch);
        if (gitRemoteBranchExists(baseBranch)) {
            baseBranch = gitFlowConfig.getOrigin() + "/" + baseBranch;
        } else if (!gitBranchExists(baseBranch)) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Base commit for feature branch '" + featureBranch
                                + "' can't be estimated because the base branch '" + baseBranch + "' doesn't exist.\n"
                                + "This indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException("Base commit for feature branch '" + featureBranch
                        + "' can't be estimated because the base branch '" + baseBranch + "' doesn't exist locally.",
                        "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
            }
        }
        return gitBranchPoint(baseBranch, featureBranch);
    }

    protected String gitInteractiveRebaseFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        if (gitInteractiveRebaseInProcess()) {
            return gitRebaseFeatureBranchInProcess();
        }
        return null;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     *
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitRebaseFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        String headName = gitGetRebaseHeadNameIfExists();
        if (headName == null) {
            return null;
        }
        final String branchRef = headName.trim();
        if (!branchRef.startsWith("refs/heads/")) {
            throw new MojoFailureException("Illegal rebasing branch reference: " + branchRef);
        }
        final String tempBranchName = branchRef.substring("refs/heads/".length());
        if (!tempBranchName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
            throw new MojoFailureException("Rebasing branch is not a feature branch: " + branchRef);
        }
        return tempBranchName;
    }

    /**
     * Checks whether a merge is in progress by checking MERGE_HEAD file and the
     * current branch is a feature branch.
     *
     * @return the name of the current (feature) branch or <code>null</code> if
     *         no merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeIntoFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        if (!gitMergeInProcess()) {
            return null;
        }
        String currentBranchName = gitCurrentBranch();
        if (StringUtils.isBlank(currentBranchName)) {
            throw new MojoFailureException("Failed to obtain current branch name.");
        }
        if (!currentBranchName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
            throw new MojoFailureException("Merge target branch is not a feature branch: " + currentBranchName);
        }
        return currentBranchName;
    }

    /**
     * Checks whether a merge is in process by checking MERGE_HEAD file and that
     * the MERGE_HEAD points a feature branch.
     *
     * @return the name of the feature branch that is being merged into current
     *         branch or <code>null</code> if no merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeFromFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        String mergeHeadName = gitGetMergeHeadIfExists();
        if (mergeHeadName == null) {
            return null;
        }
        return getFeatureBranchName(mergeHeadName);
    }

    private String getFeatureBranchName(String mergeHeadName) throws CommandLineException, MojoFailureException {
        String featureBranch = gitGetBranchNameFromMergeHeadIfStartsWith(mergeHeadName,
                gitFlowConfig.getFeatureBranchPrefix());
        if (featureBranch == null) {
            throw new MojoFailureException("Merging branch is not a feature branch: " + mergeHeadName);
        }
        return featureBranch;
    }

    /**
     * Create a name of temporary feature branch for passed feature branch.
     *
     * @param featureBranchName
     *            the name of feature branch
     * @return the name of temporary feature branch
     */
    protected String createTempFeatureBranchName(String featureBranchName) {
        return "tmp-" + featureBranchName;
    }

}
