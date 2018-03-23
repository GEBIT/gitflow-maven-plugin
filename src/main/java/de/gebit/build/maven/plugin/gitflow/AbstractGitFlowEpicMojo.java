//
// AbstractGitFlowEpicMojo.java
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

import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Abstract implementation for all epic mojos.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
public abstract class AbstractGitFlowEpicMojo extends AbstractGitFlowMojo {

    /**
     * Substitute keys of the form <code>@{name}</code> in the messages. By
     * default knows about <code>key</code>, which will be replaced by issue
     * number and all project properties.
     *
     * @param message
     *            the message to process
     * @param issueNumber
     *            the epic issue number
     * @return the message with applied substitutions
     * @see #lookupKey(String)
     */
    protected String substituteInEpicMessage(String message, String issueNumber) throws MojoFailureException {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("key", issueNumber);
        return substituteStrings(message, replacements);
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
     * Get the branch point of an epic branch.
     *
     * @param epicBranch
     *            the name of the epic branch
     * @return commit ID of the branch point (common ancestor with the
     *         development/maintenance branch)
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    protected String gitEpicBranchBaseBranch(String epicBranch) throws MojoFailureException, CommandLineException {
        List<String> baseBranchCandidates = gitEpicBranchBaseBranches(epicBranch);
        if (baseBranchCandidates.isEmpty()) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Failed to find base branch for epic branch '" + epicBranch
                                + "'. This indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException("Failed to find base branch for epic branch '" + epicBranch + "'.",
                        "Set 'fetchRemote' parameter to true in order to search for base branch also in remote "
                                + "repository.");
            }
        }
        String baseBranch = baseBranchCandidates.get(0);
        getLog().debug("Epic branch is based on " + baseBranch + ".");
        return baseBranch;
    }

    protected List<String> gitEpicBranchBaseBranches(String epicBranch)
            throws MojoFailureException, CommandLineException {
        getLog().info("Looking for branch base of '" + epicBranch + "'.");

        // try all development branches
        Map<String, List<String>> branchPointCandidates = new HashMap<>();
        String developmentBranch = gitFlowConfig.getDevelopmentBranch();
        gitFetchBranches(developmentBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, epicBranch, developmentBranch, true);
        } else if (gitBranchExists(developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, epicBranch, developmentBranch, false);
        }
        List<String> remoteMaintenanceBranches = gitRemoteMaintenanceBranches();
        if (remoteMaintenanceBranches.size() > 0) {
            gitFetchBranches(remoteMaintenanceBranches);
            for (String maintenanceBranch : remoteMaintenanceBranches) {
                addBranchPointCandidate(branchPointCandidates, epicBranch, maintenanceBranch, true);
            }
        }
        List<String> localMaintenanceBranches = gitLocalMaintenanceBranches();
        if (localMaintenanceBranches.size() > 0) {
            for (String maintenanceBranch : localMaintenanceBranches) {
                if (!branchPointCandidates.containsKey(maintenanceBranch)) {
                    addBranchPointCandidate(branchPointCandidates, epicBranch, maintenanceBranch, false);
                }
            }
        }
        Set<String> branchPoints = branchPointCandidates.keySet();
        String nearestBranchPoint = gitNearestAncestorCommit(epicBranch, branchPoints);
        if (nearestBranchPoint != null) {
            return branchPointCandidates.get(nearestBranchPoint);
        }
        return Collections.EMPTY_LIST;
    }

    private void addBranchPointCandidate(Map<String, List<String>> branchPointCandidates, String epicBranch,
            String baseBranch, boolean remote) throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint((remote ? gitFlowConfig.getOrigin() + "/" : "") + baseBranch, epicBranch);
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

    private String gitVersionChangeCommitOnEpicBranch(String epicBranch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        String firstCommitOnBranch = gitFirstCommitOnBranch(epicBranch, branchPoint);
        String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
        String issueNumber = extractIssueNumberFromEpicBranchName(epicBranch);
        String epicStartMessage = substituteInEpicMessage(commitMessages.getEpicStartMessage(), issueNumber);
        if (!firstCommitMessage.contains(epicStartMessage)) {
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

}
