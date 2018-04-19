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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

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
public class GitFlowFeatureStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-start";

    private static final String FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME = FEATURE_ISSUE + "-someDescription";

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_ISSUE + ": updating versions for feature branch";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_ISSUE + "-SNAPSHOT";

    private static final String INTEGRATION_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String PROMPT_BRANCH_OF_LAST_INTEGRATED = "The current commit on " + MASTER_BRANCH
            + " is not integrated. Create a branch of the last integrated commit (" + INTEGRATION_BRANCH + ")?";

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
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();
        assertArtifactNotInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    private void assertCentralBranchConfigSetCorrectly(final String expectedVersionChangeCommit)
            throws IOException, GitAPIException {
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("featureStartMessage"));
        assertEquals(expectedVersionChangeCommit, branchConfig.getProperty("versionChangeCommit"));
    }

    private void assertFeatureStartedCorrectly()
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);

        assertCentralBranchConfigSetCorrectly(null);
    }

    @Test
    public void testExecuteTychoBuild() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);

        assertCentralBranchConfigSetCorrectly(null);
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NAME);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), TestProjects.INVALID_VERSION.version);
            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(otherRepositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
            git.assertRemoteBranches(otherRepositorySet, MASTER_BRANCH, CONFIG_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, FEATURE_BRANCH);

            Properties branchConfig = git.readPropertiesFileInLocalBranch(otherRepositorySet, CONFIG_BRANCH,
                    FEATURE_BRANCH);
            assertEquals("feature", branchConfig.getProperty("branchType"));
            assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
            assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
            assertEquals(TestProjects.INVALID_VERSION.version, branchConfig.getProperty("baseVersion"));
            assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("featureStartMessage"));
            assertEquals(null, branchConfig.getProperty("versionChangeCommit"));
        }
    }

    @Test
    public void testExecuteNoCommandsAfterFeatureVersion() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.commandsAfterFeatureVersion", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteNoCommandsAfterFeatureVersionAndNoCommandsAfterVersion() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.commandsAfterVersion", "");
        userProperties.setProperty("flow.commandsAfterFeatureVersion", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertProjectVersionInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        assertVersionBuildPropertyInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithUntrackedChanges() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        git.createTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

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

        Set<String> addedFiles = git.status(repositorySet).getAdded();
        assertEquals("number of added files is wrong", 1, addedFiles.size());
        assertEquals("added file is wrong", GitExecution.TESTFILE_NAME, addedFiles.iterator().next());
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

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithLocalChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + MASTER_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + MASTER_BRANCH + "'");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChanges() throws Exception {
        // set up
        git.remoteCreateTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "Remote branch is ahead of the local branch '" + MASTER_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed.", "'git pull'");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithLocalAndRemoteChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote_testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "Local and remote branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithLocalChangesAndFetchRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + MASTER_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + MASTER_BRANCH + "'");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithRemoteChangesAndFetchRemoteFalse() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        git.remoteCreateTestfile(repositorySet);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

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
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Remote branch is ahead of the local branch '" + MASTER_BRANCH + "'.",
                "Pull changes on remote branch to the local branch in order to proceed.", "'git pull'");
        assertNoChanges();
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
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "Local and remote branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositoriesExceptCommitedTestfile();
    }

    @Test
    public void testExecuteWithFeatureName() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithoutFeatureNameInBatchMode() throws Exception {
        // set up
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Property 'featureName' is required in non-interactive mode but was not set.",
                "Specify a featureName or run in interactive mode.", "'mvn flow:feature-start -DfeatureName=XXX -B'",
                "'mvn flow:feature-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithInvalidFeatureNameInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", "Invalid-feature-name");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The feature name 'Invalid-feature-name' is invalid. "
                        + "A feature name must start with the project's JIRA issue key, e.g. "
                        + TestProjects.BASIC.jiraProject + "-[number][-optional-short-description]",
                "Specify correct value for parameter 'featureName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithCustomFeatureNamePatternDescriptionAndInvalidFeatureNameInBatchMode() throws Exception {
        // set up
        final String FEATURE_NAME_PATTERN_DESCRIPTION = "Test feature name pattern description";
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", "Invalid-feature-name");
        userProperties.setProperty("flow.featureNamePatternDescription", FEATURE_NAME_PATTERN_DESCRIPTION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The feature name 'Invalid-feature-name' is invalid. " + FEATURE_NAME_PATTERN_DESCRIPTION,
                "Specify correct value for parameter 'featureName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithDefaultFeatureNamePatternDescriptionAndInvalidFeatureNameInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", "Invalid-feature-name");
        userProperties.setProperty("flow.featureNamePatternDescription", "");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The feature name 'Invalid-feature-name' is invalid. " + "It does not match the required pattern: ^((?:"
                        + TestProjects.BASIC.jiraProject + ")-\\d+)(?:-[^\\s]*)?$",
                "Specify correct value for parameter 'featureName' and run again.");
        assertNoChanges();
    }

    @Test
    public void testExecuteWithInvalidFeatureNameInInteractivMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", "Invalid-feature-name");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock).showMessage("The feature name 'Invalid-feature-name' is invalid."
                + " A feature name must start with the project's JIRA issue key, e.g. " + TestProjects.BASIC.jiraProject
                + "-[number][-optional-short-description]");
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithInvalidFeatureNameFromPrompterInInteractivMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock, times(2)).showMessage("The feature name 'Invalid-feature-name' is invalid."
                + " A feature name must start with the project's JIRA issue key, e.g. " + TestProjects.BASIC.jiraProject
                + "-[number][-optional-short-description]");
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithCustomFeatureNamePatternDescriptionAndInvalidFeatureName() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NAME);
        Properties userProperties = new Properties();
        final String FEATURE_NAME_PATTERN_DESCRIPTION = "Test feature name pattern description";
        userProperties.setProperty("flow.featureNamePatternDescription", FEATURE_NAME_PATTERN_DESCRIPTION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock, times(2))
                .showMessage("The feature name 'Invalid-feature-name' is invalid. " + FEATURE_NAME_PATTERN_DESCRIPTION);
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithDefaultFeatureNamePatternDescriptionAndInvalidFeatureName() throws Exception {
        // set up
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureNamePatternDescription", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock, times(2)).showMessage(
                "The feature name 'Invalid-feature-name' is invalid. It does not match the required pattern: ^((?:"
                        + TestProjects.BASIC.jiraProject + ")-\\d+)(?:-[^\\s]*)?$");
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureStartedCorrectly();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithoutFeatureNamePattern() throws Exception {
        // set up
        final String FEATURE_START_MESSAGE = "updating versions for feature branch";
        final String INVALID_FEATURE_NAME = "Invalid-feature-name";
        final String EXPECTED_FEATURE_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-" + INVALID_FEATURE_NAME
                + "-SNAPSHOT";
        final String EXPECTED_FEATURE_BRANCH = "feature/" + INVALID_FEATURE_NAME;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureNamePattern", "");
        userProperties.setProperty("flow.featureStartMessage", FEATURE_START_MESSAGE);
        userProperties.setProperty("featureName", INVALID_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_FEATURE_VERSION_NUMBER);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, FEATURE_START_MESSAGE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(INVALID_FEATURE_NAME, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(FEATURE_START_MESSAGE, branchConfig.getProperty("featureStartMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithFeatureNamePatternWithGroups() throws Exception {
        // set up
        final String EXPECTED_FEATURE_NAME = FEATURE_NAME + "-test";
        final String EXPECTED_FEATURE_BRANCH = "feature/" + EXPECTED_FEATURE_NAME;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureNamePattern", "^(${project.jira}-\\d+)(?:-[^\\s]*)?$");
        userProperties.setProperty("featureName", EXPECTED_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("featureStartMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithFeatureNamePatternWithoutGroups() throws Exception {
        // set up
        final String FEATURE_START_MESSAGE = "updating versions for feature branch";
        final String EXPECTED_FEATURE_NAME = FEATURE_NAME + "-test";
        final String EXPECTED_FEATURE_BRANCH = "feature/" + EXPECTED_FEATURE_NAME;
        final String EXPECTED_FEATURE_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-" + EXPECTED_FEATURE_NAME
                + "-SNAPSHOT";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureNamePattern", "^${project.jira}-\\d+-.+$");
        userProperties.setProperty("flow.featureStartMessage", FEATURE_START_MESSAGE);
        userProperties.setProperty("featureName", EXPECTED_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_FEATURE_VERSION_NUMBER);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, FEATURE_START_MESSAGE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_FEATURE_NAME, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(FEATURE_START_MESSAGE, branchConfig.getProperty("featureStartMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureBranchAlreadyExistsLocally() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "Feature branch '" + FEATURE_BRANCH + "' already exists.",
                "Either checkout the existing feature branch or start a new feature with another name.",
                "'git checkout " + FEATURE_BRANCH + "' to checkout the feature branch",
                "'mvn flow:feature-start' to run again and specify another feature name");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteFeatureBranchAlreadyExistsRemotely() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        git.createRemoteBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote feature branch '" + FEATURE_BRANCH + "' already exists on the remote 'origin'.",
                "Either checkout the existing feature branch or start a new feature with another name.",
                "'git checkout " + FEATURE_BRANCH + "' to checkout the feature branch",
                "'mvn flow:feature-start' to run again and specify another feature name");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteFeatureBranchAlreadyExistsRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.createRemoteBranch(repositorySet, FEATURE_BRANCH);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteFetchedFeatureBranchAlreadyExistsRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        git.createRemoteBranch(repositorySet, FEATURE_BRANCH);
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote feature branch '" + FEATURE_BRANCH + "' already exists on the remote 'origin'.",
                "Either checkout the existing feature branch or start a new feature with another name.",
                "'git checkout " + FEATURE_BRANCH + "' to checkout the feature branch",
                "'mvn flow:feature-start' to run again and specify another feature name");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFeatureStartedCorrectly();
        assertArtifactInstalled();

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchSameAsMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveMode() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertTestfileMissing(repositorySet);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveModeAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
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
                "Integration branch '" + INTEGRATION_BRANCH + "' is ahead of base branch '" + MASTER_BRANCH
                        + "', this indicates a severe error condition on your branches.",
                " Please consult a gitflow expert on how to fix this!");
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranchAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteWithIntegrationBranchAndFetchedNewerRemoteIntegartionBranchAndFetchRemoteFalse()
            throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
        git.fetch(repositorySet);
        when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Integration branch '" + INTEGRATION_BRANCH + "' is ahead of base branch '" + MASTER_BRANCH
                        + "', this indicates a severe error condition on your branches.",
                " Please consult a gitflow expert on how to fix this!");
        verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithDivergentLocalAndRemoteIntegrationBranches() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
        git.switchToBranch(repositorySet, INTEGRATION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt("Feature branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Feature branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

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
        when(promptControllerMock.prompt("Feature branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Feature branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);

        assertGitFlowFailureException(result, "Feature start process aborted by user.", null);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranchInBatchMode() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteOnOtherBranchWithChanges() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt("Feature branch will be started not from current branch but will be based "
                + "off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt("Feature branch will be started not from current branch but will be "
                + "based off branch '" + MASTER_BRANCH + "'. Continue?", Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        assertCentralBranchConfigSetCorrectly(EXPECTED_VERSION_CHANGE_COMMIT);
    }

    @Test
    public void testExecuteOnEpicBranch() throws Exception {
        // set up
        final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-4711";
        final String EPIC_NAME = EPIC_ISSUE + "-someEpicDescription";
        final String EPIC_BRANCH = "epic/" + EPIC_NAME;
        final String EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + EPIC_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_EPIC_SET_VERSION = EPIC_ISSUE + ": updating versions for epic branch";
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_EPIC_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(EPIC_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("featureStartMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteOnEpicBranchWithoutEpicVersion() throws Exception {
        // set up
        final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-4711";
        final String EPIC_NAME = EPIC_ISSUE + "-someEpicDescription";
        final String EPIC_BRANCH = "epic/" + EPIC_NAME;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME, userProperties);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(EPIC_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(TestProjects.BASIC.version, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("featureStartMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

}
