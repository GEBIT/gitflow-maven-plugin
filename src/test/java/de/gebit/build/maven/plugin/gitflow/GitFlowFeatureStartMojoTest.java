//
// GitFlowFeatureStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.*;
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
import org.junit.Ignore;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-start";

    private static final String FEATURE_NUMBER = TestProjects.BASIC.jiraProject + "-42";

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NUMBER + ": updating versions for feature branch";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_BRANCH_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_NUMBER
            + "-SNAPSHOT";

    private static final String INTEGRATION_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String PROMPT_BRANCH_OF_LAST_INTEGRATED = "The current commit on " + MASTER_BRANCH
            + " is not integrated. Create a branch of the last integrated commit (" + INTEGRATION_BRANCH + ")?";

    @Test
    public void testExecuteWithCommandLineException() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // test
            MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(),
                    GOAL);
            // verify
            assertMavenExecutionException(result, "Error while executing external command.");
        }
    }

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
            assertArtifactNotInstalled();
        }
    }

    private void assertFeatureStartedCorrectly(RepositorySet repositorySet)
            throws ComponentLookupException, ModelParseException, IOException, GitAPIException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSkipFeatureVersion() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
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
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);
        }
    }

    @Test
    public void testExecuteTychoBuild() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
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
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);
        }
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.INVALID_VERSION.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);
        }
    }

    @Test
    public void testExecuteNoCommandsAfterFeatureVersion() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.commandsAfterFeatureVersion", "");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteNoCommandsAfterFeatureVersionAndNoCommandsAfterVersion() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.commandsAfterVersion", "");
            userProperties.setProperty("flow.commandsAfterFeatureVersion", "");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertProjectVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            assertVersionBuildPropertyInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Test
    public void testExecuteWithUntrackedChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            git.createTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

            Set<String> untrackedFiles = git.status(repositorySet).getUntracked();
            assertEquals("number of untracked files is wrong", 1, untrackedFiles.size());
            assertEquals("untracked file is wrong", GitExecution.TESTFILE_NAME, untrackedFiles.iterator().next());
            git.assertTestfileContent(repositorySet);
        }
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndAddToIndexTestfile(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "You have some uncommitted files. Commit or discard local changes in order to proceed.");

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            assertNoChangesInRepositories(repositorySet);

            Set<String> addedFiles = git.status(repositorySet).getAdded();
            assertEquals("number of added files is wrong", 1, addedFiles.size());
            assertEquals("added file is wrong", GitExecution.TESTFILE_NAME, addedFiles.iterator().next());
            git.assertTestfileContent(repositorySet);
        }
    }

    @Test
    public void testExecuteWithUnstagedChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndCommitTestfile(repositorySet);
            git.modifyTestfile(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "You have some uncommitted files. Commit or discard local changes in order to proceed.");

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            assertNoChangesInRepositoriesExceptCommitedTestfile(repositorySet);

            Set<String> modifiedFiles = git.status(repositorySet).getModified();
            assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
            assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
            git.assertTestfileContentModified(repositorySet);
        }
    }

    @Ignore("Should be activated again before refactoring of AbstractGitFlowMojo.gitFetchRemoteAndCompare() method")
    @Test
    public void testExecuteWithLocalChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndCommitTestfile(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "Local branch is ahead of the remote branch " + MASTER_BRANCH + ". Execute git push.");

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            assertNoChangesInRepositoriesExceptCommitedTestfile(repositorySet);
            git.assertTestfileContent(repositorySet);
        }
    }

    @Test
    public void testExecuteWithRemoteChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.remoteCreateTestfile(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "Remote branch is ahead of the local branch " + MASTER_BRANCH + ". Execute git pull.");
            assertNoChanges(repositorySet);
        }
    }

    private void assertNoChanges(RepositorySet repositorySet)
            throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories(repositorySet);
    }

    private void assertNoChangesInRepositories(RepositorySet repositorySet)
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile(RepositorySet repositorySet)
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Ignore("Should be activated again before refactoring of AbstractGitFlowMojo.gitFetchRemoteAndCompare() method")
    @Test
    public void testExecuteWithLocalChangesAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndCommitTestfile(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.fetchRemote", "false");
            git.setOffline(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            git.setOnline(repositorySet);
            assertMavenFailureException(result,
                    "Local branch is ahead of the remote branch " + MASTER_BRANCH + ". Execute git push.");

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertTestfileContent(repositorySet);
        }
    }

    @Test
    public void testExecuteWithRemoteChangesAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            git.remoteCreateTestfile(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.fetchRemote", "false");
            git.setOffline(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            git.setOnline(repositorySet);
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Ignore("Should be activated again before refactoring of AbstractGitFlowMojo.gitFetchRemoteAndCompare() method")
    @Test
    public void testExecuteWithFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.remoteCreateTestfile(repositorySet);
            git.fetch(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.fetchRemote", "false");
            git.setOffline(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties);
            // verify
            git.setOnline(repositorySet);
            assertMavenFailureException(result,
                    "Remote branch is ahead of the local branch " + MASTER_BRANCH + ". Execute git pull.");
            assertNoChanges(repositorySet);
        }
    }

    @Test
    public void testExecuteWithFeatureName() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteWithoutFeatureNameInBatchMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
            // verify
            assertMavenFailureException(result, "No featureName set, aborting...");
            assertNoChanges(repositorySet);
        }
    }

    @Test
    public void testExecuteWithInvalidFeatureNameInBatchMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", "Invalid-feature-name");
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties);
            // verify
            assertMavenFailureException(result, "Set featureName is not valid, aborting...");
            assertNoChanges(repositorySet);
        }
    }

    @Test
    public void testExecuteWithInvalidFeatureNameInInteractivMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", "Invalid-feature-name");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock)
                    .showMessage("A feature name must start with the project's JIRA issue key, e.g. "
                            + TestProjects.BASIC.jiraProject + "-[number][-optional-short-description]");
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteWithInvalidFeatureNameFromPrompterInInteractivMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock, times(2))
                    .showMessage("A feature name must start with the project's JIRA issue key, e.g. "
                            + TestProjects.BASIC.jiraProject + "-[number][-optional-short-description]");
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteWithCustomFeatureNamePatternDescriptionAndInvalidFeatureName() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NUMBER);
            Properties userProperties = new Properties();
            final String FEATURE_NAME_PATTERN_DESCRIPTION = "Test feature name pattern description";
            userProperties.setProperty("flow.featureNamePatternDescription", FEATURE_NAME_PATTERN_DESCRIPTION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock, times(2)).showMessage(FEATURE_NAME_PATTERN_DESCRIPTION);
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteWithDefaultFeatureNamePatternDescriptionAndInvalidFeatureName() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn("Invalid-feature-name", "Invalid-feature-name", FEATURE_NUMBER);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.featureNamePatternDescription", "");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock, times(3)).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock, times(2)).showMessage("Feature name does not match the required pattern: ^("
                    + TestProjects.BASIC.jiraProject + "-\\d+)(?:-[^\\s]*)?$");
            verifyNoMoreInteractions(promptControllerMock);

            assertFeatureStartedCorrectly(repositorySet);
        }
    }

    @Test
    public void testExecuteWithoutFeatureNamePattern() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            final String FEATURE_START_MESSAGE = "updating versions for feature branch";
            final String INVALID_FEATURE_NAME = "Invalid-feature-name";
            final String EXPECTED_FEATURE_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-"
                    + INVALID_FEATURE_NAME + "-SNAPSHOT";
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
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, FEATURE_START_MESSAGE);
        }
    }

    @Test
    public void testExecuteWithFeatureNamePatternWithGroups() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            final String EXPECTED_FEATURE_NAME = FEATURE_NUMBER + "-test";
            final String EXPECTED_FEATURE_BRANCH = "feature/" + EXPECTED_FEATURE_NAME;
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.featureNamePattern", "^(${project.jira}-\\d+)(?:-[^\\s]*)?$");
            userProperties.setProperty("featureName", EXPECTED_FEATURE_NAME);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, EXPECTED_FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Test
    public void testExecuteWithFeatureNamePatternWithoutGroups() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            final String FEATURE_START_MESSAGE = "updating versions for feature branch";
            final String EXPECTED_FEATURE_NAME = FEATURE_NUMBER + "-test";
            final String EXPECTED_FEATURE_BRANCH = "feature/" + EXPECTED_FEATURE_NAME;
            final String EXPECTED_FEATURE_VERSION_NUMBER = TestProjects.BASIC.releaseVersion + "-"
                    + EXPECTED_FEATURE_NAME + "-SNAPSHOT";
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.featureNamePattern", "^${project.jira}-\\d+-.+$");
            userProperties.setProperty("flow.featureStartMessage", FEATURE_START_MESSAGE);
            userProperties.setProperty("featureName", EXPECTED_FEATURE_NAME);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_FEATURE_VERSION_NUMBER);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, EXPECTED_FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, EXPECTED_FEATURE_BRANCH, FEATURE_START_MESSAGE);
        }
    }

    @Test
    public void testExecuteFeatureBranchAlreadyExistsLocally() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties);
            // verify
            assertMavenFailureException(result, "Feature branch with that name already exists. Cannot start feature.");

            assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        }
    }

    @Ignore("Should be activated again before remote check will be implemented")
    @Test
    public void testExecuteFeatureBranchAlreadyExistsRemotely() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            git.createRemoteBranch(repositorySet, FEATURE_BRANCH);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties);
            // verify
            assertMavenFailureException(result,
                    "Remote feature branch with that name already exists. Cannot start feature.");
            assertNoChanges(repositorySet);
        }
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            userProperties.setProperty("flow.installProject", "true");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertFeatureStartedCorrectly(repositorySet);
            assertArtifactInstalled();
        }
    }

    @Test
    public void testExecuteWithIntegrationBranchSameAsMasterBranch() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
            git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranch() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            git.createAndCommitTestfile(repositorySet);
            git.push(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            git.assertTestfileMissing(repositorySet);
        }
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            git.createAndCommitTestfile(repositorySet);
            git.push(repositorySet);
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                    .thenReturn("y");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
            verifyNoMoreInteractions(promptControllerMock);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            git.assertTestfileMissing(repositorySet);
        }
    }

    @Test
    public void testExecuteWithIntegrationBranchDifferentFromMasterBranchInInteractiveModeAnswerNo() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            git.createAndCommitTestfile(repositorySet);
            git.push(repositorySet);
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                    .thenReturn("n");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verify(promptControllerMock).prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y");
            verifyNoMoreInteractions(promptControllerMock);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                    GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
            git.assertTestfileContent(repositorySet);
        }
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranch() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
            when(promptControllerMock.prompt(PROMPT_BRANCH_OF_LAST_INTEGRATED, Arrays.asList("y", "n"), "y"))
                    .thenReturn("y");
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            assertMavenFailureException(result, "Failed to determine branch base of '" + INTEGRATION_BRANCH
                    + "' in respect to '" + MASTER_BRANCH + "'.");
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
    }

    @Test
    public void testExecuteWithIntegrationBranchAndNewerRemoteIntegartionBranchAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_BRANCH);
            git.remoteCreateTestfileInBranch(repositorySet, INTEGRATION_BRANCH);
            Properties userProperties = new Properties();
            userProperties.setProperty("featureName", FEATURE_NUMBER);
            userProperties.setProperty("flow.fetchRemote", "false");
            git.setOffline(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            git.setOnline(repositorySet);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, MASTER_BRANCH);
            git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Ignore("Should be activated again before refactoring of AbstractGitFlowMojo.gitFetchRemoteAndCompare() method")
    @Test
    public void testExecuteWithIntegrationBranchAndFetchedNewerRemoteIntegartionBranchAndFetchRemoteFalse()
            throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
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
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            git.setOnline(repositorySet);
            assertMavenFailureException(result, "Failed to determine branch base of '" + INTEGRATION_BRANCH
                    + "' in respect to '" + MASTER_BRANCH + "'.");
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
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            final String OTHER_BRANCH = "otherBranch";
            git.switchToBranch(repositorySet, OTHER_BRANCH, true);
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Test
    public void testExecuteOnOtherBranchWithChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            final String OTHER_BRANCH = "otherBranch";
            git.switchToBranch(repositorySet, OTHER_BRANCH, true);
            git.createAndCommitTestfile(repositorySet);
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        }
    }

}
