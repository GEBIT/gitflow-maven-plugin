//
// GitFlowFeatureRebaseAbortMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureRebaseAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase-abort";

    private static final String PROJECT_VERSION = TestProjects.BASIC.version;

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String TMP_FEATURE_BRANCH = "tmp-" + FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String PROMPT_REBASE_ABORT = "You have a rebase in process on your current branch. "
            + "Are you sure you want to abort the feature rebase process?";

    private static final String PROMPT_MERGE_ABORT = "You have a merge in process on your current branch. "
            + "Are you sure you want to abort the feature rebase process?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
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
    public void testExecuteNoReabseAndMergeInProcess() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "No rebase of feature branch or merge into feature branch detected. Nothing to abort.", null);
    }

    @Test
    public void testExecuteReabseOntoInProcess() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts();
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");

    }

    private String createDivergentFeatureAndMasterWithConflicts() throws Exception, IOException, GitAPIException {
        return createDivergentFeatureAndMasterWithConflicts(FEATURE_BRANCH);
    }

    private String createDivergentFeatureAndMasterWithConflicts(String featureBranch)
            throws Exception, IOException, GitAPIException {
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, featureBranch);
        String versionChangeCommit = git.currentCommit(repositorySet);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        return versionChangeCommit;
    }

    @Test
    public void testExecuteReabseOntoInProcessAndPromptAnswerNo() throws Exception {
        // set up
        createDivergentFeatureAndMasterWithConflicts();
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH, TMP_FEATURE_BRANCH);

        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
    }

    @Test
    public void testExecuteReabseInProcess() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_TMP_FEATURE_BRANCH = "tmp-" + USED_FEATURE_BRANCH;
        createDivergentFeatureAndMasterWithConflicts(USED_FEATURE_BRANCH);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), PROJECT_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");

    }

    @Test
    public void testExecuteReabseInProcessAndPromptAnswerNo() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_TMP_FEATURE_BRANCH = "tmp-" + USED_FEATURE_BRANCH;
        createDivergentFeatureAndMasterWithConflicts(USED_FEATURE_BRANCH);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_FEATURE_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");
    }

    @Test
    public void testExecuteMergeInProcess() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");

    }

    @Test
    public void testExecuteMergeInProcessAndPromptAnswerNo() throws Exception {
        // set up
        createDivergentFeatureAndMasterWithConflicts();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
    }

}
