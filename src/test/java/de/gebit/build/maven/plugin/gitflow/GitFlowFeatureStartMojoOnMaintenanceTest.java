//
// GitFlowFeatureStartMojoTest.java
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

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureStartMojoOnMaintenanceTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-start";

    private static final String FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME = FEATURE_ISSUE + "-someDescription";

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_ISSUE + ": updating versions for feature branch";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_RELEASE_VERSION = BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION;

    private static final String FEATURE_VERSION = MAINTENANCE_RELEASE_VERSION + "-" + FEATURE_ISSUE + "-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_MAINTENANCE_SET_VERSION = "NO-ISSUE: updating versions for maintenance branch";

    private static final String INTEGRATION_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String PROMPT_BRANCH_OF_LAST_INTEGRATED = "The current commit on " + MAINTENANCE_BRANCH
            + " is not integrated (probably not stable). Create a branch based of the last integrated commit ("
            + INTEGRATION_BRANCH + ")?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
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
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectlyOnMaintenance();
        assertArtifactNotInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    private void assertCentralBranchConfigSetCorrectly(final String expectedVersionChangeCommit)
            throws IOException, GitAPIException {
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(MAINTENANCE_FIRST_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(expectedVersionChangeCommit, branchConfig.getProperty("versionChangeCommit"));
    }

    private void assertFeatureStartedCorrectlyOnMaintenance()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    @Test
    public void testExecuteSkipFeatureVersion() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipFeatureVersion", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        assertCentralBranchConfigSetCorrectly(null);
    }

    @Test
    public void testExecuteWithLocalChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Local branch is ahead of the remote branch '" + MAINTENANCE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed or run feature start in "
                        + "interactive mode.",
                "'git push " + MAINTENANCE_BRANCH + "' to push local changes to remote branch",
                "'mvn flow:feature-start' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChanges() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Remote branch is ahead of the local branch '" + MAINTENANCE_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed or run feature start in "
                        + "interactive mode.",
                "'git pull' to pull changes into local branch",
                "'mvn flow:feature-start' to run in interactive mode");
        assertNoChanges();
    }

    private void assertNoChanges() throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories();
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    @Test
    public void testExecuteWithLocalChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Local branch is ahead of the remote branch '" + MAINTENANCE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed or run feature start in "
                        + "interactive mode.",
                "'git push " + MAINTENANCE_BRANCH + "' to push local changes to remote branch",
                "'mvn flow:feature-start' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
    }

    @Test
    public void testExecuteWithFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        git.fetch(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote branch is ahead of the local branch '" + MAINTENANCE_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed or run feature start in "
                        + "interactive mode.",
                "'git pull' to pull changes into local branch",
                "'mvn flow:feature-start' to run in interactive mode");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithIntegrationBranchSameAsMaintenanceBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranchInInteractiveMode() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMaintenanceBranchInInteractiveModeAnswerNo()
            throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);
        git.assertTestfileContent(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME)).thenReturn(
                FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteOnEpicBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.EPIC_ON_MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(BasicConstants.EPIC_ON_MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(MAINTENANCE_FIRST_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteOnEpicBranchWithoutEpicVersion() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(BasicConstants.EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(MAINTENANCE_FIRST_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

}
