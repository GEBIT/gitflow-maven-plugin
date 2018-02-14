//
// GitFlowFeatureStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.Test;

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

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
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

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH);
            assertVersionInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
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
    public void testExecuteWithUncommitedChanges() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            git.createAndAddToIndexTestfile(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            String message = "You have some uncommitted files. Commit or discard local changes in order to proceed.";
            assertMavenFailureException(result, message);
        }
    }

}
