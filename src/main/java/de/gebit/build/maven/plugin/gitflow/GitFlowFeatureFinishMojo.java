/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
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

import de.gebit.build.maven.plugin.gitflow.ExtendedPrompter.SelectOption;
import de.gebit.build.maven.plugin.gitflow.steps.Breakpoint;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureFinishBreakpoint;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureFinishStep;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureFinishStepParameters;
import de.gebit.build.maven.plugin.gitflow.steps.Step;
import de.gebit.build.maven.plugin.gitflow.steps.StepsUtil;

/**
 * Finish the implementation of the feature.
 * <p>
 * Finish the implementation of the feature by merging it in the
 * development/upstream branch. Removes the version commit that changed the
 * version (to reduce unnecessary commits in <code>pom.xml</code>), merges into
 * development branch (using fast forward strategy if <code>flow.rebase</code>
 * is <code>true</code>), pushes the development branch to remote and finally
 * deletes the feature branch.
 * <p>
 * Make sure your local development branch is not behind the remote, before
 * executing.
 * <p>
 * If <code>flow.rebase</code> is <code>true</code>, rebases the feature branch
 * on top of the development branch before finishing.
 * <p>
 * Example:
 *
 * <pre>
 * mvn flow:feature-finish -N [-Dflow.allowFF=true|false] [-Dflow.rebase=true|false] [-D...]
 * </pre>
 *
 * @see GitFlowFeatureStartMojo
 * @author Volodymyr Medvid
 */
@Mojo(name = GitFlowFeatureFinishMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureFinishMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-finish";

    /** Whether to keep feature branch after finish. */
    @Parameter(property = "flow.keepFeatureBranch", defaultValue = "false")
    private boolean keepFeatureBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "flow.skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;

    /**
     * You can try a rebase of the feature branch skipping the initial commit that
     * update the pom versions just before finishing a feature. The operation will
     * peform a rebase, which may not finish successfully. You can make your changes
     * and run feature-finish again in that case. <br>
     * Note: problems arise if you're modifying the poms near the version number.
     * You will need to fix those conflicts before running feature-finish again, as
     * otherwise the pom will be invalid and the process cannot be started. If you
     * cannot fix the pom into a working state with the current commit you can
     * manually issue a <code>git rebase --continue</code>.
     *
     * @since 1.3.0
     */
    @Parameter(property = "flow.rebaseWithoutVersionChange", defaultValue = "false")
    private boolean rebaseWithoutVersionChange = false;

    /**
     * Whether to allow fast forward merge of feature branch into development
     * branch.
     *
     * @since 2.0.0
     */
    @Parameter(property = "flow.allowFF", defaultValue = "false")
    private boolean allowFF = false;

    /**
     * Whether to rebase feature branch on top of development branch before merging
     * into it.
     *
     * @since 2.0.1
     */
    @Parameter(property = "flow.rebase", defaultValue = "false")
    private boolean rebase = false;

    /**
     * Whether a merge of development branch into feature branch should be performed
     * instead of a rebase on top of development branch before merging into it. Is
     * used only if parameter <code>rebase</code> is true.
     *
     * @since 2.0.1
     */
    @Parameter(property = "flow.updateWithMerge", defaultValue = "false")
    private boolean updateWithMerge = false;

    /**
     * Whether to squash a commit with correction of version for new modules and
     * single feature commit.
     *
     * @since 2.1.0
     */
    @Parameter(property = "flow.squashNewModuleVersionFixCommit", defaultValue = "false")
    private boolean squashNewModuleVersionFixCommit = false;

    private final List<Step<FeatureFinishBreakpoint, FeatureFinishStepParameters>> allProcessSteps = Arrays.asList(
            new FeatureFinishStep(this::selectFeatureAndBaseBranches),
            new FeatureFinishStep(this::ensureBranchesPreparedForFeatureFinish,
                    FeatureFinishBreakpoint.REBASE_BEFORE_FINISH),
            new FeatureFinishStep(this::verifyFeatureProject),
            new FeatureFinishStep(this::revertProjectVersion, FeatureFinishBreakpoint.REBASE_WITHOUT_VERSION_CHANGE),
            new FeatureFinishStep(this::mergeIntoBaseBranch, FeatureFinishBreakpoint.FINAL_MERGE),
            new FeatureFinishStep(this::buildBaseProject, FeatureFinishBreakpoint.CLEAN_INSTALL),
            new FeatureFinishStep(this::finalizeFeatureFinish));

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature finish process");
        checkCentralBranchConfig();
        StepsUtil.processSteps(allProcessSteps, this::getBreakpoint, this::initParameters);
        getMavenLog().info("Feature finish process finished");
    }

    /**
     * Check the breakpoint marker stored into branch local config.
     *
     * @return the type of marked breakpoint or <code>null</code> if no breakpoint
     *         found.
     */
    private FeatureFinishBreakpoint getBreakpoint() throws MojoFailureException, CommandLineException {
        String branch;
        try {
            branch = gitMergeFromFeatureBranchInProcess();
        } catch (MojoFailureException e) {
            branch = gitMergeIntoFeatureBranchInProcess();
        }
        if (branch == null) {
            branch = gitRebaseFeatureBranchInProcess();
            if (branch == null) {
                branch = gitCurrentBranch();
            }
        }
        String breakpointId = gitGetBranchLocalConfig(branch, "breakpoint");
        if (breakpointId != null) {
            return FeatureFinishBreakpoint.valueById(breakpointId);
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
    private FeatureFinishStepParameters initParameters(FeatureFinishBreakpoint breakpoint)
            throws MojoFailureException, CommandLineException {
        FeatureFinishStepParameters stepParameters = new FeatureFinishStepParameters();
        stepParameters.breakpoint = breakpoint;
        if (breakpoint != null) {
            switch (breakpoint) {
            case REBASE_BEFORE_FINISH:
                stepParameters.featureBranch = gitRebaseFeatureBranchInProcess();
                if (stepParameters.featureBranch == null) {
                    stepParameters.featureBranch = gitMergeIntoFeatureBranchInProcess();
                }
                stepParameters.baseBranch = gitFeatureBranchBaseBranch(stepParameters.featureBranch);
                break;
            case REBASE_WITHOUT_VERSION_CHANGE:
                stepParameters.featureBranch = gitRebaseFeatureBranchInProcess();
                stepParameters.baseBranch = gitFeatureBranchBaseBranch(stepParameters.featureBranch);
                break;
            case FINAL_MERGE:
                stepParameters.featureBranch = gitMergeFromFeatureBranchInProcess();
                stepParameters.baseBranch = gitFeatureBranchBaseBranch(stepParameters.featureBranch);
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

    private FeatureFinishStepParameters selectFeatureAndBaseBranches(FeatureFinishStepParameters stepParameters)
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
        getMavenLog().info("Base branch of feature branch is '" + baseBranch + "'");

        stepParameters.featureBranch = featureBranch;
        stepParameters.baseBranch = baseBranch;
        stepParameters.isOnFeatureBranch = isOnFeatureBranch;
        return stepParameters;
    }

    private FeatureFinishStepParameters ensureBranchesPreparedForFeatureFinish(
            FeatureFinishStepParameters stepParameters) throws MojoFailureException, CommandLineException {
        FeatureFinishStepParameters tempStepParameters = stepParameters;
        String featureBranch = tempStepParameters.featureBranch;
        if (stepParameters.breakpoint == FeatureFinishBreakpoint.REBASE_BEFORE_FINISH) {
            tempStepParameters = rebaseBeforeMerge(tempStepParameters);
        } else {
            String baseBranch = tempStepParameters.baseBranch;
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
                throw new GitFlowFailureException(
                        "There are no real changes in feature branch '" + featureBranch + "'.",
                        "Delete the feature branch or commit some changes first.",
                        "'mvn flow:feature-abort' to delete the feature branch",
                        "'git add' and 'git commit' to commit some changes into feature branch and "
                                + "'mvn flow:feature-finish' to run the feature finish again");
            }
            if (!gitIsAncestorBranch(baseBranch, featureBranch)) {
                if (rebase) {
                    tempStepParameters = rebaseBeforeMerge(tempStepParameters);
                } else {
                    List<SelectOption> options = Arrays.asList(
                            new SelectOption("r", null, "Rebase feature branch and continue feature finish process"),
                            new SelectOption("m", null,
                                    "(NOT RECOMMENDED) Continue feature finish process by trying "
                                            + "to merge feature branch into the base branch"),
                            new SelectOption("a", null, "Abort feature finish process"));
                    SelectOption selectedOption = getPrompter().promptToSelectOption(
                            "Base branch '" + baseBranch + "' has changes that are not yet included in feature branch '"
                                    + featureBranch + "'." + LS + "" + "You have following options:",
                            "Select how you want to continue:", options, "a",
                            new GitFlowFailureInfo(
                                    "Base branch '" + baseBranch
                                            + "' has changes that are not yet included in feature branch '"
                                            + featureBranch + "'.",
                                    "Rebase the feature branch first in order to proceed.",
                                    "'mvn flow:feature-rebase' to rebase the feature branch"));
                    if ("r".equalsIgnoreCase(selectedOption.getKey())) {
                        tempStepParameters = rebaseBeforeMerge(tempStepParameters);
                    } else if ("m".equalsIgnoreCase(selectedOption.getKey())) {
                        // NOP
                    } else {
                        throw new GitFlowFailureException("Feature finish aborted by user.", null);
                    }
                }
            }
        }

        if (tempStepParameters.isOnFeatureBranch == null) {
            String currentBranch = gitCurrentBranch();
            tempStepParameters.isOnFeatureBranch = (currentBranch.equals(featureBranch));
        }

        if (!tempStepParameters.isOnFeatureBranch) {
            getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
            gitCheckout(featureBranch);
        }

        return tempStepParameters;
    }

    private FeatureFinishStepParameters rebaseBeforeMerge(FeatureFinishStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String featureBranch = stepParameters.featureBranch;
        if (stepParameters.breakpoint != FeatureFinishBreakpoint.REBASE_BEFORE_FINISH) {

            String baseBranch = stepParameters.baseBranch;
            if (stepParameters.isOnFeatureBranch == null) {
                String currentBranch = gitCurrentBranch();
                stepParameters.isOnFeatureBranch = (currentBranch.equals(featureBranch));
            }
            if (!stepParameters.isOnFeatureBranch) {
                getMavenLog().info("Switching to feature branch '" + featureBranch + "'");
                gitCheckout(featureBranch);
            }
            rebaseFeatureBranchOnTopOfBaseBranch(featureBranch, baseBranch);
        } else {
            continueFeatureRebase();
        }
        boolean rebasedWithoutVersionChangeCommit = finalizeFeatureRebase(featureBranch);
        stepParameters.isOnFeatureBranch = true;
        stepParameters.rebasedWithoutVersionChangeCommit = rebasedWithoutVersionChangeCommit;
        return stepParameters;
    }

    private void rebaseFeatureBranchOnTopOfBaseBranch(String featureBranch, String baseBranch)
            throws CommandLineException, GitFlowFailureException, MojoFailureException {
        gitRemoveBranchLocalConfig(featureBranch, "rebasedWithoutVersionChangeCommit");
        if (updateWithMerge) {
            // merge development into feature
            try {
                getMavenLog().info("Merging (--no-ff) base branch '" + baseBranch + "' into feature branch '"
                        + featureBranch + "' before performing feature finish...");
                gitMerge(baseBranch, true);
            } catch (MojoFailureException ex) {
                getMavenLog().info("Feature finish process paused to resolve merge conflicts");
                gitSetBranchLocalConfig(featureBranch, "breakpoint",
                        FeatureFinishBreakpoint.REBASE_BEFORE_FINISH.getId());
                throw new GitFlowFailureException(ex,
                        "Automatic merge of base branch '" + baseBranch + "' into feature branch '" + featureBranch
                                + "' failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process",
                        "'mvn flow:feature-rebase-abort' to abort feature finish process");
            }
        } else {
            getMavenLog().info("Rebasing feature branch '" + featureBranch + "' on top of base branch '" + baseBranch
                    + "' before performing feature finish...");
            // rebase feature on top of development
            String baseCommit = gitBranchPoint(baseBranch, featureBranch);
            String versionChangeCommitOnBranch = gitVersionChangeCommitOnFeatureBranch(featureBranch, baseCommit);
            if (versionChangeCommitOnBranch != null) {
                gitSetBranchLocalConfig(featureBranch, "rebasedWithoutVersionChangeCommit", "true");
                try {
                    gitRebaseOnto(baseBranch, versionChangeCommitOnBranch, featureBranch);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
                    gitSetBranchLocalConfig(featureBranch, "breakpoint",
                            FeatureFinishBreakpoint.REBASE_BEFORE_FINISH.getId());
                    throw new GitFlowFailureException(ex,
                            "Automatic rebase of feature branch '" + featureBranch + "' on top of base branch '"
                                    + baseBranch + "' failed.\nGit error message:\n"
                                    + StringUtils.trim(ex.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-finish' again.\n"
                                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-finish' to continue feature finish process",
                            "'mvn flow:feature-rebase-abort' to abort feature finish process");
                }
            } else {
                try {
                    gitRebase(baseBranch);
                } catch (MojoFailureException ex) {
                    getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
                    gitSetBranchLocalConfig(featureBranch, "breakpoint",
                            FeatureFinishBreakpoint.REBASE_BEFORE_FINISH.getId());
                    throw new GitFlowFailureException(ex,
                            "Automatic rebase of feature branch '" + featureBranch + "' on top of base branch '"
                                    + baseBranch + "' failed.\nGit error message:\n"
                                    + StringUtils.trim(ex.getMessage()),
                            "Fix the rebase conflicts and mark them as resolved. After that, run "
                                    + "'mvn flow:feature-finish' again.\n"
                                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                    + "conflicts as resolved",
                            "'mvn flow:feature-finish' to continue feature finish process",
                            "'mvn flow:feature-rebase-abort' to abort feature finish process");
                }
            }
        }
    }

    private void continueFeatureRebase() throws GitFlowFailureException, CommandLineException {
        if (updateWithMerge) {
            // continue with commit
            if (!getPrompter().promptConfirmation(
                    "You have a merge in process on your current branch. If you run 'mvn flow:feature-finish' "
                            + "before and merge had conflicts you can continue. In other case it is better to "
                            + "clarify the reason of merge in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of feature finish aborted by user.", null);
            }
            getMavenLog().info("Continue merging base branch into feature branch before performing feature finish...");
            try {
                gitCommitMerge();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Feature finish process paused to resolve merge conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after merge of base branch into feature branch.\n"
                                + "Git error message:\n" + StringUtils.trim(exc.getMessage()),
                        "Fix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process",
                        "'mvn flow:feature-rebase-abort' to abort feature finish process");
            }
        } else {
            // continue with the rebase
            if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                    + "If you run 'mvn flow:feature-finish' before and rebase had conflicts you can "
                    + "continue. In other case it is better to clarify the reason of rebase in process. Continue?",
                    true, true)) {
                throw new GitFlowFailureException("Continuation of feature finish aborted by user.", null);
            }
            getMavenLog()
                    .info("Continue rebasing feature branch on top of base branch before performing feature finish...");
            try {
                gitRebaseContinueOrSkip();
            } catch (MojoFailureException exc) {
                getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
                throw new GitFlowFailureException(exc,
                        "There are unresolved conflicts after rebase of feature branch on top of base branch.\n"
                                + "Git error message:\n" + StringUtils.trim(exc.getMessage()),
                        "Fix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-finish' to continue feature finish process",
                        "'mvn flow:feature-rebase-abort' to abort feature finish process");
            }
        }
    }

    private boolean finalizeFeatureRebase(String featureBranch) throws MojoFailureException, CommandLineException {
        boolean rebasedWithoutVersionChangeCommit = gitGetBranchLocalConfig(featureBranch,
                "rebasedWithoutVersionChangeCommit") != null;
        if (rebasedWithoutVersionChangeCommit) {
            gitRemoveBranchLocalConfig(featureBranch, "rebasedWithoutVersionChangeCommit");
        }
        return rebasedWithoutVersionChangeCommit;
    }

    private FeatureFinishStepParameters verifyFeatureProject(FeatureFinishStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        if (!skipTestProject) {
            getMavenLog().info("Testing the feature project before performing feature finish...");
            mvnCleanVerify();
        }
        return stepParameters;
    }

    private FeatureFinishStepParameters revertProjectVersion(FeatureFinishStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint != FeatureFinishBreakpoint.REBASE_WITHOUT_VERSION_CHANGE) {
            if (stepParameters.rebasedWithoutVersionChangeCommit != null
                    && stepParameters.rebasedWithoutVersionChangeCommit == true) {
                getLog().info("Project version on feature branch already reverted while rebasing.");
                fixupModuleParents(featureBranch, stepParameters.baseBranch);
            } else {
                String baseBranch = stepParameters.baseBranch;
                String featureVersion = getCurrentProjectVersion();
                getLog().info("Project version on feature branch: " + featureVersion);

                gitCheckout(baseBranch);

                String baseVersion = getCurrentProjectVersion();
                getLog().info("Project version on base branch: " + baseVersion);

                boolean rebased = rebaseToRemoveVersionChangeCommit(featureBranch, baseBranch);
                if (rebased) {
                    fixupModuleParents(featureBranch, baseBranch);
                }
                if (!rebased && !tychoBuild) {
                    // rebase not configured or not possible, then manually
                    // revert the version
                    revertProjectVersionManually(featureBranch, featureVersion, baseVersion, baseBranch);
                }
            }
        } else {
            stepParameters.baseBranch = continueRebaseToRemoveVersionChangeCommit(featureBranch);
        }
        return stepParameters;
    }

    private void fixupModuleParents(String featureBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        getLog().info("Ensure consistent version in all modules");
        String baseVersion = getCurrentProjectVersion();
        String issueNumber = getFeatureIssueNumber(featureBranch);
        String featureFinishMessage = substituteWithIssueNumber(commitMessages.getFeatureFinishMessage(), issueNumber);
        boolean amend = false;
        if (squashNewModuleVersionFixCommit) {
            int featureCommits = gitGetDistanceToAncestor(featureBranch, baseBranch);
            amend = (featureCommits == 1);
        }
        mvnFixupVersions(baseVersion, issueNumber, featureFinishMessage, amend);
    }

    private FeatureFinishStepParameters mergeIntoBaseBranch(FeatureFinishStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint != FeatureFinishBreakpoint.FINAL_MERGE) {
            String baseBranch = stepParameters.baseBranch;
            // git checkout develop
            gitCheckout(baseBranch);
            // git merge --no-ff feature/...
            try {
                getMavenLog().info("Merging (" + (allowFF ? "--ff" : "--no-ff") + ") feature branch '" + featureBranch
                        + "' into base branch '" + baseBranch + "'...");
                gitMerge(featureBranch, !allowFF);
            } catch (MojoFailureException ex) {
                getMavenLog().info("Feature finish process paused to resolve merge conflicts");
                setBreakpoint(FeatureFinishBreakpoint.FINAL_MERGE, featureBranch);
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

    private FeatureFinishStepParameters buildBaseProject(FeatureFinishStepParameters stepParameters)
            throws CommandLineException, MojoFailureException, GitFlowFailureException {
        String baseBranch = stepParameters.baseBranch;
        String featureBranch = stepParameters.featureBranch;

        if (stepParameters.breakpoint == FeatureFinishBreakpoint.CLEAN_INSTALL) {
            getMavenLog().info("Restart after failed project installation on base branch detected");
            checkUncommittedChanges();
        }
        if (installProject) {
            getMavenLog().info("Installing the project on base branch '" + baseBranch + "'...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog()
                        .info("Feature finish process paused on failed project installation to fix project problems");
                Map<String, String> configs = new HashMap<>();
                configs.put("breakpointFeatureBranch", featureBranch);
                setBreakpoint(FeatureFinishBreakpoint.CLEAN_INSTALL, baseBranch, configs);
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        FailureInfoHelper.installProjectFailure(GOAL, baseBranch, "feature finish", reason));
            }
        }
        return stepParameters;
    }

    private FeatureFinishStepParameters finalizeFeatureFinish(FeatureFinishStepParameters stepParameters)
            throws MojoFailureException, CommandLineException {
        String baseBranch = stepParameters.baseBranch;
        String featureBranch = stepParameters.featureBranch;

        removeBreakpoints(featureBranch, baseBranch);
        // first push modified branches
        if (pushRemote) {
            getMavenLog().info("Pushing base branch '" + baseBranch + "' to remote repository");
            gitPush(baseBranch, false, false);
        }
        // then delete if wanted
        if (!keepFeatureBranch) {
            getMavenLog().info("Removing local feature branch '" + featureBranch + "'");
            gitBranchDeleteForce(featureBranch);

            if (pushRemote) {
                getMavenLog().info("Removing remote feature branch '" + featureBranch + "'");
                gitBranchDeleteRemote(featureBranch);
            }
            String featureName = featureBranch.substring(gitFlowConfig.getFeatureBranchPrefix().length());
            gitRemoveAllBranchCentralConfigsForBranch(featureBranch, "feature '" + featureName + "' finished");
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
                            "'git checkout INTERNAL' to switch to the feature branch",
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

    private void revertProjectVersionManually(String featureBranch, String featureVersion, String baseVersion,
            String baseBranch) throws MojoFailureException, CommandLineException {
        if (!featureVersion.equals(baseVersion)) {
            gitCheckout(featureBranch);
            String issueNumber = getFeatureIssueNumber(featureBranch);
            String featureFinishMessage = substituteWithIssueNumber(commitMessages.getFeatureFinishMessage(),
                    issueNumber);
            getMavenLog().info("Setting base version '" + baseVersion + "' for project on feature branch...");
            mvnSetVersions(baseVersion, GitFlowAction.FEATURE_FINISH, null, baseBranch);
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
        getMavenLog().info("Continue removing version change commit on feature branch using rebase...");
        try {
            gitRebaseContinueOrSkip();
        } catch (MojoFailureException exc) {
            getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
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
        getMavenLog().info("Continue merging feature branch into base branch...");
        try {
            gitCommitMerge();
        } catch (MojoFailureException exc) {
            getMavenLog().info("Feature finish process paused to resolve merge conflicts");
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
     * Merges the first commit on the given branch ignoring any changes. This first
     * commit is the commit that changed the versions.
     *
     * @param featureBranch
     *            The feature branch name.
     * @param branchPoint
     *            the branch point on both feature and development branch
     * @param versionChangeCommitId
     *            commit ID of the version change commit. Must be first commit on
     *            featuereBranch after branchPoint
     * @return true if the version has been premerged and does not need to be turned
     *         back
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
            getMavenLog().info("Removing version change commit on feature branch using rebase...");
            removeCommits(branchPoint, versionChangeCommitId, featureBranch);
            getLog().info("Version change commit in feature branch removed.");
        } catch (MojoFailureException ex) {
            getMavenLog().info("Feature finish process paused to resolve rebase conflicts");
            setBreakpoint(FeatureFinishBreakpoint.REBASE_WITHOUT_VERSION_CHANGE, featureBranch);
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
     * Remove breakpoint information and known additional configs from branch local
     * config.
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

}
