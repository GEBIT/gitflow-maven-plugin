//
// GitFlowReleaseAbortMojoTest.java
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

import java.util.Arrays;
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
public class GitFlowReleaseAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-abort";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String MAINTENANCE_VERSION = "2.0";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "2.0.0-SNAPSHOT";

    private static final String MAINTENANCE_RELEASE_VERSION = "2.0.0";

    private static final String PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION = "You have some uncommitted files. "
            + "If you continue any changes will be discarded. Continue?";

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
    public void testExecute() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranchPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
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
        assertGitFlowFailureException(result, "You have aborted release-abort process because of uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranchInBatchMode() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteNoReleaseBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "There are no release branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnMasterTwoReleaseBranches() throws Exception {
        final String RELEASE_BRANCH2 = RELEASE_PREFIX + "3.0.0";
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH2);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "More than one release branch exists. Cannot abort release from non-release branch.",
                "Please switch to a release branch first in order to proceed.",
                "'git checkout BRANCH' to switch to the release branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, RELEASE_BRANCH2);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnReleaseBranchTwoReleaseBranches() throws Exception {
        // set up
        final String RELEASE_BRANCH2 = RELEASE_PREFIX + "3.0.0";
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH2);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH2);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranchAndMissingDevelopmentBranchConfig() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.removeConfigValue(repositorySet, "branch", RELEASE_BRANCH, "development");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranchAndEmptyDevelopmentBranchConfig() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.setConfigValue(repositorySet, "branch", RELEASE_BRANCH, "development", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocally() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "No development branch found for current release branch. Cannot abort release.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'git config branch." + RELEASE_BRANCH
                        + ".development [development branch name]' to configure correct development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValue(repositorySet, "branch", RELEASE_BRANCH, "development", DEVELOPMENT_BRANCH);
    }

    @Test
    public void testExecuteMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteMaintenanceBranchMissingLocallyAndRemotelyAndOtherDevelopmentBranch() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION,
                userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, DEVELOPMENT_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteMaintenanceAndDevelopmentBranchesMissingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION,
                userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "No development branch found for current release branch. Cannot abort release.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'git config branch." + RELEASE_BRANCH
                        + ".development [development branch name]' to configure correct development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValue(repositorySet, "branch", RELEASE_BRANCH, "development", MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecutePushRemoteTrueAndPushReleaseBranchTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotely() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndPushRemoteTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "There are no release branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertConfigValueMissing(repositorySet, "branch", RELEASE_BRANCH, "development");
    }

}
