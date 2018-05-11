/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.Collections;
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

/**
 * The git flow feature finish mojo.
 *
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "feature-finish", aggregator = true)
public class GitFlowFeatureFinishMojo extends AbstractGitFlowFeatureMojo {

    /** Whether to keep feature branch after finish. */
    @Parameter(property = "keepFeatureBranch", defaultValue = "false")
    private boolean keepFeatureBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * You can try a rebase of the feature branch skipping the initial commit
     * that update the pom versions just before finishing a feature. The
     * operation will peform a rebase, which may not finish successfully. You
     * can make your changes and run feature-finish again in that case. <br>
     * Note: problems arise if you're modifying the poms near the version
     * number. You will need to fix those conflicts before running
     * feature-finish again, as otherwise the pom will be invalid and the
     * process cannot be started. If you cannot fix the pom into a working state
     * with the current commit you can manually issue a
     * <code>git rebase --continue</code>.
     *
     * @since 1.3.0
     */
    @Parameter(property = "rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

    /**
     * Whether to allow fast forward merge of feature branch into development
     * branch.
     *
     * @since 2.0.0
     */
    @Parameter(property = "allowFF", defaultValue = "false")
    private boolean allowFF = false;

    private final Step[] allProcessSteps = { new Step(this::selectFeatureAndBaseBranches),
            new Step(this::ensureBranchesPreparedForFeatureFinish, Breakpoint.REBASE_BEFORE_FINISH),
            new Step(this::verifyFeatureProject),
            new Step(this::revertProjectVersion, Breakpoint.REBASE_WITHOUT_VERSION_CHANGE),
            new Step(this::mergeIntoBaseBranch, Breakpoint.FINAL_MERGE),
            new Step(this::buildBaseProject, Breakpoint.CLEAN_INSTALL), new Step(this::finalizeFeatureFinish) };

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getLog().info("Starting feature finish process.");
        checkCentralBranchConfig();

        Breakpoint breakpoint = getBreakpoint();
        Step[] steps = getStepsToExecute(breakpoint);
        StepParameters stepParameters = initParameters(breakpoint);
        for (Step step : steps) {
            getLog().info("Executing step: " + step.executor);
            stepParameters = step.execute(stepParameters);
        }
        getLog().info("Feature finish process finished.");
    }

    /**
     * Check the breakpoint marker stored into branch local config.
     *
     * @return the type of marked breakpoint or <code>null</code> if no
     *         breakpoint found.
     */
    private Breakpoint getBreakpoint() throws MojoFailureException, CommandLineException {
        String branch = gitMergeFromFeatureBranchInProcess();
        if (branch == null) {
            branch = gitRebaseFeatureBranchInProcess();
            if (branch == null) {
                branch = gitCurrentBranch();
            }
        }
        String breakpointId = gitGetBranchLocalConfig(branch, "breakpoint");
        if (breakpointId != null) {
            return Breakpoint.valueById(breakpointId);
        }
        return null;
    }

    /**
     * Get steps to be executed.<br>
     * If breakpoint parameter is <code>null</code> then returns all steps.<br>
     * If breakpoint parameter is not <code>null</code> then returns all steps
     * beginning from the breakpoint.
     *
     * @param breakpoint
     *            the breakpoint which to start from or <code>null</code> to get
     *            all steps
     * @return the steps to be executed
     */
    private Step[] getStepsToExecute(Breakpoint breakpoint) {
        List<Step> steps = new ArrayList<>();
        for (int i = allProcessSteps.length - 1; i >= 0; i--) {
            steps.add(allProcessSteps[i]);
            if (breakpoint != null && breakpoint == allProcessSteps[i].breakpoint) {
                break;
            }
        }
        Collections.reverse(steps);
        return steps.toArray(new Step[steps.size()]);
    }

    /**
     * Initialize step parameters depending on passes breakpoint.
     *
     * @param breakpoint
     *            the breakpoint to consider while parameters initialization
     * @return initialized parameters
     */
    private StepParameters initParameters(Breakpoint breakpoint) throws MojoFailureException, CommandLineException {
        StepParameters stepParameters = new StepParameters();
        stepParameters.breakpoint = breakpoint;
        if (breakpoint != null) {
            switch (breakpoint) {
            case REBASE_BEFORE_FINISH:
                stepParameters.featureBranch = gitRebaseFeatureBranchInProcess();
                // TODO: init parameters!!!
                break;
            case REBASE_WITHOUT_VERSION_CHANGE:
                stepParameters.featureBranch = gitRebaseFeatureBranchInProcess();
                break;
            case FINAL_MERGE:
                stepParameters.featureBranch = gitMergeFromFeatureBranchInProcess();
                break;
            case CLEAN_INSTALL:
                String currentBranch = gitCurrentBranch();
                stepParameters.baseBranch = currentBranch;
                stepParameters.featureBranch = gitGetBranchLocalConfig(currentBranch, "breakpointFeatureBranch");
                break;
            }
        }
        return stepParameters;
    }

    private StepParameters selectFeatureAndBaseBranches(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        // check uncommitted changes
        checkUncommittedChanges();

        List<String> branches = gitAllFeatureBranches();
        if (branches.isEmpty()) {
            throw new GitFlowFailureException("There are no feature branches in your repository.",
                    "Please start a feature first.", "'mvn flow:feature-start'");
        }
        String currentBranch = gitCurrentBranch();
        boolean isOnFeatureBranch = branches.contains(currentBranch);

        String featureBranch = getFeatureBranchToFinish(currentBranch, isOnFeatureBranch, branches);

        String baseBranch = gitFeatureBranchBaseBranch(featureBranch);

        stepParameters.featureBranch = featureBranch;
        stepParameters.baseBranch = baseBranch;
        stepParameters.isOnFeatureBranch = isOnFeatureBranch;
        return stepParameters;
    }

    private StepParameters ensureBranchesPreparedForFeatureFinish(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String baseBranch = stepParameters.baseBranch;
        String featureBranch = stepParameters.featureBranch;
        gitEnsureLocalBranchIsUpToDateIfExists(baseBranch,
                new GitFlowFailureInfo("Remote and local base branches '" + baseBranch + "' diverge.",
                        "Rebase the changes in local branch '" + baseBranch
                                + "' and then include these changes in the feature branch '" + featureBranch
                                + "' in order to proceed.",
                        "'git checkout " + baseBranch + "' and 'git rebase' to rebase the changes in base branch '"
                                + baseBranch + "'",
                        "'git checkout " + featureBranch
                                + "' and 'mvn flow:feature-rebase' to include these changes in the feature branch '"
                                + featureBranch + "'"));
        if (!hasCommitsExceptVersionChangeCommitOnFeatureBranch(featureBranch, baseBranch)) {
            throw new GitFlowFailureException("There are no real changes in feature branch '" + featureBranch + "'.",
                    "Delete the feature branch or commit some changes first.",
                    "'mvn flow:feature-abort' to delete the feature branch",
                    "'git add' and 'git commit' to commit some changes into feature branch and "
                            + "'mvn flow:feature-finish' to run the feature finish again");
        }
        if (!gitIsAncestorBranch(baseBranch, featureBranch)) {
            boolean confirmed = getPrompter()
                    .promptConfirmation(
                            "Base branch '" + baseBranch + "' has changes that are not yet included in feature branch "
                                    + "'" + featureBranch + "'. If you continue it will be tryed to merge the changes. "
                                    + "But it is strongly recomended to run 'mvn flow:feature-rebase' first and then "
                                    + "run 'mvn flow:feature-finish' again. Are you sure you want to continue?",
                            false, false);
            if (!confirmed) {
                throw new GitFlowFailureException(
                        "Base branch '" + baseBranch + "' has changes that are not yet included in feature branch '"
                                + featureBranch + "'.",
                        "Rebase the feature branch first in order to proceed.",
                        "'mvn flow:feature-rebase' to rebase the feature branch");
            }
        }

        if (stepParameters.isOnFeatureBranch == null) {
            String currentBranch = gitCurrentBranch();
            stepParameters.isOnFeatureBranch = (currentBranch.equals(featureBranch));
        }

        if (!stepParameters.isOnFeatureBranch) {
            gitCheckout(featureBranch);
        }

        return stepParameters;
    }

    private StepParameters verifyFeatureProject(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        if (!skipTestProject) {
            mvnCleanVerify();
        }
        return stepParameters;
    }

    private StepParameters revertProjectVersion(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint != Breakpoint.REBASE_WITHOUT_VERSION_CHANGE) {
            String baseBranch = stepParameters.baseBranch;
            String featureVersion = getCurrentProjectVersion();
            getLog().info("Project version on feature branch: " + featureVersion);

            gitCheckout(baseBranch);

            String baseVersion = getCurrentProjectVersion();
            getLog().info("Project version on base branch: " + baseVersion);

            boolean rebased = rebaseToRemoveVersionChangeCommit(featureBranch, baseBranch);
            if (!rebased && !tychoBuild) {
                // rebase not configured or not possible, then manually
                // revert the version
                revertProjectVersionManually(featureBranch, featureVersion, baseVersion);
            }
        } else {
            stepParameters.baseBranch = continueRebaseToRemoveVersionChangeCommit(featureBranch);
        }
        return stepParameters;
    }

    private StepParameters mergeIntoBaseBranch(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint != Breakpoint.FINAL_MERGE) {
            String baseBranch = stepParameters.baseBranch;
            // git checkout develop
            gitCheckout(baseBranch);
            // git merge --no-ff feature/...
            try {
                getLog().info("Merging feature branch into base branch.");
                gitMerge(featureBranch, !allowFF);
            } catch (MojoFailureException ex) {
                setBreakpoint(Breakpoint.FINAL_MERGE, featureBranch);
                throw new GitFlowFailureException(ex,
                        "Automatic merge failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again. Do NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process");
            }
        } else {
            stepParameters.baseBranch = continueMergeOntoBaseBranch();
        }
        return stepParameters;
    }

    private StepParameters buildBaseProject(StepParameters stepParameters)
            throws CommandLineException, MojoFailureException, GitFlowFailureException {
        String baseBranch = stepParameters.baseBranch;
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint == Breakpoint.CLEAN_INSTALL) {
            checkUncommittedChanges();
        }
        if (installProject) {
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                Map<String, String> configs = new HashMap<>();
                configs.put("breakpointFeatureBranch", featureBranch);
                setBreakpoint(Breakpoint.CLEAN_INSTALL, baseBranch, configs);
                throw new GitFlowFailureException(e,
                        "Failed to execute 'mvn clean install' on the project on base branch '" + baseBranch
                                + "' after feature finish.",
                        "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                                + "'mvn flow:feature-finish' again in order to continue.\n"
                                + "Do NOT push the base branch!");
            }
        }
        return stepParameters;
    }

    private StepParameters finalizeFeatureFinish(StepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String baseBranch = stepParameters.baseBranch;
        String featureBranch = stepParameters.featureBranch;

        removeBreakpoints(featureBranch, baseBranch);
        if (!keepFeatureBranch) {
            getLog().info("Removing local feature branch.");
            gitBranchDeleteForce(featureBranch);

            if (pushRemote) {
                getLog().info("Removing remote feature branch.");
                gitBranchDeleteRemote(featureBranch);
            }
        }
        if (pushRemote) {
            gitPush(baseBranch, false, false);
        }
        return stepParameters;
    }

    private String getFeatureBranchToFinish(String currentBranch, boolean isOnFeatureBranch,
            List<String> allFeatureBranches)
            throws GitFlowFailureException, MojoFailureException, CommandLineException {
        String featureBranch;
        if (!isOnFeatureBranch) {
            featureBranch = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                    "Choose feature branch to finish", allFeatureBranches,
                    new GitFlowFailureInfo(
                            "In non-interactive mode 'mvn flow:feature-finish' can be executed only on a feature branch.",
                            "Please switch to a feature branch first or run in interactive mode.",
                            "'git checkout BRANCH' to switch to the feature branch",
                            "'mvn flow:feature-finish' to run in interactive mode"));
            getLog().info("Finishing feature on selected feature branch: " + featureBranch);

            // git checkout feature/...
            gitEnsureLocalBranchIsUpToDateIfExists(featureBranch,
                    new GitFlowFailureInfo("Remote and local feature branches '" + featureBranch + "' diverge.",
                            "Rebase or merge the changes in local feature branch '" + featureBranch + "' first.",
                            "'git rebase'"));
        } else {
            featureBranch = currentBranch;
            getLog().info("Finishing feature on current feature branch: " + featureBranch);
            gitEnsureCurrentLocalBranchIsUpToDate(
                    new GitFlowFailureInfo("Remote and local feature branches '{0}' diverge.",
                            "Rebase or merge the changes in local feature branch '{0}' first.", "'git rebase'"));
        }
        return featureBranch;
    }

    private boolean rebaseToRemoveVersionChangeCommit(String featureBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        boolean rebased = false;
        if (rebaseWithoutVersionChange) {
            String branchPoint = gitBranchPoint(featureBranch, baseBranch);
            String firstCommitOnBranch = gitVersionChangeCommitOnFeatureBranch(featureBranch, branchPoint);
            getLog().debug("branch point is " + branchPoint + ", version change commit is " + firstCommitOnBranch);
            if (firstCommitOnBranch != null) {
                rebased = gitTryRebaseWithoutVersionChange(featureBranch, branchPoint, firstCommitOnBranch);
            }
        }
        return rebased;
    }

    private void revertProjectVersionManually(String featureBranch, String featureVersion, String baseVersion)
            throws MojoFailureException, CommandLineException {
        if (!featureVersion.equals(baseVersion)) {
            getLog().info("Reverting the project version on feature branch to the version on base branch.");
            gitCheckout(featureBranch);
            String issueNumber = getFeatureIssueNumber(featureBranch);
            String featureFinishMessage = substituteWithIssueNumber(commitMessages.getFeatureFinishMessage(),
                    issueNumber);
            mvnSetVersions(baseVersion);
            gitCommit(featureFinishMessage);
        } else {
            getLog().info("Project version on feature branch is same as project version on base branch. "
                    + "Version update not needed.");
        }
    }

    private String continueRebaseToRemoveVersionChangeCommit(String featureBranch)
            throws GitFlowFailureException, CommandLineException, MojoFailureException {
        if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                + "If you run 'mvn flow:feature-finish' before and rebase had conflicts you can "
                + "continue. In other case it is better to clarify the reason of rebase in process. " + "Continue?",
                true, true)) {
            throw new GitFlowFailureException("Continuation of feature finish aborted by user.", null);
        }
        try {
            gitRebaseContinueOrSkip();
        } catch (MojoFailureException exc) {
            throw new GitFlowFailureException(exc,
                    "There are unresolved conflicts after rebase.\nGit error message:\n"
                            + StringUtils.trim(exc.getMessage()),
                    "Fix the rebase conflicts and mark them as resolved. "
                            + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git rebase --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                    "'mvn flow:feature-finish' to continue feature finish process");
        }
        return gitFeatureBranchBaseBranch(featureBranch);
    }

    private String continueMergeOntoBaseBranch()
            throws GitFlowFailureException, CommandLineException, MojoFailureException {
        if (!getPrompter().promptConfirmation(
                "You have a merge in process on your current branch. If you run 'mvn flow:feature-finish' before "
                        + "and merge had conflicts you can continue. In other case it is better to clarify the "
                        + "reason of merge in process. Continue?",
                true, true)) {
            throw new GitFlowFailureException("Continuation of feature finish aborted by user.", null);
        }
        try {
            gitCommitMerge();
        } catch (MojoFailureException exc) {
            throw new GitFlowFailureException(exc,
                    "There are unresolved conflicts after merge.\nGit error message:\n"
                            + StringUtils.trim(exc.getMessage()),
                    "Fix the merge conflicts and mark them as resolved. "
                            + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git merge --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                    "'mvn flow:feature-finish' to continue feature finish process");
        }
        return gitCurrentBranch();
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
    private boolean gitTryRebaseWithoutVersionChange(String featureBranch, String branchPoint,
            String versionChangeCommitId) throws MojoFailureException, CommandLineException {
        getLog().info("First commit on feature branch is version change commit. "
                + "Trying to remove version change commit using rebase.");
        if (!gitHasNoMergeCommits(featureBranch, versionChangeCommitId)) {
            getLog().info("Feature branch contains merge commits. Removing of version change commit is not possible.");
            return false;
        }
        try {
            removeCommits(branchPoint, versionChangeCommitId, featureBranch);
            getLog().info("Version change commit in feature branch removed.");
        } catch (MojoFailureException ex) {
            setBreakpoint(Breakpoint.REBASE_WITHOUT_VERSION_CHANGE, featureBranch);
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
     * Store breakpoint type to branch local config.
     *
     * @param breakpoint
     *            the breakpoint type
     * @param branch
     *            the branch in which config should be stored
     */
    private void setBreakpoint(Breakpoint breakpoint, String branch) throws MojoFailureException, CommandLineException {
        setBreakpoint(breakpoint, branch, null);
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
    private void setBreakpoint(Breakpoint breakpoint, String branch, Map<String, String> additionalConfigs)
            throws MojoFailureException, CommandLineException {
        gitSetBranchLocalConfig(branch, "breakpoint", breakpoint.getId());
        if (additionalConfigs != null && additionalConfigs.size() > 0) {
            for (Entry<String, String> config : additionalConfigs.entrySet()) {
                gitSetBranchLocalConfig(branch, config.getKey(), config.getValue());
            }
        }
    }

    /**
     * Remove breakpoint information and known additional configs from branch
     * local config.
     *
     * @param featureBranch
     *            the feature branch name
     * @param baseBranch
     *            the name of the base branch
     */
    private void removeBreakpoints(String featureBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        gitRemoveBranchLocalConfig(featureBranch, "breakpoint");
        gitRemoveBranchLocalConfig(baseBranch, "breakpoint");
        gitRemoveBranchLocalConfig(baseBranch, "breakpointFeatureBranch");
    }

    /**
     * Breakpoint types.
     *
     * @author Volodymyr Medvid
     */
    private enum Breakpoint {
        CLEAN_INSTALL("cleanInstall"), FINAL_MERGE("finalMerge"), REBASE_BEFORE_FINISH(
                "rebaseBeforeFinish"), REBASE_WITHOUT_VERSION_CHANGE("rebaseWithoutVersionChange");

        private String id;

        private Breakpoint(String shortId) {
            id = "featureFinish." + shortId;
        }

        public static Breakpoint valueById(String anId) {
            for (Breakpoint breakpoint : values()) {
                if (breakpoint.getId().equals(anId)) {
                    return breakpoint;
                }
            }
            return null;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * Single process step for feature finish.
     *
     * @author Volodymyr Medvid
     */
    private class Step {

        private StepOperator executor;
        private Breakpoint breakpoint;

        /**
         * Creates a process step.
         *
         * @param anExecutor
         *            the method to be executed on this step
         */
        public Step(StepOperator anExecutor) {
            this(anExecutor, null);
        }

        /**
         * Creates a process step.
         *
         * @param anExecutor
         *            the method to be executed on this step
         * @param aBreakpoint
         *            the breakpoint type associated with this step. The process
         *            will be restarted from this step if previous run faild on
         *            passed break point.
         */
        public Step(StepOperator anExecutor, Breakpoint aBreakpoint) {
            executor = anExecutor;
            breakpoint = aBreakpoint;
        }

        /**
         * Execute the process step.
         */
        public StepParameters execute(StepParameters parameters) throws MojoFailureException, CommandLineException {
            return executor.execute(parameters);
        }
    }

    @FunctionalInterface
    private interface StepOperator {

        StepParameters execute(StepParameters parameters) throws MojoFailureException, CommandLineException;
    }

    private class StepParameters {
        private Breakpoint breakpoint;
        private String featureBranch;
        private String baseBranch;
        private Boolean isOnFeatureBranch;
    }

}
