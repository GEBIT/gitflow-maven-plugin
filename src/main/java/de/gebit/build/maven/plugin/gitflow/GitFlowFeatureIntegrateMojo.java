//
// GitFlowFeatureIntegrateMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.steps.FeatureIntegrateBreakpoint;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureIntegrateStep;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureIntegrateStepParameters;
import de.gebit.build.maven.plugin.gitflow.steps.Step;
import de.gebit.build.maven.plugin.gitflow.steps.StepsUtil;

/**
 * Integrate the current feature branch into another feature branch.
 * <p>
 * Integrates the changes from the current feature branch into another feature
 * branch to be tested together before finishing. The optional property
 * <code>featureName</code> specifies the name of the target feature, e.g.
 * <code>XYZ-1234</code> for the target feature branch <b>feature/XYZ-1234</b>.
 * The target feature branch must exist.
 * <p>
 * If conflicts occur during rebase, you can fix the conflicts and continue the
 * integration process by executing <code>flow:feature-integrate</code> again or
 * you can abort the integration process by executing
 * <code>flow:feature-integrate-abort</code>.
 * <p>
 * Example:
 * <pre>
 * mvn -N flow:feature-integrate [-DfeatureName=XXXX] [-Dflow.keepFeatureBranch=true|false] [-Dflow.installProject=true|false]
 * </pre>
 * @author Volodymyr Medvid
 * @since 2.1.0
 * @see GitFlowFeatureIntegrateAbortMojo
 */
@Mojo(name = GitFlowFeatureIntegrateMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureIntegrateMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-integrate";

    /** Whether to keep feature branch after in. */
    @Parameter(property = "flow.keepFeatureBranch", defaultValue = "false")
    private boolean keepFeatureBranch = false;

    /**
     * The target feature name which the current feature branch will be integrated
     * into.
     */
    @Parameter(property = "featureName", defaultValue = "${featureName}", required = false, readonly = true)
    protected String featureName;

    /**
     * Whether to squash a commit with correction of version for new modules and
     * single feature commit.
     */
    @Parameter(property = "flow.squashNewModuleVersionFixCommit", defaultValue = "false")
    private boolean squashNewModuleVersionFixCommit = false;

    private final List<Step<FeatureIntegrateBreakpoint, FeatureIntegrateStepParameters>> allProcessSteps = Arrays
            .asList(new FeatureIntegrateStep(this::selectTargetFeatureBranch),
                    new FeatureIntegrateStep(this::rebaseSourceFeatureBranchOnTopTargetFeatureBranch,
                            FeatureIntegrateBreakpoint.REBASE),
                    new FeatureIntegrateStep(this::buildTargetFeatureProject, FeatureIntegrateBreakpoint.CLEAN_INSTALL),
                    new FeatureIntegrateStep(this::finalizeFeatureIntegrate));

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature integration process");
        checkCentralBranchConfig();
        StepsUtil.processSteps(allProcessSteps, this::getBreakpoint, this::initParameters);
        getMavenLog().info("Feature integration process finished");
    }

    /**
     * Check the breakpoint marker stored into branch local config.
     *
     * @return the type of marked breakpoint or <code>null</code> if no breakpoint
     *         found.
     */
    private FeatureIntegrateBreakpoint getBreakpoint() throws MojoFailureException, CommandLineException {
        String branch;
        branch = gitRebaseBranchInProcess();
        if (branch == null) {
            branch = gitCurrentBranch();
        }
        String breakpointId = gitGetBranchLocalConfig(branch, "breakpoint");
        if (breakpointId != null) {
            return FeatureIntegrateBreakpoint.valueById(breakpointId);
        }
        return null;
    }

    /**
     * Initialize step parameters depending on passes breakpoint.
     *
     * @param breakpoint
     *            the breakpoint to consider while parameters initialization
     * @return initialized parameters
     */
    private FeatureIntegrateStepParameters initParameters(FeatureIntegrateBreakpoint breakpoint)
            throws MojoFailureException, CommandLineException {
        FeatureIntegrateStepParameters stepParameters = new FeatureIntegrateStepParameters();
        stepParameters.breakpoint = breakpoint;
        if (breakpoint != null) {
            switch (breakpoint) {
            case REBASE:
                String rebasingBranch = gitRebaseBranchInProcess();
                stepParameters.tempSourceFeatureBranch = rebasingBranch;
                stepParameters.targetFeatureBranch = gitGetBranchLocalConfig(rebasingBranch, "targetFeatureBranch");
                stepParameters.sourceFeatureBranch = gitGetBranchLocalConfig(rebasingBranch, "sourceFeatureBranch");
                stepParameters.sourceBaseBranch = gitFeatureBranchBaseBranch(stepParameters.sourceFeatureBranch);
                break;
            case CLEAN_INSTALL:
                String currentBranch = gitCurrentBranch();
                stepParameters.targetFeatureBranch = currentBranch;
                stepParameters.sourceFeatureBranch = gitGetBranchLocalConfig(currentBranch, "sourceFeatureBranch");
                stepParameters.sourceBaseBranch = gitFeatureBranchBaseBranch(stepParameters.sourceFeatureBranch);
                break;
            }
        }
        return stepParameters;
    }

    private FeatureIntegrateStepParameters selectTargetFeatureBranch(FeatureIntegrateStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        checkUncommittedChanges();

        String currentBranch = gitCurrentBranch();
        if (!isFeatureBranch(currentBranch)) {
            throw new GitFlowFailureException(
                    "'mvn flow:feature-integrate' can be executed only on the feature branch that should be integrated "
                            + "into another feature branch.",
                    "Please switch to a feature branch first.",
                    "'git checkout INTERNAL' to switch to the feature branch");
        }
        String sourceFeatureBranch = currentBranch;

        List<String> branches = gitAllFeatureBranches();
        branches.remove(sourceFeatureBranch);
        if (branches.isEmpty()) {
            throw new GitFlowFailureException(
                    "There are no feature branches except current feature branch in your repository.",
                    "Please start a feature first which the current feature branch '" + sourceFeatureBranch
                            + "' should be integrated into.",
                    "'mvn flow:feature-start'");
        }
        gitEnsureLocalBranchIsUpToDateIfExists(sourceFeatureBranch,
                new GitFlowFailureInfo("Remote and local feature branches '" + sourceFeatureBranch + "' diverge.",
                        "Rebase the changes in local feature branch '" + sourceFeatureBranch + "' first.",
                        "'git rebase' to rebase the changes in local feature branch"));
        String sourceBaseBranch = gitFeatureBranchBaseBranch(sourceFeatureBranch);
        if (!hasCommitsExceptVersionChangeCommitOnFeatureBranch(sourceFeatureBranch, sourceBaseBranch)) {
            throw new GitFlowFailureException(
                    "There are no real changes in current feature branch '" + sourceFeatureBranch + "'.",
                    "Delete the feature branch or commit some changes first.",
                    "'mvn flow:feature-abort' to delete the feature branch",
                    "'git add' and 'git commit' to commit some changes into feature branch and "
                            + "'mvn flow:feature-integrate' to run the feature integration again");
        }
        if (!gitHasNoMergeCommits(sourceFeatureBranch, sourceBaseBranch)) {
            getLog().info("Feature branch contains merge commits. Removing of version change commit is not possible.");
            throw new GitFlowFailureException(
                    "Current feature branch '" + sourceFeatureBranch
                            + "' contains merge commits. Integration of this feature branch is not possible.",
                    "Finish the current feature without integration.",
                    "'mvn flow:feature-finish' to finish the feature and marge it to the development branch");
        }
        featureName = featureName == null ? null : StringUtils.deleteWhitespace(featureName);
        String targetFeatureBranch;
        if (featureName == null || featureName.isEmpty()) {
            targetFeatureBranch = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                    "Choose the target feature branch which the current feature branch should be integrated into",
                    branches,
                    new GitFlowFailureInfo(
                            "Property 'featureName' is required in non-interactive mode but was not set.",
                            "Specify a target featureName or run in interactive mode.",
                            "'mvn flow:feature-integrate -DfeatureName=XXX -B'", "'mvn flow:feature-integrate'"));
        } else {
            targetFeatureBranch = gitFlowConfig.getFeatureBranchPrefix() + featureName;
            if (!gitBranchExists(targetFeatureBranch) && !gitRemoteBranchExists(targetFeatureBranch)) {
                throw new GitFlowFailureException("Target feature branch '" + targetFeatureBranch + "' doesn't exist.",
                        "Either provide featureName of an existing feature branch or run without 'featureName' in "
                                + "interactive mode.",
                        "'mvn flow:feature-integrate' to run in intractive mode and select an existing target branch");
            }
        }
        if (sourceFeatureBranch.equals(targetFeatureBranch)) {
            throw new GitFlowFailureException(
                    "The target feature branch can not be the same as current feature branch.",
                    "Please select a target feature branch different from current feature branch '"
                            + sourceFeatureBranch + "'.",
                    "'mvn flow:feature-integrate'");
        }
        gitEnsureLocalBranchIsUpToDateIfExists(targetFeatureBranch,
                new GitFlowFailureInfo(
                        "Remote and local target feature branches '" + targetFeatureBranch + "' diverge.",
                        "Rebase or merge the changes in local feature branch '" + targetFeatureBranch + "' first.",
                        "'git checkout " + targetFeatureBranch + "' and 'git rebase' to rebase the changes in local "
                                + "target feature branch"));
        getMavenLog().info("Integrating current feature branch '" + sourceFeatureBranch
                + "' into target feature branch '" + targetFeatureBranch + "'");
        String targetBaseBranch = gitFeatureBranchBaseBranch(targetFeatureBranch);
        if (!sourceBaseBranch.equals(targetBaseBranch)) {
            throw new GitFlowFailureException(
                    "The current and target feature branches have different base branches:\n" + "'"
                            + sourceFeatureBranch + "' -> '" + sourceBaseBranch + "'\n" + "'" + targetFeatureBranch
                            + "' -> '" + targetBaseBranch + "'\n",
                    "Please select a target feature branch that has the same base branch '" + sourceBaseBranch
                            + "' as current feature branch.",
                    "'mvn flow:feature-integrate'");
        }
        stepParameters.sourceFeatureBranch = sourceFeatureBranch;
        stepParameters.sourceBaseBranch = sourceBaseBranch;
        stepParameters.targetFeatureBranch = targetFeatureBranch;
        return stepParameters;
    }

    private FeatureIntegrateStepParameters rebaseSourceFeatureBranchOnTopTargetFeatureBranch(
            FeatureIntegrateStepParameters stepParameters) throws CommandLineException, MojoFailureException {
        String sourceFeatureBranch = stepParameters.sourceFeatureBranch;
        String targetFeatureBranch = stepParameters.targetFeatureBranch;
        String sourceBaseBranch = stepParameters.sourceBaseBranch;
        String tempSourceFeatureBranch = stepParameters.tempSourceFeatureBranch;
        if (stepParameters.breakpoint != FeatureIntegrateBreakpoint.REBASE) {
            getLog().info("Rebasing source feature branch '" + sourceFeatureBranch
                    + "' on top of target feature branch '" + targetFeatureBranch + "'...");
            String sourceFeatureBaseCommit = gitBranchPoint(sourceBaseBranch, sourceFeatureBranch);
            String versionChangeCommitOnBranch = gitVersionChangeCommitOnFeatureBranch(sourceFeatureBranch,
                    sourceFeatureBaseCommit);
            if (versionChangeCommitOnBranch != null) {
                sourceFeatureBaseCommit = versionChangeCommitOnBranch;
                getLog().info(
                        "First commit on source feature branch is version change commit. Exclude it from rebase.");
            } else {
                getLog().info("First commit on source feature branch is not a version change commit. "
                        + "Rebase the whole source feature branch.");
            }
            tempSourceFeatureBranch = createTempFeatureBranchName(sourceFeatureBranch);
            getLog().info("Creating temp copy of source feature branch to be used for rebase on top of target feature "
                    + "branch.");
            if (gitBranchExists(tempSourceFeatureBranch)) {
                gitBranchDeleteForce(tempSourceFeatureBranch);
            }
            gitCreateAndCheckout(tempSourceFeatureBranch, sourceFeatureBranch);
            stepParameters.tempSourceFeatureBranch = tempSourceFeatureBranch;
            try {
                gitRebaseOnto(targetFeatureBranch, sourceFeatureBaseCommit, tempSourceFeatureBranch);
            } catch (MojoFailureException ex) {
                Map<String, String> configs = new HashMap<>();
                configs.put("sourceFeatureBranch", sourceFeatureBranch);
                configs.put("targetFeatureBranch", stepParameters.targetFeatureBranch);
                setBreakpoint(FeatureIntegrateBreakpoint.REBASE, tempSourceFeatureBranch, configs);
                getMavenLog().info("Feature rebase process paused to resolve rebase conflicts");
                throw new GitFlowFailureException(ex,
                        "Automatic rebase of feature branch '" + sourceFeatureBranch + "' on top of feature branch '"
                                + targetFeatureBranch + "' failed.\nGit error message:\n"
                                + StringUtils.trim(ex.getMessage()),
                        "Fix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-integrate' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-integrate' to continue feature integration process",
                        "'mvn flow:feature-integrate-abort' to abort feature integration process");
            }
        } else {
            continueRebase(sourceFeatureBranch, targetFeatureBranch);
        }
        fixupModuleParents(sourceFeatureBranch, targetFeatureBranch);
        gitUpdateRef(targetFeatureBranch, tempSourceFeatureBranch);
        gitCheckout(targetFeatureBranch);
        gitBranchDeleteForce(tempSourceFeatureBranch);
        return stepParameters;
    }

    private void fixupModuleParents(String sourceFeatureBranch, String targetFeatureBranch)
            throws MojoFailureException, CommandLineException {
        getLog().info("Ensure consistent version in all modules");
        String version = getCurrentProjectVersion();
        String issueNumber = getFeatureIssueNumber(sourceFeatureBranch);
        String featureFinishMessage = substituteWithIssueNumber(commitMessages.getFeatureFinishMessage(), issueNumber);
        boolean amend = false;
        if (squashNewModuleVersionFixCommit) {
            int featureCommits = gitGetDistanceToAncestor(getCurrentCommit(), targetFeatureBranch);
            amend = (featureCommits == 1);
        }
        mvnFixupVersions(version, issueNumber, featureFinishMessage, amend);
    }

    private void continueRebase(String sourceFeatureBranch, String targetFeatureBranch)
            throws CommandLineException, MojoFailureException {
        if (!getPrompter().promptConfirmation(
                "You have a rebase in process on your current branch. "
                        + "If you run 'mvn flow:feature-integrate' before and rebase had conflicts you can "
                        + "continue. In other case it is better to clarify the reason of rebase in process. Continue?",
                true, true)) {
            throw new GitFlowFailureException("Continuation of feature integrate process aborted by user.", null);
        }
        getMavenLog().info("Continue feature integration into feature '" + targetFeatureBranch + "'...");
        try {
            gitRebaseContinueOrSkip();
        } catch (MojoFailureException exc) {
            getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
            throw new GitFlowFailureException(exc,
                    "Automatic rebase of feature branch '" + sourceFeatureBranch + "' on top of feature branch '"
                            + targetFeatureBranch + "' failed.\nGit error message:\n"
                            + StringUtils.trim(exc.getMessage()),
                    "Fix the rebase conflicts and mark them as resolved. After that, run "
                            + "'mvn flow:feature-integrate' again.\n"
                            + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                            + "conflicts as resolved",
                    "'mvn flow:feature-integrate' to continue feature integration process",
                    "'mvn flow:feature-integrate-abort' to abort feature integration process");
        }
    }

    private FeatureIntegrateStepParameters buildTargetFeatureProject(FeatureIntegrateStepParameters stepParameters)
            throws CommandLineException, MojoFailureException, GitFlowFailureException {
        String sourceFeatureBranch = stepParameters.sourceFeatureBranch;
        String targetFeatureBranch = stepParameters.targetFeatureBranch;

        if (stepParameters.breakpoint == FeatureIntegrateBreakpoint.CLEAN_INSTALL) {
            getMavenLog().info("Restart after failed project installation on integrated feature branch detected");
            checkUncommittedChanges();
        }
        if (installProject) {
            getMavenLog().info("Installing the project on integrated feature branch '" + targetFeatureBranch + "'...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info(
                        "Feature integrate process paused on failed project installation to fix project problems");
                Map<String, String> configs = new HashMap<>();
                configs.put("sourceFeatureBranch", sourceFeatureBranch);
                setBreakpoint(FeatureIntegrateBreakpoint.CLEAN_INSTALL, targetFeatureBranch, configs);
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e, FailureInfoHelper.installProjectFailure(GOAL, targetFeatureBranch,
                        "feature integration", reason));
            }
        }
        return stepParameters;
    }

    private FeatureIntegrateStepParameters finalizeFeatureIntegrate(FeatureIntegrateStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String sourceFeatureBranch = stepParameters.sourceFeatureBranch;
        String targetFeatureBranch = stepParameters.targetFeatureBranch;

        removeBreakpoints(targetFeatureBranch);
        // first push modified branches
        if (pushRemote) {
            getMavenLog().info("Pushing integrated feature branch '" + targetFeatureBranch + "' to remote repository");
            gitPush(targetFeatureBranch, false, false);
        }
        // then delete if wanted
        if (!keepFeatureBranch) {
            getMavenLog().info("Removing local source feature branch '" + sourceFeatureBranch + "'");
            gitBranchDeleteForce(sourceFeatureBranch);

            if (pushRemote) {
                getMavenLog().info("Removing remote source feature branch '" + sourceFeatureBranch + "'");
                gitBranchDeleteRemote(sourceFeatureBranch);
            }
            String featureName = sourceFeatureBranch.substring(gitFlowConfig.getFeatureBranchPrefix().length());
            gitRemoveAllBranchCentralConfigsForBranch(sourceFeatureBranch,
                    "feature '" + featureName + "' integrated");
        }
        return stepParameters;
    }

    /**
     * Store breakpoint type and additional configs into branch local config.
     *
     * @param breakpoint
     *            the breakpoint type
     * @param branch
     *            the branch in which config should be stored
     * @param additionalConfigs
     *            optional additional configs
     */
    private void setBreakpoint(FeatureIntegrateBreakpoint breakpoint, String branch,
            Map<String, String> additionalConfigs) throws MojoFailureException, CommandLineException {
        gitSetBranchLocalConfig(branch, "breakpoint", breakpoint.getId());
        for (Entry<String, String> config : additionalConfigs.entrySet()) {
            gitSetBranchLocalConfig(branch, config.getKey(), config.getValue());
        }
    }

    /**
     * Remove breakpoint information and known additional configs from branch local
     * config.
     *
     * @param targetFeatureBranch
     *            the target feature branch name
     */
    private void removeBreakpoints(String targetFeatureBranch) throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(targetFeatureBranch, "breakpoint");
        gitRemoveBranchLocalConfig(targetFeatureBranch, "sourceFeatureBranch");
    }

}
