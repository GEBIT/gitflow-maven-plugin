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

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureRebaseAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase-abort";

    private static final String PROJECT_VERSION = TestProjects.WITH_UPSTREAM.version;

    private static final String FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME = FEATURE_ISSUE + "-someDescription";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String TMP_FEATURE_BRANCH = "tmp-" + FEATURE_BRANCH;

    private static final String FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_ISSUE + "-SNAPSHOT";

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_ISSUE + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String PROMPT_REBASE_ABORT = "You have a rebase in process on your current branch. "
            + "Are you sure you want to abort the feature rebase process?";

    private static final String PROMPT_MERGE_ABORT = "You have a merge in process on your current branch. "
            + "Are you sure you want to abort the feature rebase process?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
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
        return createDivergentFeatureAndMasterWithConflicts(null);
    }

    private String createDivergentFeatureAndMasterWithConflicts(Properties userProperties)
            throws Exception, IOException, GitAPIException {
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userProperties);
        String versionChangeCommit = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH, TMP_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH, TMP_FEATURE_BRANCH);

        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
    }

    @Test
    public void testExecuteReabseInProcess() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipFeatureVersion", "true");
        createDivergentFeatureAndMasterWithConflicts(userProperties);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), PROJECT_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");

    }

    @Test
    public void testExecuteReabseInProcessAndPromptAnswerNo() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipFeatureVersion", "true");
        createDivergentFeatureAndMasterWithConflicts(userProperties);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting feature rebase process aborted by user.", null);

        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "newVersionChangeCommit");
    }

}
