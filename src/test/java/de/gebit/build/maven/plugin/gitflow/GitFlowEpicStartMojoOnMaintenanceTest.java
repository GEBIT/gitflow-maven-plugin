//
// GitFlowEpicStartMojoOnMaintenanceTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.io.ModelParseException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowEpicStartMojoOnMaintenanceTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-start";

    private static final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String EPIC_NAME = EPIC_ISSUE + "-someDescription";

    private static final String EPIC_BRANCH = "epic/" + EPIC_NAME;

    private static final String COMMIT_MESSAGE_SET_VERSION = EPIC_ISSUE + ": updating versions for epic branch";

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    private static final String EXPECTED_BRANCH_VERSION = "1.42.0-" + EPIC_ISSUE + "-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String COMMIT_MESSAGE_MAINTENANCE_SET_VERSION = "NO-ISSUE: updating versions for maintenance branch";

    private static final String INTEGRATION_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String PROMPT_EPIC_BRANCH_NAME = ExecutorHelper.EPIC_START_PROMPT_EPIC_BRANCH_NAME;

    private static final String PROMPT_BRANCH_OF_LAST_INTEGRATED = "The current commit on " + MAINTENANCE_BRANCH
            + " is not integrated. Create a branch of the last integrated commit (" + INTEGRATION_BRANCH + ")?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectlyOnMaintenance();
        assertArtifactNotInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    private void assertCentralBranchConfigSetCorrectly(final String expectedVersionChangeCommit)
            throws IOException, GitAPIException {
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(MAINTENANCE_FIRST_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("epicStartMessage"));
        assertEquals(expectedVersionChangeCommit, branchConfig.getProperty("versionChangeCommit"));
    }

    private void assertEpicStartedCorrectlyOnMaintenance()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    private void assertEpicStartedCorrectlyOnMaintenanceOffline()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    @Test
    public void testExecuteWithLocalChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Local branch is ahead of the remote branch '" + MAINTENANCE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + MAINTENANCE_BRANCH + "'");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChanges() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Remote branch is ahead of the local branch '" + MAINTENANCE_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed.", "'git pull'");
        assertNoChanges();
    }

    private void assertNoChanges() throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories();
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    @Test
    public void testExecuteWithLocalChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Local branch is ahead of the remote branch '" + MAINTENANCE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + MAINTENANCE_BRANCH + "'");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectlyOnMaintenanceOffline();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        git.fetch(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote branch is ahead of the local branch '" + MAINTENANCE_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed.", "'git pull'");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithIntegrationBranchSameAsMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranchInInteractiveMode() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranchInInteractiveModeAnswerNo()
            throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH, EPIC_BRANCH,
                CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileContent(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Integration branch '" + INTEGRATION_BRANCH + "' is ahead of base branch '" + MAINTENANCE_BRANCH
                        + "', this indicates a severe error condition on your branches.",
                " Please consult a gitflow expert on how to fix this!");
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, INTEGRATION_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

}
