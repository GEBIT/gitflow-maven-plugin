//
// GitFlowFeatureFinishWithPreRebaseMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureFinishWithPreRebaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String TESTFILE_NAME = "testfile.txt";

    private static final String FEATURE_ISSUE = BasicConstants.EXISTING_FEATURE_ISSUE;

    private static final String FEATURE_NAME = BasicConstants.EXISTING_FEATURE_NAME;

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String FEATURE_WITHOUT_VERSION_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_FEATURE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + MASTER_BRANCH + " into " + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_REVERT_VERSION = FEATURE_ISSUE
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic rebase of feature branch ''{0}'' on top of base branch ''{1}'' failed.\n"
                    + "CONFLICT (added on {1} and on {0}): " + TESTFILE_NAME,
            "Fix the rebase conflicts and mark them as resolved by using 'git add'. "
                    + "After that, run 'mvn flow:feature-finish' again.\n"
                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:feature-finish' to continue feature finish process",
            "'mvn flow:feature-rebase-abort' to abort feature finish process");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge of base branch ''{0}'' into feature branch ''{1}'' failed.\n"
                    + "CONFLICT (added on {1} and on {0}): " + TESTFILE_NAME,
            "Fix the merge conflicts and mark them as resolved by using 'git add'. "
                    + "After that, run 'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'!",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:feature-finish' to continue feature finish process",
            "'mvn flow:feature-rebase-abort' to abort feature finish process");

    private RepositorySet repositorySet;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
        userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    private void executeMojo() throws Exception {
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
    }

    private MavenExecutionResult executeMojoWithResult() throws Exception {
        return executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
    }

    private void createFeatureBranchDivergentFromMaster(String featureBranch)
            throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, featureBranch);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void createFeatureBranchDivergentFromMasterWithConflicts(String featureBranch)
            throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, featureBranch);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void assertGitFlowFailureExceptionOnRebaseBeforeMerge(MavenExecutionResult result, String baseBranch,
            String featureBranch) {
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        MessageFormat.format(EXPECTED_REBASE_CONFLICT_MESSAGE.getProblem(), featureBranch, baseBranch),
                        EXPECTED_REBASE_CONFLICT_MESSAGE.getSolutionProposal(),
                        EXPECTED_REBASE_CONFLICT_MESSAGE.getStepsToContinue()));
    }

    private void assertGitFlowFailureExceptionOnUpdateBeforeMerge(MavenExecutionResult result, String baseBranch,
            String featureBranch) {
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        MessageFormat.format(EXPECTED_MERGE_CONFLICT_MESSAGE.getProblem(), baseBranch, featureBranch),
                        EXPECTED_MERGE_CONFLICT_MESSAGE.getSolutionProposal(),
                        EXPECTED_MERGE_CONFLICT_MESSAGE.getStepsToContinue()));
    }

    @Test
    public void testExecuteNoChangesOnMaster() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo();
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNoChangesOnFeatureExceptVersionChange() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in feature branch '" + FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch "
                        + "and 'mvn flow:feature-finish' to run the feature finish again");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithPreRebase() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster(FEATURE_BRANCH);
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithPreMerge() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster(FEATURE_BRANCH);
        userProperties.setProperty("flow.updateWithMerge", "true");
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MERGE_INTO_FEATURE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithPreRebaseFeatureWithoutVersion() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster(FEATURE_WITHOUT_VERSION_BRANCH);
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithRebaseConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteWithMergeConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        userProperties.setProperty("flow.updateWithMerge", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnUpdateBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteWithRebaseConflictOnPreRebaseFeatureWithoutVersion() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_WITHOUT_VERSION_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "There are unresolved conflicts after rebase of feature branch on top of base branch.\n"
                                + "CONFLICT (added on base branch and on " + FEATURE_BRANCH + "): " + TESTFILE_NAME,
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-finish' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:feature-finish' to continue feature finish process",
                        "'mvn flow:feature-rebase-abort' to abort feature finish process"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnUpdateBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "There are unresolved conflicts after merge of base branch into feature branch.\n"
                                + "CONFLICT (added on " + FEATURE_BRANCH + " and on base branch): " + TESTFILE_NAME,
                        "Fix the merge conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                                + "resolved",
                        "'mvn flow:feature-finish' to continue feature finish process",
                        "'mvn flow:feature-rebase-abort' to abort feature finish process"));
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictOnPreRebaseFeatureWithoutVersion() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_WITHOUT_VERSION_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, new GitFlowFailureInfo(
                "There are unresolved conflicts after rebase of feature branch on top of base branch.\n"
                        + "CONFLICT (added on base branch and on " + FEATURE_WITHOUT_VERSION_BRANCH + "): "
                        + TESTFILE_NAME,
                "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                        + "'mvn flow:feature-finish' again.\n"
                        + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                        + "conflicts as resolved",
                "'mvn flow:feature-finish' to continue feature finish process",
                "'mvn flow:feature-rebase-abort' to abort feature finish process"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnUpdateBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictOnPreRebaseFeatureWithoutVersionAndPromptAnswerNo()
            throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_WITHOUT_VERSION_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_BRANCH);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnUpdateBeforeMerge(result, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MERGE_INTO_FEATURE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictOnPreRebaseFeatureWithoutVersion() throws Exception {
        // set up
        createFeatureBranchDivergentFromMasterWithConflicts(FEATURE_WITHOUT_VERSION_BRANCH);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionOnRebaseBeforeMerge(result, MASTER_BRANCH, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, TESTFILE_NAME);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteWithPreRebaseOnMaster() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        createFeatureBranchDivergentFromMaster(USED_FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithDeletedModuleOnFeatureBranchAndNewVersionOnMaster_GBLD648() throws Exception {
        final String MODULE_TO_DELETE = "module1";
        final String COMMIT_MESSAGE_DELETE_MODULE = FEATURE_ISSUE + ": delete module";
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            ExecutorHelper.executeFeatureStart(this, otherRepositorySet, FEATURE_NAME);
            removeModule(otherRepositorySet, MODULE_TO_DELETE);
            git.commitAll(otherRepositorySet, COMMIT_MESSAGE_DELETE_MODULE);
            git.createAndCommitTestfile(otherRepositorySet);
            git.switchToBranch(otherRepositorySet, MASTER_BRANCH);
            setProjectVersion(otherRepositorySet, NEW_MASTER_VERSION);
            git.commitAll(otherRepositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
            git.push(otherRepositorySet);
            git.switchToBranch(otherRepositorySet, FEATURE_BRANCH);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertMissingLocalBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(otherRepositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, MASTER_BRANCH, COMMIT_MESSAGE_DELETE_MODULE,
                    COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_MASTER_VERSION_UPDATE, COMMIT_MESSAGE_FOR_TESTFILE);
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_MASTER_VERSION);

            assertFalse("module1 directory shouldn't exist",
                    new File(otherRepositorySet.getWorkingDirectory(), MODULE_TO_DELETE).exists());

            Model workingPom = readPom(otherRepositorySet.getWorkingDirectory());
            List<String> modules = workingPom.getModules();
            assertEquals(1, modules.size());
            assertEquals("module2", modules.get(0));

            git.assertRemoteFileMissing(otherRepositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        }
    }

}
