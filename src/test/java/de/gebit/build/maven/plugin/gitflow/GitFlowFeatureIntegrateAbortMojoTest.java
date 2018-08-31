//
// GitFlowFeatureIntegrateAbortMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;
import de.gebit.build.maven.plugin.gitflow.steps.FeatureIntegrateBreakpoint;

/**
 *
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureIntegrateAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-integrate-abort";

    private static final String FEATURE_BRANCH_PREFIX = BasicConstants.TWO_FEATURE_BRANCHES_PREFIX;
    private static final String SOURCE_FEATURE_BRANCH = BasicConstants.FIRST_FEATURE_BRANCH;
    private static final String TARGET_FEATURE_NAME = BasicConstants.SECOND_FEATURE_NAME;
    private static final String TARGET_FEATURE_BRANCH = BasicConstants.SECOND_FEATURE_BRANCH;
    private static final String TMP_SOURCE_FEATURE_BRANCH = "tmp-" + SOURCE_FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SOURCE_TESTFILE = "SOURCE: Unit test dummy file commit";
    private static final String COMMIT_MESSAGE_TARGET_TESTFILE = "TARGET: Unit test dummy file commit";

    private static final String PROMPT_REBASE_ABORT = "You have a rebase in process on your current branch. "
            + "Are you sure you want to abort the integration of feature branch '" + SOURCE_FEATURE_BRANCH
            + "' into feature branch '" + TARGET_FEATURE_BRANCH + "'?";

    private RepositorySet repositorySet;
    private Properties userProperties = new Properties();

    @Before
    public void setUp() throws Exception {
        userProperties.setProperty("flow.featureBranchPrefix", FEATURE_BRANCH_PREFIX);
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, SOURCE_FEATURE_BRANCH);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecuteWithCommandLineException() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecuteReabseInProcess() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = ExecutorHelper.executeFeatureIntegrateWithResult(this, repositorySet,
                userProperties);
        assertGitFlowFailureExceptionRegEx(result, new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                        + TARGET_FEATURE_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-integrate' again.\n"
                        + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                        + "conflicts as resolved\\E",
                "\\Q'mvn flow:feature-integrate' to continue feature integration process\\E",
                "\\Q'mvn flow:feature-integrate-abort' to abort feature integration process\\E"));

        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TARGET_FEATURE_BRANCH,
                TMP_SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.rebase");
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "targetFeatureBranch",
                TARGET_FEATURE_BRANCH);
        userProperties.remove("featureName");
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_SOURCE_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, SOURCE_FEATURE_BRANCH, SOURCE_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, SOURCE_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                BasicConstants.FIRST_FEATURE_VERSION_COMMIT_MESSAGE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);

    }

    @Test
    public void testExecuteReabseInProcessAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = ExecutorHelper.executeFeatureIntegrateWithResult(this, repositorySet,
                userProperties);
        assertGitFlowFailureExceptionRegEx(result, new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                        + TARGET_FEATURE_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-integrate' again.\n"
                        + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                        + "conflicts as resolved\\E",
                "\\Q'mvn flow:feature-integrate' to continue feature integration process\\E",
                "\\Q'mvn flow:feature-integrate-abort' to abort feature integration process\\E"));

        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TARGET_FEATURE_BRANCH,
                TMP_SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.rebase");
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "targetFeatureBranch",
                TARGET_FEATURE_BRANCH);
        userProperties.remove("featureName");
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature integrate process aborted by user.", null);
        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TARGET_FEATURE_BRANCH,
                TMP_SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.rebase");
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "targetFeatureBranch",
                TARGET_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteNoRebaseInProcess() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "No rebase in progress detected. Nothing to abort.", null);
    }

    @Test
    public void testExecuteOtherRebaseInProcess() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.rebase(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Rebasing branch is not a temporary feature branch created during feature integrate process: "
                        + SOURCE_FEATURE_BRANCH,
                null);
    }

    @Test
    public void testExecuteRebaseInProcessWithoutBreakpoint() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.createBranch(repositorySet, TMP_SOURCE_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.rebase(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "No rebase breakpoint found.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteRebaseInProcessWithoutSourceBranchInfo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.createBranch(repositorySet, TMP_SOURCE_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.rebase(repositorySet, MASTER_BRANCH);
        git.setConfigValue(repositorySet, "branch", TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                FeatureIntegrateBreakpoint.REBASE.getId());
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "No info about source feature branch found in local branch config. "
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

}
