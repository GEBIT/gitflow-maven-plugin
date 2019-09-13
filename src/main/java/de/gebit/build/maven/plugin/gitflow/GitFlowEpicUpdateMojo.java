//
// GitFlowEpicUpdateMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Rebase an epic branch on top of development (upstream) branch.<br>
 * If some other (feature) branches based on the epic branch exists or the epic
 * branch has merge commits, the rebase of the epic branch is not possible. In
 * this case the development (upstream) branch can be merged into the epic
 * branch.
 * <p>
 * Integrates the changes from development branch into the epic branch. If
 * conflicts occur on rebase/merge, you can fix the conflicts and continue
 * rebase/merge process by executing <code>flow:epic-update</code> again.
 *
 * @author Volodymyr Medvid
 * @see GitFlowEpicStartMojo
 * @since 2.0.0
 */
@Mojo(name = GitFlowEpicUpdateMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowEpicUpdateMojo extends AbstractGitFlowEpicMojo {

    static final String GOAL = "epic-update";

    /**
     * Controls whether a merge of the development branch instead of a rebase on the
     * development branch is performed.
     *
     * @since 2.1.6
     */
    @Parameter(property = "flow.updateEpicWithMerge", defaultValue = "false")
    private boolean updateEpicWithMerge = false;

    /**
     * If fast forward pushes on epic branches are not allowed, the remote branch is
     * deleted before pushing the rebased branch.
     *
     * @since 2.1.6
     */
    @Parameter(property = "flow.deleteRemoteBranchOnRebase", defaultValue = "false")
    private boolean deleteRemoteBranchOnRebase = false;

    /**
     * The epic branch to be updated.
     *
     * @since 2.2.0
     */
    @Parameter(property = "branchName", readonly = true)
    protected String branchName;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic update process");
        checkCentralBranchConfig();
        boolean confirmedUpdateWithMerge = updateEpicWithMerge;
        String epicBranchLocalName = gitRebaseEpicBranchInProcess();
        if (epicBranchLocalName == null) {
            epicBranchLocalName = gitMergeIntoEpicBranchInProcess();
            if (epicBranchLocalName != null) {
                confirmedUpdateWithMerge = true;
            }
        } else {
            confirmedUpdateWithMerge = false;
        }
        boolean continueOnCleanInstall = false;
        if (epicBranchLocalName == null) {
            String currentBranch = gitCurrentBranch();
            if (isEpicBranch(currentBranch)) {
                String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
                if (breakpoint != null) {
                    if ("epicUpdate.cleanInstall".equals(breakpoint)) {
                        continueOnCleanInstall = true;
                        epicBranchLocalName = currentBranch;
                    }
                }
            }
        }
        if (!continueOnCleanInstall) {
            String baseVersion;
            String oldEpicVersion;
            String oldBaseVersion;
            if (epicBranchLocalName == null) {
                checkUncommittedChanges();

                String currentBranch = gitCurrentBranch();
                String currentVersion = null;
                boolean isOnEpicBranch;
                if (StringUtils.isNotEmpty(branchName)) {
                    epicBranchLocalName = gitLocalRef(branchName);
                    if (!isEpicBranch(epicBranchLocalName)) {
                        throw new GitFlowFailureException(
                                "Branch '" + branchName + "' defined in 'branchName' property is not an epic branch.",
                                "Please define an epic branch in order to proceed.");
                    }
                    getLog().info("Updating epic on specified epic branch: " + epicBranchLocalName);
                    isOnEpicBranch = epicBranchLocalName.equals(currentBranch);
                    if (!isOnEpicBranch && !gitLocalOrRemoteBranchesExist(epicBranchLocalName)) {
                        throw new GitFlowFailureException(createBranchNotExistingError(
                                "Epic branch '" + branchName + "' defined in 'branchName' property",
                                "Please define an existing epic branch in order to proceed."));
                    }
                } else {
                    List<String> branches = gitAllEpicBranches();
                    if (branches.isEmpty()) {
                        throw new GitFlowFailureException("There are no epic branches in your repository.",
                                "Please start an epic first.", "'mvn flow:epic-start'");
                    }
                    isOnEpicBranch = branches.contains(currentBranch);
                    if (!isOnEpicBranch) {
                        epicBranchLocalName = getPrompter().promptToSelectFromOrderedList("Epic branches:",
                                "Choose epic branch to update", branches,
                                new GitFlowFailureInfo(
                                        "In non-interactive mode 'mvn flow:epic-update' can be executed only on an epic branch.",
                                        "Please switch to an epic branch first or run in interactive mode.",
                                        "'git checkout BRANCH' to switch to the epic branch",
                                        "'mvn flow:epic-update' to run in interactive mode"));
                        getLog().info("Updating epic on selected epic branch: " + epicBranchLocalName);
                    } else {
                        epicBranchLocalName = currentBranch;
                        getLog().info("Updating epic on current epic branch: " + epicBranchLocalName);
                    }
                }
                
                if (!isOnEpicBranch) {
                    gitEnsureLocalBranchIsUpToDateIfExists(epicBranchLocalName,
                            new GitFlowFailureInfo("Remote and local epic branches '" + epicBranchLocalName + "' diverge.",
                                    "Rebase or merge the changes in local epic branch '" + epicBranchLocalName + "' first.",
                                    "'git rebase'"));
                    currentVersion = getCurrentProjectVersion();
                    getMavenLog().info("Switching to epic branch '" + epicBranchLocalName + "'");
                    gitCheckout(epicBranchLocalName);
                } else {
                    gitEnsureCurrentLocalBranchIsUpToDate(
                            new GitFlowFailureInfo("Remote and local epic branches '{0}' diverge.",
                                    "Rebase or merge the changes in local epic branch '{0}' first.", "'git rebase'"));
                }

                String baseBranch = gitEpicBranchBaseBranch(epicBranchLocalName);
                getMavenLog().info("Base branch of epic branch is '" + baseBranch + "'");

                // use integration branch?
                String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
                gitEnsureLocalBranchIsUpToDateIfExists(integrationBranch,
                        new GitFlowFailureInfo(
                                "Local and remote integration branches '" + integrationBranch
                                        + "' diverge, this indicates a severe error condition on your branches.",
                                "Please consult a gitflow expert on how to fix this!"));
                gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                        new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                        + "branch '" + baseBranch + "'"));
                if (gitBranchExists(integrationBranch)) {
                    boolean useIntegrationBranch = true;
                    if (!Objects.equals(getCurrentCommit(integrationBranch), getCurrentCommit(baseBranch))) {
                        useIntegrationBranch = getPrompter().promptConfirmation("The current commit on " + baseBranch
                                + " is not integrated. Update epic branch to the last integrated commit ("
                                + integrationBranch + ")?", true, true);
                    }
                    if (useIntegrationBranch) {
                        if (!gitIsAncestorBranch(integrationBranch, baseBranch)) {
                            throw new GitFlowFailureException(
                                    "Integration branch '" + integrationBranch + "' is ahead of base branch '"
                                            + baseBranch
                                            + "', this indicates a severe error condition on your branches.",
                                    " Please consult a gitflow expert on how to fix this!");
                        }

                        getMavenLog()
                                .info("Using integration branch '" + integrationBranch + "' to update the epic branch");
                        baseBranch = integrationBranch;
                    }
                }

                if (pushRemote) {
                    gitEnsureLocalAndRemoteBranchesAreSynchronized(baseBranch, new GitFlowFailureInfo(
                            "Local base branch '" + baseBranch + "' is ahead of remote branch. Pushing of the updated "
                                    + "epic branch will create an inconsistent state in remote repository.",
                            "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to false in "
                                    + "order to avoid inconsistent state in remote repository."),
                            new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                                    "Rebase the changes in local branch '" + baseBranch + "' in order to proceed.",
                                    "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base "
                                            + "branch '" + baseBranch + "'"),
                            new GitFlowFailureInfo(
                                    "Base branch '" + baseBranch + "' doesn't exist remotely. Pushing of the updated "
                                            + "epic branch will create an inconsistent state in remote repository.",
                                    "Push the base branch '" + baseBranch + "' first or set 'pushRemote' parameter to "
                                            + "false in order to avoid inconsistent state in remote repository."));
                }
                if (baseBranch.equals(currentBranch) && currentVersion != null) {
                    baseVersion = currentVersion;
                } else {
                    gitCheckout(baseBranch);
                    baseVersion = getCurrentProjectVersion();
                    gitCheckout(epicBranchLocalName);
                }
                getLog().info("Project version on base branch: " + baseVersion);
                if (!gitIsAncestorBranch(baseBranch, epicBranchLocalName)) {
                    String branchPoint = gitBranchPoint(epicBranchLocalName, baseBranch);
                    List<String> mergeCommits = gitGetMergeCommits(epicBranchLocalName, branchPoint);
                    boolean hasNonEmptyMergeCommits = false;
                    if (!mergeCommits.isEmpty()) {
                        hasNonEmptyMergeCommits = gitHasNonEmptyMergeCommits(mergeCommits);
                    }
                    String rebaseNotPossibleReason = null;
                    if (hasNonEmptyMergeCommits) {
                        rebaseNotPossibleReason = "epic branch contains merge commits";
                    } else {
                        List<String> subBranches = getSubBranches(epicBranchLocalName, branchPoint);
                        if (!subBranches.isEmpty()) {
                            StringBuilder msgBuilder = new StringBuilder("found branches based on epic branch:");
                            final int MAX = 4;
                            int cnt = 0;
                            for (String subBranch : subBranches) {
                                msgBuilder.append("\n- ");
                                msgBuilder.append(subBranch);
                                cnt++;
                                if (cnt == MAX) {
                                    break;
                                } else if (subBranches.size() > MAX && cnt == MAX - 1) {
                                    msgBuilder.append("\n- and ");
                                    msgBuilder.append(subBranches.size() - MAX + 1);
                                    msgBuilder.append(" more branches");
                                    break;
                                }
                            }
                            rebaseNotPossibleReason = msgBuilder.toString();
                        }
                    }

                    if (updateEpicWithMerge) {
                        if (rebaseNotPossibleReason == null) {
                            String answer = getPrompter().promptSelection(
                                    "Updating is configured for merges, a later rebase will not be possible. Select if you "
                                            + "want to proceed with (m)erge or you want to use (r)ebase instead or (a)bort "
                                            + "the process.",
                                    new String[] { "m", "r", "a" }, "m");
                            if ("m".equalsIgnoreCase(answer)) {
                                confirmedUpdateWithMerge = true;
                            } else if ("r".equalsIgnoreCase(answer)) {
                                confirmedUpdateWithMerge = false;
                            } else {
                                throw new GitFlowFailureException("Epic update aborted by user.", null);
                            }
                        } else {
                            confirmedUpdateWithMerge = true;
                        }
                    } else if (rebaseNotPossibleReason != null) {
                        GitFlowFailureInfo failureInfo;
                        if (hasNonEmptyMergeCommits) {
                            failureInfo = new GitFlowFailureInfo(
                                    "Epic branch can't be rebased. Reason: " + rebaseNotPossibleReason,
                                    "Run epic update in interactive mode to update epic branch using merge",
                                    "'mvn flow:epic-update' to run in interactive mode");
                        } else {
                            failureInfo = new GitFlowFailureInfo(
                                    "Epic branch can't be rebased. Reason: " + rebaseNotPossibleReason
                                            + "\nIf you continue with merge, a later rebase will not be possible.",
                                    "Finish the listed branches and run epic update again in order to rebase it.\n"
                                            + "Or run epic update in interactive mode in order to update epic branch using "
                                            + "merge.",
                                    "'mvn flow:epic-update' to run in interactive mode");
                        }
                        boolean confirmed = getPrompter()
                                .promptConfirmation(
                                        "Epic branch can't be rebased. Reason: " + rebaseNotPossibleReason
                                                + "\nIf you continue with merge, a later rebase will not be possible.\n"
                                                + "Do you want to merge base branch into epic branch?",
                                        false, failureInfo);
                        if (confirmed) {
                            confirmedUpdateWithMerge = true;
                        } else {
                            throw new GitFlowFailureException(failureInfo);
                        }
                    } else {
                        confirmedUpdateWithMerge = false;
                    }
                    oldEpicVersion = getCurrentProjectVersion();
                    oldBaseVersion = gitGetBranchCentralConfig(epicBranchLocalName, BranchConfigKeys.BASE_VERSION);
                    String oldEpicHEAD = getCurrentCommit();
                    gitSetBranchLocalConfig(epicBranchLocalName, "baseVersion", baseVersion);
                    gitSetBranchLocalConfig(epicBranchLocalName, "oldEpicHEAD", oldEpicHEAD);
                    gitSetBranchLocalConfig(epicBranchLocalName, "oldEpicVersion", oldEpicVersion);
                    gitSetBranchLocalConfig(epicBranchLocalName, "oldBaseVersion", oldBaseVersion);
                    gitSetBranchLocalConfig(epicBranchLocalName, "oldStartCommitMessage",
                            gitGetBranchCentralConfig(epicBranchLocalName, BranchConfigKeys.START_COMMIT_MESSAGE));
                    gitSetBranchLocalConfig(epicBranchLocalName, "oldVersionChangeCommit",
                            gitGetBranchCentralConfig(epicBranchLocalName, BranchConfigKeys.VERSION_CHANGE_COMMIT));
                    if (confirmedUpdateWithMerge) {
                        updateEpicByMerge(epicBranchLocalName, baseBranch, baseVersion, oldBaseVersion);
                    } else {
                        updateEpicByRebase(epicBranchLocalName, baseBranch, branchPoint, !mergeCommits.isEmpty());
                    }
                } else {
                    getMavenLog().info("No changes on base branch '" + baseBranch + "' found. Nothing to update.");
                    getMavenLog().info("Epic update process finished");
                    return;
                }
            } else {
                continueEpicUpdate(confirmedUpdateWithMerge, epicBranchLocalName);
                baseVersion = gitGetBranchLocalConfig(epicBranchLocalName, "baseVersion");
                oldEpicVersion = gitGetBranchLocalConfig(epicBranchLocalName, "oldEpicVersion");
                oldBaseVersion = gitGetBranchLocalConfig(epicBranchLocalName, "oldBaseVersion");
                getLog().info("Project version on base branch: " + baseVersion);
            }
            finalizeRebase(epicBranchLocalName);
            if (oldEpicVersion != null) {
                String epicVersion = getCurrentProjectVersion();
                if (!versionless && confirmedUpdateWithMerge && !baseVersion.equals(oldBaseVersion) && !tychoBuild) {
                    fixupModuleParents(epicBranchLocalName, epicVersion, oldBaseVersion, null, confirmedUpdateWithMerge);
                    String issueNumber = getEpicIssueNumber(epicBranchLocalName);
                    String epicStartMessage = substituteWithIssueNumber(commitMessages.getEpicStartMessage(),
                            issueNumber);
                    String version = insertSuffixInVersion(baseVersion, issueNumber);
                    if (!baseVersion.equals(version)) {
                        getMavenLog().info("Setting version '" + version + "' for project on epic branch...");
                        mvnSetVersions(version, GitFlowAction.EPIC_START, "On epic branch: ", epicBranchLocalName);
                        gitCommit(epicStartMessage);
                    }
                } else {
                    getLog().info("Project version on epic branch: " + epicVersion);
                    fixupModuleParents(epicBranchLocalName, epicVersion, oldEpicVersion, baseVersion,
                            confirmedUpdateWithMerge);
                }
            }
        } else {
            getMavenLog().info("Restart after failed epic project installation detected");
            checkUncommittedChanges();
        }
        if (installProject) {
            getMavenLog().info("Installing the epic project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Epic update process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(epicBranchLocalName, "breakpoint", "epicUpdate.cleanInstall");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        FailureInfoHelper.installProjectFailure(GOAL, epicBranchLocalName, "epic update", reason));
            }
        }
        gitRemoveBranchLocalConfig(epicBranchLocalName, "breakpoint");
        if (pushRemote) {
            if (!confirmedUpdateWithMerge && deleteRemoteBranchOnRebase) {
                getMavenLog().info("Deleting remote epic branch to not run into non-fast-forward error");
                gitBranchDeleteRemote(epicBranchLocalName);
            }
            getMavenLog().info("Pushing (forced) epic branch '" + epicBranchLocalName + "' to remote repository");
            gitPush(epicBranchLocalName, false, true);
        }
        finalizeEpicUpdateProcess(epicBranchLocalName);
        getMavenLog().info("Epic update process finished");
    }

    private void fixupModuleParents(String epicBranch, String newEpicVersion, String oldEpicVersion,
            String newBaseVersion, boolean confirmedUpdateWithMerge) throws MojoFailureException, CommandLineException {
        if ((confirmedUpdateWithMerge && !newEpicVersion.equals(newBaseVersion))
                || (!confirmedUpdateWithMerge && !newEpicVersion.equals(oldEpicVersion))) {
            getLog().info("Ensure consistent version in all modules");
            String issueNumber = getEpicIssueNumber(epicBranch);
            String epicNewModulesMessage = substituteWithIssueNumber(commitMessages.getEpicNewModulesMessage(),
                    issueNumber);
            mvnFixupVersions(newEpicVersion, epicNewModulesMessage, false, oldEpicVersion, newBaseVersion);
        }
    }

    private List<String> getSubBranches(String epicBranch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        List<String> subBranches = new LinkedList<>();
        // to ensure that corresponding branches started from branch point are also
        // included
        subBranches.addAll(getBranchesWithBaseBranch(epicBranch));
        String firstCommit = gitFirstCommitOnBranch(epicBranch, branchPoint);
        if (firstCommit != null) {
            List<String> branchesWithFirstCommit = gitAllBranchesWithCommit(firstCommit);
            for (String branch : branchesWithFirstCommit) {
                if (!epicBranch.equals(branch) && !subBranches.contains(branch)) {
                    subBranches.add(branch);
                }
            }
        }
        Collections.sort(subBranches);
        return subBranches;
    }

    private void updateEpicByMerge(String epicBranch, String baseBranch, String baseVersion, String oldBaseVersion)
            throws CommandLineException, MojoFailureException {
        if (!versionless && !tychoBuild && !baseVersion.equals(oldBaseVersion)) {
            String issueNumber = getEpicIssueNumber(epicBranch);
            String epicFinishMessage = substituteWithIssueNumber(commitMessages.getEpicFinishMessage(), issueNumber);
            getMavenLog().info("Reverting the project version on epic branch to the last merged base version '"
                    + oldBaseVersion + "'.");
            mvnSetVersions(oldBaseVersion, GitFlowAction.EPIC_FINISH, null, baseBranch);
            gitCommit(epicFinishMessage);
        }
        try {
            getMavenLog().info(
                    "Merging (--no-ff) base branch '" + baseBranch + "' into epic branch '" + epicBranch + "'...");
            gitMerge(baseBranch, true);
        } catch (MojoFailureException ex) {
            getMavenLog().info("Epic update process paused to resolve merge conflicts");
            throw new GitFlowFailureException(ex,
                    "Automatic merge failed.\n" + createMergeConflictDetails(epicBranch, baseBranch, ex),
                    "Fix the merge conflicts and mark them as resolved by using 'git add'.\nIMPORTANT: "
                            + "be sure not to update the version in epic branch while resolving conflicts!\n"
                            + "After that, run 'mvn flow:epic-update' again.\nDo NOT run 'git merge --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                            + "resolved",
                    "'mvn flow:epic-update' to continue epic update process");
        }
    }

    private void updateEpicByRebase(String epicBranch, String baseBranch, String branchPoint, boolean hasMergeCommits)
            throws CommandLineException, MojoFailureException {
        getMavenLog().info("Rebasing epic branch '" + epicBranch + "' on top of base branch '" + baseBranch + "'...");
        // rebase feature on top of development
        String versionChangeCommitOnBranch = gitVersionChangeCommitOnEpicBranch(epicBranch, branchPoint);
        if (versionChangeCommitOnBranch != null) {
            getLog().info("First commit on epic branch is version change commit. Exclude it from rebase.");
            String tempEpicBranch = createTempEpicBranchName(epicBranch);
            getLog().info("Creating temporary branch with new version change commit to be used for epic rebase.");
            if (gitBranchExists(tempEpicBranch)) {
                gitBranchDeleteForce(tempEpicBranch);
            }
            gitCreateAndCheckout(tempEpicBranch, baseBranch);
            String currentVersion = getCurrentProjectVersion();
            String baseVersion = currentVersion;
            gitSetBranchLocalConfig(epicBranch, "newBaseVersion", baseVersion);
            String versionChangeCommit = null;
            String version = createEpicVersion(baseVersion, epicBranch);
            if (!currentVersion.equals(version)) {
                String prevBaseVersion = gitGetBranchCentralConfig(epicBranch, BranchConfigKeys.BASE_VERSION);
                boolean sameBaseVersion = Objects.equals(baseVersion, prevBaseVersion);
                getMavenLog()
                        .info("- setting epic version '" + version + "' for project on branch prepared for rebase");
                String epicStartMessage = getEpicStartCommitMessage(epicBranch);
                versionChangeCommit = mvnSetVersions(version, GitFlowAction.EPIC_UPDATE, "On epic branch: ", epicBranch, sameBaseVersion,
                        epicBranch, epicStartMessage);
                gitSetBranchLocalConfig(epicBranch, "newStartCommitMessage", epicStartMessage);
                gitSetBranchLocalConfig(epicBranch, "newVersionChangeCommit", versionChangeCommit);
            } else {
                getLog().info(
                        "Project version for epic is same as base project version. " + "Version update not needed.");
            }
            try {
                gitRebaseOnto(tempEpicBranch, versionChangeCommitOnBranch, epicBranch, hasMergeCommits);
            } catch (MojoFailureException ex) {
                String rebasePausedLogMessage = "Epic update process paused to resolve rebase conflicts";
                GitFlowFailureInfo rebasePausedFailureInfo = new GitFlowFailureInfo(
                        "Automatic rebase failed.\n" + createMergeConflictDetails(baseBranch, epicBranch, ex),
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:epic-update' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-update' to continue epic update process");
                if (!fixAndContinueIfModuleDeletedConflictDetected(rebasePausedLogMessage, rebasePausedFailureInfo)) {
                    getMavenLog().info(rebasePausedLogMessage);
                    throw new GitFlowFailureException(ex, rebasePausedFailureInfo);
                }
            }
        } else {
            getLog().info(
                    "First commit on epic branch is not a version change commit. " + "Rebase the whole epic branch.");
            try {
                gitRebase(baseBranch);
            } catch (MojoFailureException ex) {
                String rebasePausedLogMessage = "Epic update process paused to resolve rebase conflicts";
                GitFlowFailureInfo rebasePausedFailureInfo = new GitFlowFailureInfo(
                        "Automatic rebase failed.\n" + createMergeConflictDetails(baseBranch, epicBranch, ex),
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:epic-update' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-update' to continue epic update process");
                if (!fixAndContinueIfModuleDeletedConflictDetected(rebasePausedLogMessage, rebasePausedFailureInfo)) {
                    getMavenLog().info(rebasePausedLogMessage);
                    throw new GitFlowFailureException(ex, rebasePausedFailureInfo);
                }
            }
        }
    }

    private String createEpicVersion(String baseVersion, String featureBranchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Project version: " + baseVersion);
        String version = baseVersion;
        String epicIssue = getEpicIssueNumber(featureBranchName);
        getLog().info("Epic issue number read from central branch config: " + epicIssue);
        version = insertSuffixInVersion(version, epicIssue);
        getLog().info("Added feature issue number to project version: " + version);
        return version;
    }

    private void continueEpicUpdate(boolean confirmedUpdateWithMerge, String epicBranch)
            throws CommandLineException, MojoFailureException {
        if (confirmedUpdateWithMerge) {
            // continue with commit
            if (!getPrompter().promptConfirmation(
                    "You have a merge in process on your current branch. If you run 'mvn flow:epic-update' before "
                            + "and merge had conflicts you can continue. In other case it is better to clarify the "
                            + "reason of merge in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of epic update aborted by user.", null);
            }
            getMavenLog().info("Continue merging base branch into epic branch...");
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Epic update process paused to resolve merge conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge.\n"
                                + createMergeConflictDetails(epicBranch, "base branch", exc),
                        "Fix the merge conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:epic-update' again.\nDo NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-update' to continue epic update process");
            }
        } else {
            // continue with the rebase
            if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                    + "If you run 'mvn flow:epic-update' before and rebase had conflicts you can "
                    + "continue. In other case it is better to clarify the reason of rebase in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of epic update aborted by user.", null);
            }
            getMavenLog().info("Continue rebasing epic branch on top of base branch...");
            try {
                gitRebaseContinueOrSkip();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Epic update process paused to resolve rebase conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after rebase.\n"
                                + createMergeConflictDetails("base branch", epicBranch, exc),
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:epic-update' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:epic-update' to continue epic update process");
            }
        }
    }

    private void finalizeRebase(String epicBranch) throws MojoFailureException, CommandLineException {
        String tempEpicBranch = createTempEpicBranchName(epicBranch);
        if (gitBranchExists(tempEpicBranch)) {
            getLog().info("Deleting temporary branch used for epic rebase.");
            gitBranchDeleteForce(tempEpicBranch);
        }
    }

    private void finalizeEpicUpdateProcess(String epicBranch) throws MojoFailureException, CommandLineException {
        String newBaseVersion = gitGetBranchLocalConfig(epicBranch, "newBaseVersion");
        if (newBaseVersion != null) {
            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(epicBranch, BranchConfigKeys.BASE_VERSION, newBaseVersion);
            branchConfigChanges.set(epicBranch, BranchConfigKeys.START_COMMIT_MESSAGE,
                    gitGetBranchLocalConfig(epicBranch, "newStartCommitMessage"));
            branchConfigChanges.set(epicBranch, BranchConfigKeys.VERSION_CHANGE_COMMIT,
                    gitGetBranchLocalConfig(epicBranch, "newVersionChangeCommit"));
            gitApplyBranchCentralConfigChanges(branchConfigChanges, "epic branch '" + epicBranch + "' rebased");
        }
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
