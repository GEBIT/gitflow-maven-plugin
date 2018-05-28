//
// GitFlowIntegratedMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowIntegratedMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "integrated";

    private static final String INTEGRATION_BRANCH_PREFIX = "integration/";

    private static final String INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + MASTER_BRANCH;

    private static final String PROMPT_INTEGRATION_BRANCH_NAME = "What is the integration branch name?";

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
    public void testExecuteWithDefaultIntegrationBranchForMaster() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteWithDefaultIntegrationBranchForOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String OTHER_INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + OTHER_BRANCH;
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_INTEGRATION_BRANCH_NAME, OTHER_INTEGRATION_BRANCH)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INTEGRATION_BRANCH_NAME, OTHER_INTEGRATION_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, OTHER_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, OTHER_BRANCH, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, OTHER_INTEGRATION_BRANCH, OTHER_INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, OTHER_BRANCH, OTHER_INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteWithIntegrationBranchFromPrompt() throws Exception {
        // set up
        final String SOME_INTEGRATION_BRANCH = "int/some-integration";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH))
                .thenReturn(SOME_INTEGRATION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOME_INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOME_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, SOME_INTEGRATION_BRANCH, SOME_INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, SOME_INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteWithIntegrationBranchFromProperty() throws Exception {
        // set up
        final String SOME_INTEGRATION_BRANCH = "int/some-integration";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", SOME_INTEGRATION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOME_INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOME_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, SOME_INTEGRATION_BRANCH, SOME_INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, SOME_INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteWithIntegrationBranchFromPropertyInBatchMode() throws Exception {
        // set up
        final String SOME_INTEGRATION_BRANCH = "int/some-integration";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", SOME_INTEGRATION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOME_INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOME_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, SOME_INTEGRATION_BRANCH, SOME_INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, SOME_INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteInBatchMode() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteWithPushRemoteFalse() throws Exception {
        // set up
        final String SOME_INTEGRATION_BRANCH = "int/some-integration";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", SOME_INTEGRATION_BRANCH);
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOME_INTEGRATION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOME_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, SOME_INTEGRATION_BRANCH, MASTER_BRANCH);
    }

    @Test
    public void testExecuteIntegrationBranchAlreadyExists() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        git.createAndCommitTestfile(repositorySet, "testfile2.txt", "Unit test dummy file 2 commit");
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteMissingRemoteBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Current branch '" + OTHER_BRANCH + "' doesn't exist remotely. Pushing of the integration "
                        + "branch will create an inconsistent state in remote repository.",
                "Push the current branch '" + OTHER_BRANCH + "' first or set 'pushRemote' parameter to "
                        + "false in order to avoid inconsistent state in remote repository.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
    }

    @Test
    public void testExecuteMissingRemoteBranchAndPushRemoteFalse() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String OTHER_INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + OTHER_BRANCH;
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", OTHER_INTEGRATION_BRANCH);
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_INTEGRATION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, OTHER_INTEGRATION_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_INTEGRATION_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteCurrentLocalAheadOfRemote() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Current local branch '" + OTHER_BRANCH + "' is ahead of remote branch. Pushing of the integration "
                        + "branch will create an inconsistent state in remote repository.",
                "Push the current branch '" + OTHER_BRANCH + "' first or set 'pushRemote' parameter to false in "
                        + "order to avoid inconsistent state in remote repository.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
    }

    @Test
    public void testExecuteCurrentLocalAheadOfRemoteAndPushRemoteFalse() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String OTHER_INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + OTHER_BRANCH;
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", OTHER_INTEGRATION_BRANCH);
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_INTEGRATION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, OTHER_INTEGRATION_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_INTEGRATION_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteRemoteAheadOfCurrentLocal() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String OTHER_INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + OTHER_BRANCH;
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, OTHER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", OTHER_INTEGRATION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_INTEGRATION_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, OTHER_INTEGRATION_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, OTHER_BRANCH, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, OTHER_INTEGRATION_BRANCH, OTHER_INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, OTHER_BRANCH, OTHER_INTEGRATION_BRANCH);
    }

    @Test
    public void testExecuteCurrentLocalAndRemoteBranchesDiverge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", "LOCAL: Unit test dummy file commit");
        git.remoteCreateTestfileInBranch(repositorySet, OTHER_BRANCH, "remote-testfile.txt",
                "REMOTE: Unit test dummy file commit");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Current local and remote branches '" + OTHER_BRANCH + "' diverge. Pushing of the integration "
                        + "branch will create an inconsistent state in remote repository.",
                "Rebase the changes in local branch '" + OTHER_BRANCH + "' first or set 'pushRemote' parameter to "
                        + "false in order to avoid inconsistent state in remote repository.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
    }

    @Test
    public void testExecuteCurrentLocalAndRemoteBranchesDivergeRemoteFalse() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String OTHER_INTEGRATION_BRANCH = INTEGRATION_BRANCH_PREFIX + OTHER_BRANCH;
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, OTHER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        Properties userProperties = new Properties();
        userProperties.setProperty("integrationBranch", OTHER_INTEGRATION_BRANCH);
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_INTEGRATION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, OTHER_INTEGRATION_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, COMMIT_MESSAGE_LOCAL);
        git.assertCommitsInRemoteBranch(repositorySet, OTHER_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_INTEGRATION_BRANCH, COMMIT_MESSAGE_LOCAL);
    }

}
