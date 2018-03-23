//
// AbstractGitFlowFeatureMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Extracts the feature issue number from feature name using feature name
     * pattern. E.g. extracts issue number "GBLD-42" from feature name
     * "GBLD-42-someDescription" if default feature name pattern is used.
     * Returns feature name if issue number can't be extracted.
     *
     * @param aFeatureName
     *            the feature name
     * @return the extracted feature issue number or feature name if issue
     *         number can't be extracted
     */
    protected String extractIssueNumberFromFeatureName(String aFeatureName) {
        String issueNumber = aFeatureName;
        if (featureNamePattern != null) {
            // extract the issue number only
            Matcher m = Pattern.compile(featureNamePattern).matcher(aFeatureName);
            if (m.matches()) {
                if (m.groupCount() == 0) {
                    getLog().warn("Feature branch conforms to <featureNamePattern>, but ther is no matching"
                            + " group to extract the issue number.");
                } else {
                    issueNumber = m.group(1);
                }
            } else {
                getLog().warn("Feature branch does not conform to <featureNamePattern> specified, cannot "
                        + "extract issue number.");
            }
        }
        return issueNumber;
    }

    /**
     * Extracts the feature issue number from feature branch name using feature
     * name pattern and feature branch prefix. E.g. extracts issue number
     * "GBLD-42" from feature branch name "feature/GBLD-42-someDescription" if
     * default feature name pattern is used. Returns feature name if issue
     * number can't be extracted.
     *
     * @param featureBranchName
     *            the feature branch name
     * @return the extracted feature issue number or feature name if issue
     *         number can't be extracted
     */
    protected String extractIssueNumberFromFeatureBranchName(String featureBranchName) {
        String featureName = featureBranchName.replaceFirst(gitFlowConfig.getFeatureBranchPrefix(), "");
        return extractIssueNumberFromFeatureName(featureName);
    }

    /**
     * Substitute keys of the form <code>@{name}</code> in the messages. By
     * default knows about <code>key</code>, which will be replaced by issue
     * number and all project properties.
     *
     * @param message
     *            the message to process
     * @param issueNumber
     *            the feature issue number
     * @return the message with applied substitutions
     * @see #lookupKey(String)
     */
    protected String substituteInFeatureMessage(String message, String issueNumber) throws MojoFailureException {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("key", issueNumber);
        return substituteStrings(message, replacements);
    }

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
        String featureStartMessage = substituteInFeatureMessage(commitMessages.getFeatureStartMessage(),
                extractIssueNumberFromFeatureBranchName(featureBranch));
        if (!firstCommitMessage.contains(featureStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit.");
            }
            return null;
        }
        return firstCommitOnBranch;
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
     * Merges the first commit on the given branch ignoring any changes. This
     * first commit is the commit that changed the versions.
     *
     * @param featureBranch
     *            The feature branch name.
     * @param branchPoint
     *            the branch point on both feature and development branch
     * @param versionChangeCommitId
     *            commit ID of the version change commit. Must be first commit
     *            on featuereBranch after branchPoint
     * @return true if the version has been premerged and does not need to be
     *         turned back
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitTryRebaseWithoutVersionChange(String featureBranch, String branchPoint,
            String versionChangeCommitId) throws MojoFailureException, CommandLineException {
        if (!gitHasNoMergeCommits(featureBranch, versionChangeCommitId)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Cannot rebase due to merge commits.");
            }
            return false;
        }

        getLog().info("Removing version change commit.");
        try {
            removeCommits(branchPoint, versionChangeCommitId, featureBranch);
        } catch (MojoFailureException ex) {
            throw new GitFlowFailureException(ex,
                    "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                    "Fix the rebase conflicts and mark them as resolved. "
                            + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git rebase --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                    "'mvn flow:feature-finish' to continue feature finish process");
        }
        return true;
    }

    /**
     * Get the branch point of a feature branch.
     *
     * @param featureBranch
     *            feature branch name
     * @return commit ID of the branch point (common ancestor with the
     *         development branch)
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    protected String gitFeatureBranchBaseBranch(String featureBranch)
            throws MojoFailureException, CommandLineException {
        List<String> baseBranchCandidates = gitFeatureBranchBaseBranches(featureBranch);
        if (baseBranchCandidates.isEmpty()) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch
                                + "'. This indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch + "'.",
                        "Set 'fetchRemote' parameter to true in order to search for base branch also in remote "
                                + "repository.");
            }
        }
        String baseBranch = baseBranchCandidates.get(0);
        getLog().debug("Feature branch is based on " + baseBranch + ".");
        return baseBranch;
    }

    protected List<String> gitFeatureBranchBaseBranches(String featureBranch)
            throws MojoFailureException, CommandLineException {
        getLog().info("Looking for branch base of '" + featureBranch + "'.");

        // try all development branches
        Map<String, List<String>> branchPointCandidates = new HashMap<>();
        String developmentBranch = gitFlowConfig.getDevelopmentBranch();
        gitFetchBranches(developmentBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, featureBranch, developmentBranch, true);
        } else if (gitBranchExists(developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, featureBranch, developmentBranch, false);
        }
        List<String> remoteMaintenanceBranches = gitRemoteMaintenanceBranches();
        if (remoteMaintenanceBranches.size() > 0) {
            gitFetchBranches(remoteMaintenanceBranches);
            for (String maintenanceBranch : remoteMaintenanceBranches) {
                addBranchPointCandidate(branchPointCandidates, featureBranch, maintenanceBranch, true);
            }
        }
        List<String> localMaintenanceBranches = gitLocalMaintenanceBranches();
        if (localMaintenanceBranches.size() > 0) {
            for (String maintenanceBranch : localMaintenanceBranches) {
                if (!branchPointCandidates.containsKey(maintenanceBranch)) {
                    addBranchPointCandidate(branchPointCandidates, featureBranch, maintenanceBranch, false);
                }
            }
        }
        List<String> remoteEpicBranches = gitRemoteEpicBranches();
        if (remoteEpicBranches.size() > 0) {
            gitFetchBranches(remoteEpicBranches);
            for (String epicBranch : remoteEpicBranches) {
                addBranchPointCandidate(branchPointCandidates, featureBranch, epicBranch, true);
            }
        }
        List<String> localEpicBranches = gitLocalEpicBranches();
        if (localEpicBranches.size() > 0) {
            for (String epicBranch : localEpicBranches) {
                if (!branchPointCandidates.containsKey(epicBranch)) {
                    addBranchPointCandidate(branchPointCandidates, featureBranch, epicBranch, false);
                }
            }
        }
        Set<String> branchPoints = branchPointCandidates.keySet();
        String nearestBranchPoint = gitNearestAncestorCommit(featureBranch, branchPoints);
        if (nearestBranchPoint != null) {
            return branchPointCandidates.get(nearestBranchPoint);
        }
        return Collections.EMPTY_LIST;
    }

    private void addBranchPointCandidate(Map<String, List<String>> branchPointCandidates, String featureBranch,
            String baseBranch, boolean remote) throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint((remote ? gitFlowConfig.getOrigin() + "/" : "") + baseBranch, featureBranch);
        if (branchPoint != null) {
            List<String> baseBranches = branchPointCandidates.get(branchPoint);
            if (baseBranches == null) {
                baseBranches = new ArrayList<>();
                branchPointCandidates.put(branchPoint, baseBranches);
            }
            if (!baseBranches.contains(baseBranch)) {
                baseBranches.add(baseBranch);
            }
        }
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
        String baseBranch = gitFeatureBranchBaseBranch(featureBranch);
        gitFetchBranches(baseBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), baseBranch)) {
            baseBranch = gitFlowConfig.getOrigin() + "/" + baseBranch;
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

}
