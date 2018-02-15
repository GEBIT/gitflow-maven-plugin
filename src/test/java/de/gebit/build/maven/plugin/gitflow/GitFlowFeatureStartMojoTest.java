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
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

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

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

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

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        }
    }

    @Ignore("Should be activated again before refactoring of AbstractGitFlowMojo.gitFetchRemoteAndCompare() method")
    @Test
    public void testExecuteWithLocalChangesAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndCommitTestfile(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.fetchRemote", "false");
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "Local branch is ahead of the remote branch " + MASTER_BRANCH + ". Execute git push.");

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Ignore
    @Test
    public void testExecuteWithFetchedRemoteChangesAndFetchRemoteFalse() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.remoteCreateTestfile(repositorySet);
            git.fetch(repositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.fetchRemote", "false");
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            assertMavenFailureException(result,
                    "Remote branch is ahead of the local branch " + MASTER_BRANCH + ". Execute git pull.");

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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
            // verify
            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

    @Test
    public void testExecuteWithoutFeatureNameInBatchMode() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
            // verify
            assertMavenFailureException(result, "No feature name set, aborting...");

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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
            assertMavenFailureException(result, "Set feature name is not valid, aborting...");

            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
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

            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        }
    }

}
