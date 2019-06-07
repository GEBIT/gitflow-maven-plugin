//
// GitFlowFeatureRebaseAbortMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
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
import de.gebit.xmlxpath.XML;

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

    private static final String PROMPT_INSTALL_PROJECT_ABORT = "You have an interrupted feature rebase process on your "
            + "current branch because project installation failed after rebase.\n"
            + "Are you sure you want to abort the feature rebase process and rollback the rebase?";

    private static final String PROMPT_TEST_PROJECT_ABORT = "You have an interrupted feature finish process on your "
            + "current branch because project test failed before merge.\n"
            + "Are you sure you want to abort the feature finish process and rollback the rebase?";

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
        assertGitFlowFailureException(result, "No interrupted feature rebase process detected. Nothing to abort.",
                null);
    }

    private void assertMissingAllLocalConfigValuesForBranch(String branch) {
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldFeatureVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "rebasedBeforeFinish");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldVersionChangeCommit");
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
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

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
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
        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "oldFeatureVersion");
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

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(USED_FEATURE_BRANCH);
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
        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "oldFeatureVersion");
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
        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "oldFeatureVersion");
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
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

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
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
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
    }

    @Test
    public void testExecuteReabseInProcessOnFeatureFinish() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts(FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
    }

    @Test
    public void testExecuteReabseInProcessOnFeatureFinishAndPromptAnswerNo() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts(FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteReabseInProcessOnFeatureFinishWithoutFeatureVersion() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        createDivergentFeatureAndMasterWithConflicts(USED_FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), PROJECT_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(USED_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteReabseInProcessOnFeatureFinishWithoutFeatureVersionAndPromptAnswerNo() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        createDivergentFeatureAndMasterWithConflicts(USED_FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueExists(repositorySet, USED_FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteMergeInProcessOnFeatureFinish() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts(FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
    }

    @Test
    public void testExecuteMergeInProcessOnFeatureFinishAndPromptAnswerNo() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentFeatureAndMasterWithConflicts(FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebase", "true");
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteAfterInstallProjectFailed() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "2.0.0-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "Invalid java file and new version";
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, "feature-rebase", FEATURE_BRANCH, "feature rebase");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_MASTER,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
    }

    private void replaceProjectVersion(String newVersion) throws IOException {
        File pom = new File(repositorySet.getWorkingDirectory(), "pom.xml");
        XML pomXML = XML.load(pom);
        pomXML.setValue("/project/version", newVersion);
        pomXML.setValue("/project/properties/version.build", newVersion);
        pomXML.store();
    }

    @Test
    public void testExecuteOnNonFeatureBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "No interrupted feature rebase process detected. Nothing to abort.",
                null);
    }

    @Test
    public void testExecuteAfterInstallProjectFailedAndPushedManually() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "2.0.0-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "Invalid java file and new version";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, "feature-rebase", FEATURE_BRANCH, "feature rebase");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_MASTER,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
        git.push(repositorySet, true);
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "The state of current local and remote branches is unexpected for an interrupted feature rebase "
                        + "process.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteAfterInstallProjectFailedAndPromptAnswerNo() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "2.0.0-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "Invalid java file and new version";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, "feature-rebase", FEATURE_BRANCH, "feature rebase");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_MASTER,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
        git.push(repositorySet, true);
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);
    }

    @Test
    public void testExecuteFailureOnTestProjectOnFeatureFinish_GBLD710() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_TESTFILE_MASTER = "MASTER: test file";
        final String TESTFILE_MASTER = "testfile_master.txt";
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_MASTER, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = ExecutorHelper.executeFeatureFinishWithResult(this, repositorySet,
                userProperties);
        assertTestProjectFailureException(result, GitFlowFeatureFinishMojo.GOAL, FEATURE_BRANCH, "feature finish");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "oldFeatureVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureHEAD");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedBeforeFinish", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.testProjectAfterRebase");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        when(promptControllerMock.prompt(PROMPT_TEST_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_TEST_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(FEATURE_BRANCH);
    }

}
