//
// GitFlowEpicAbortMojoTest.java
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
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowEpicAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-abort";

    private static final String EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;

    private static final String EPIC_VERSION = BasicConstants.EXISTING_EPIC_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. "
            + BasicConstants.SINGLE_EPIC_BRANCH + LS + "Choose epic branch to abort";

    private static final String PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION = "You have some uncommitted files. "
            + "If you continue any changes will be discarded. Continue?";

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, EPIC_BRANCH);
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
    public void testExecuteOnEpicBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicAbortedCorrectly();
    }

    private void assertEpicAbortedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnEpicBranch() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicAbortedCorrectly();
        git.assertTestfileMissing(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnEpicBranchConfirmationNo() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted epic-abort process because of uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnEpicBranchInBatchMode() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed or run in interactive mode.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes",
                "'mvn flow:epic-abort' to run in interactive mode");
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnBaseBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnOtherBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnEpicBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicAbortedCorrectly();
        git.assertTestfileMissing(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnEpicBranchInBatchMode() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed or run in interactive mode.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes",
                "'mvn flow:epic-abort' to run in interactive mode");
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnBaseBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnOtherBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoEpicBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", "no-epics/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "There are no epic branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnEpicBranchTwoEpicBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchTwoEpicBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + BasicConstants.FIRST_EPIC_BRANCH + LS + "2. "
                + BasicConstants.SECOND_EPIC_BRANCH + LS + "Choose epic branch to abort";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchTwoEpicBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + BasicConstants.FIRST_EPIC_BRANCH + LS + "2. "
                + BasicConstants.SECOND_EPIC_BRANCH + LS + "Choose epic branch to abort";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteWithBatchModeOnEpicBranch() throws Exception {
        // set up
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertEpicAbortedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:epic-abort' can be executed only on an epic branch.",
                "Please switch to an epic branch first or run in interactive mode.",
                "'git checkout INTERNAL' to switch to the epic branch",
                "'mvn flow:epic-abort' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to abort";
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteBaseBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranchStartedRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to abort";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH, MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicAbortedCorrectly();
        git.assertExistingLocalBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for epic branch '" + EPIC_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for epic branch '" + USED_EPIC_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWhileRebaseInProcess() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        RebaseResult rebaseResult = repositorySet.getLocalRepoGit().rebase().setUpstream(MASTER_BRANCH).call();
        assertEquals("an exception expected because of rebase conflict", Status.STOPPED, rebaseResult.getStatus());
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "A rebase of the current branch is in process. Cannot abort epic now.",
                "Finish rebase process first in order to proceed.");
    }

    @Test
    public void testExecuteWhileMergeInProcess() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of merge conflict", result.hasExceptions());
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "A merge into the current branch is in process. Cannot abort epic now.",
                "Finish merge process first in order to proceed.");
    }

    @Test
    public void testExecutePushRemoteTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicAbortedCorrectly();
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

}
