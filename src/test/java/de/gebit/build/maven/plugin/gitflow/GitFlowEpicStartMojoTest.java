//
// GitFlowEpicStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
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
public class GitFlowEpicStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-start";

    private static final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String EPIC_NAME = EPIC_ISSUE + "-someDescription";

    private static final String EPIC_BRANCH = "epic/" + EPIC_NAME;

    private static final String EPIC_BRANCH_VERSION = TestProjects.BASIC.releaseVersion + "-" + EPIC_ISSUE
            + "-SNAPSHOT";

    private static final String INTEGRATION_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = EPIC_ISSUE + ": updating versions for epic branch";

    private static final String PROMPT_EPIC_BRANCH_NAME = ExecutorHelper.EPIC_START_PROMPT_EPIC_BRANCH_NAME;

    private static final String PROMPT_BRANCH_OF_LAST_INTEGRATED = "The current commit on " + MASTER_BRANCH
            + " is not integrated (probably not stable). Create a branch based of the last integrated commit ("
            + INTEGRATION_BRANCH + ")?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC);
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
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectly();
        assertArtifactNotInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    private void assertCentralBranchConfigSetCorrectly(final String expectedVersionChangeCommit)
            throws IOException, GitAPIException {
        assertCentralBranchConfigSetCorrectly(expectedVersionChangeCommit, EPIC_ISSUE, EPIC_BRANCH,
                COMMIT_MESSAGE_SET_VERSION);
    }

    private void assertCentralBranchConfigSetCorrectly(final String expectedVersionChangeCommit, String epicIssue,
            String epicBranch, String commitMessageSetVersion) throws IOException, GitAPIException {
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, epicBranch);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(epicIssue, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(commitMessageSetVersion, branchConfig.getProperty("startCommitMessage"));
        assertEquals(expectedVersionChangeCommit, branchConfig.getProperty("versionChangeCommit"));

        assertEquals(null, branchConfig.getProperty("JOB_BUILD"));
    }

    private void assertEpicStartedCorrectly()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    private void assertEpicStartedCorrectlyOffline()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteTychoBuild() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH);

        assertCentralBranchConfigSetCorrectly(null);
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
        repositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir);
        try {

            // set up
            when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.INVALID_VERSION.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
            git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
            git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH);

            Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
            assertEquals("epic", branchConfig.getProperty("branchType"));
            assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
            assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
            assertEquals(TestProjects.INVALID_VERSION.version, branchConfig.getProperty("baseVersion"));
            assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
            assertEquals(null, branchConfig.getProperty("versionChangeCommit"));
        } finally {
            if (repositorySet != null) {
                repositorySet.close();
            }
        }
    }

    @Test
    public void testExecuteWithUntrackedChanges() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        git.createTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        git.assertUntrackedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositories();

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositoriesExceptCommitedTestfile();

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithLocalChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + MASTER_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git push " + MASTER_BRANCH + "' to push local changes to remote branch",
                "'mvn flow:epic-start' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChanges() throws Exception {
        // set up
        git.remoteCreateTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "Remote branch is ahead of the local branch '" + MASTER_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git pull' to pull changes into local branch",
                "'mvn flow:epic-start' to run in interactive mode");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithLocalAndRemoteChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote_testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "Local and remote branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase or merge the changes in local branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git pull' to merge changes in local branch",
                "'mvn flow:epic-start' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
    }

    private void assertNoChanges() throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories();
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + MASTER_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git push " + MASTER_BRANCH + "' to push local changes to remote branch",
                "'mvn flow:epic-start' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        git.remoteCreateTestfile(repositorySet);
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

        assertEpicStartedCorrectlyOffline();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.remoteCreateTestfile(repositorySet);
        git.fetch(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Remote branch is ahead of the local branch '" + MASTER_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git pull' to pull changes into local branch",
                "'mvn flow:epic-start' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithLocalAndFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote_testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.fetch(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Local and remote branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase or merge the changes in local branch in order to proceed or run epic start in "
                        + "interactive mode.",
                "'git pull' to merge changes in local branch",
                "'mvn flow:epic-start' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithEpicName() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertEpicStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithoutEpicNameInBatchMode() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Property 'epicName' is required in non-interactive mode but was not set.",
                "Specify a epicName or run in interactive mode.", "'mvn flow:epic-start -DepicName=XXX -B'",
                "'mvn flow:epic-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithInvalidEpicNameInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", "Invalid-epic-name");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The epic name 'Invalid-epic-name' is invalid. "
                        + "A epic name must start with the project's JIRA issue key, e.g. "
                        + TestProjects.BASIC.jiraProject + "-[number][-optional-short-description]",
                "Specify correct value for parameter 'epicName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithCustomEpicNamePatternDescriptionAndInvalidEpicNameInBatchMode() throws Exception {
        // set up
        final String EPIC_NAME_PATTERN_DESCRIPTION = "Test epic name pattern description";
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", "Invalid-epic-name");
        userProperties.setProperty("flow.epicNamePatternDescription", EPIC_NAME_PATTERN_DESCRIPTION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The epic name 'Invalid-epic-name' is invalid. " + EPIC_NAME_PATTERN_DESCRIPTION,
                "Specify correct value for parameter 'epicName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithDefaultEpicNamePatternDescriptionAndInvalidEpicNameInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", "Invalid-epic-name");
        userProperties.setProperty("flow.epicNamePatternDescription", "");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The epic name 'Invalid-epic-name' is invalid. " + "It does not match the required pattern: ^((?:"
                        + TestProjects.BASIC.jiraProject + ")-\\d+)(?:-[^\\s]*)?$",
                "Specify correct value for parameter 'epicName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithInvalidEpicNameInInteractivMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", "Invalid-epic-name");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock).showMessage("The epic name 'Invalid-epic-name' is invalid."
                + " A epic name must start with the project's JIRA issue key, e.g. " + TestProjects.BASIC.jiraProject
                + "-[number][-optional-short-description]");
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithInvalidEpicNameFromPrompterInInteractivMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn("Invalid-epic-name", "Invalid-epic-name",
                EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock, times(2)).showMessage("The epic name 'Invalid-epic-name' is invalid."
                + " A epic name must start with the project's JIRA issue key, e.g. " + TestProjects.BASIC.jiraProject
                + "-[number][-optional-short-description]");
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithCustomEpicNamePatternDescriptionAndInvalidEpicName() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn("Invalid-epic-name", "Invalid-epic-name",
                EPIC_NAME);
        Properties userProperties = new Properties();
        final String EPIC_NAME_PATTERN_DESCRIPTION = "Test epic name pattern description";
        userProperties.setProperty("flow.epicNamePatternDescription", EPIC_NAME_PATTERN_DESCRIPTION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock, times(2))
                .showMessage("The epic name 'Invalid-epic-name' is invalid. " + EPIC_NAME_PATTERN_DESCRIPTION);
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithDefaultEpicNamePatternDescriptionAndInvalidEpicName() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn("Invalid-epic-name", "Invalid-epic-name",
                EPIC_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicNamePatternDescription", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(PROMPT_EPIC_BRANCH_NAME);
        verify(promptControllerMock, times(2)).showMessage(
                "The epic name 'Invalid-epic-name' is invalid. It does not match the required pattern: ^((?:"
                        + TestProjects.BASIC.jiraProject + ")-\\d+)(?:-[^\\s]*)?$");
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithoutEpicNamePattern() throws Exception {
        // set up
        final String EPIC_START_MESSAGE = "updating versions for epic branch";
        final String INVALID_EPIC_NAME = "Invalid-epic-name";
        final String EXPECTED_EPIC_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-" + INVALID_EPIC_NAME
                + "-SNAPSHOT";
        final String EXPECTED_EPIC_BRANCH = "epic/" + INVALID_EPIC_NAME;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicNamePattern", "");
        userProperties.setProperty("flow.epicStartMessage", EPIC_START_MESSAGE);
        userProperties.setProperty("epicName", INVALID_EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_EPIC_VERSION_NUMBER);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_EPIC_BRANCH, EPIC_START_MESSAGE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(INVALID_EPIC_NAME, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(EPIC_START_MESSAGE, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithEpicNamePatternWithGroups() throws Exception {
        // set up
        final String EXPECTED_EPIC_NAME = EPIC_NAME + "-test";
        final String EXPECTED_EPIC_BRANCH = "epic/" + EXPECTED_EPIC_NAME;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicNamePattern", "^(${project.jira}-\\d+)(?:-[^\\s]*)?$");
        userProperties.setProperty("epicName", EXPECTED_EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithEpicNamePatternWithoutGroups() throws Exception {
        // set up
        final String EPIC_START_MESSAGE = "updating versions for epic branch";
        final String EXPECTED_EPIC_NAME = EPIC_NAME + "-test";
        final String EXPECTED_EPIC_BRANCH = "epic/" + EXPECTED_EPIC_NAME;
        final String EXPECTED_EPIC_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-" + EXPECTED_EPIC_NAME
                + "-SNAPSHOT";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicNamePattern", "^${project.jira}-\\d+-.+$");
        userProperties.setProperty("flow.epicStartMessage", EPIC_START_MESSAGE);
        userProperties.setProperty("epicName", EXPECTED_EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_EPIC_VERSION_NUMBER);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EXPECTED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_EPIC_BRANCH, EPIC_START_MESSAGE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_EPIC_NAME, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(EPIC_START_MESSAGE, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteEpicBranchAlreadyExistsLocally() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", BasicConstants.EXISTING_EPIC_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Epic branch '" + BasicConstants.EXISTING_EPIC_BRANCH + "' already exists.",
                "Either checkout the existing epic branch or start a new epic with another name.",
                "'git checkout " + BasicConstants.EXISTING_EPIC_BRANCH + "' to checkout the epic branch",
                "'mvn flow:epic-start' to run again and specify another epic name");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteEpicBranchAlreadyExistsRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", BasicConstants.EXISTING_EPIC_NAME);
        git.deleteLocalBranch(repositorySet, USED_EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote epic branch '" + USED_EPIC_BRANCH + "' already exists on the remote 'origin'.",
                "Either checkout the existing epic branch or start a new epic with another name.",
                "'git checkout " + USED_EPIC_BRANCH + "' to checkout the epic branch",
                "'mvn flow:epic-start' to run again and specify another epic name");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteEpicBranchAlreadyExistsRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", BasicConstants.EXISTING_EPIC_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_EPIC_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH,
                BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT, BasicConstants.EXISTING_EPIC_ISSUE,
                BasicConstants.EXISTING_EPIC_BRANCH, BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteFetchedEpicBranchAlreadyExistsRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", BasicConstants.EXISTING_EPIC_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote epic branch '" + USED_EPIC_BRANCH + "' already exists on the remote 'origin'.",
                "Either checkout the existing epic branch or start a new epic with another name.",
                "'git checkout " + USED_EPIC_BRANCH + "' to checkout the epic branch",
                "'mvn flow:epic-start' to run again and specify another epic name");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertEpicStartedCorrectly();
        assertArtifactInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchSameAsMasterBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveMode() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
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
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveModeAnswerNo() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
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
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertTestfileContent(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranch() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranchAndFetchRemoteFalse() throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndFetchedNewerRemoteIntegartionBranchAndFetchRemoteFalse()
            throws Exception {
        // set up
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        git.fetch(repositorySet);
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
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
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithDivergentLocalAndRemoteIntegrationBranches() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createIntegeratedBranch(repositorySet, INTEGRATION_BRANCH);
        git.switchToBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Local and remote integration branches '" + INTEGRATION_BRANCH
                        + "' diverge, this indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt("Epic branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Epic branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteOnOtherBranchAndPromptAnswerNo() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt("Epic branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Epic branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);

        assertGitFlowFailureException(result, "Epic start process aborted by user.", null);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranchInBatchMode() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteOnOtherBranchWithChanges() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt("Epic branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_EPIC_BRANCH_NAME)).thenReturn(EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Epic branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_EPIC_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("flow.installProject", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "Failed to install the project on epic branch after epic start.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-start' again in order to continue.");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);

        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicStart.cleanInstall");
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, "Failed to install the project on epic branch after epic start.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-start' again in order to continue.");
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicStart.cleanInstall");
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_SET_VERSION);

        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);

        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, "Failed to install the project on epic branch after epic start.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-start' again in order to continue.");
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicStart.cleanInstall");
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_SET_VERSION);

        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);

        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteWithJobBuild() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        userProperties.setProperty("jobBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        verifyZeroInteractions(promptControllerMock);

        assertEpicStartedCorrectly();
        assertArtifactNotInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertEquals("true", branchConfig.getProperty("JOB_BUILD"));
    }

    @Test
    public void testExecuteAndCheckIfUpstreamSet() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("epicName", EPIC_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertTrackingBranch(repositorySet, "origin/" + EPIC_BRANCH, EPIC_BRANCH);
    }

}
