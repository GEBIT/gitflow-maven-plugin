//
// GitFlowBranchConfigMojoRemoveAllTest.java
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

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowBranchConfigMojoCleanupTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "branch-config";

    private static final String NOT_EXISTING_FEATURE_BRANCH = "feature/not-existing";

    private static final String NOT_EXISTING_RELEASE_BRANCH = "release/not-existing";

    private static final String NOT_EXISTING_CUSTOM_BRANCH = "custom/not-existing";

    private static final String PROMPT_CONFIRMATION = "Do you realy want to remove all orphaned branch configs listed above?";

    private static final String COMMIT_MESSAGE_CLEANUP = TestProjects.BASIC.jiraProject
            + "-NONE: clean-up orphaned branch configs";

    private RepositorySet repositorySet;
    private Properties userProperties = new Properties();

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC);
        userProperties.setProperty("flow.cleanup", "true");
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecuteNothingToCleanup() throws Exception {
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertLastCommitsInRemoteBranchMissing(repositorySet, CONFIG_BRANCH, COMMIT_MESSAGE_CLEANUP);
    }

    @Test
    public void testExecuteOrphanedFeatureBranchConfig() throws Exception {
        // set up
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH, "branchType",
                "feature");
        when(promptControllerMock.prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH);
        git.assertLastCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH, COMMIT_MESSAGE_CLEANUP);
    }

    @Test
    public void testExecuteOrphanedFeatureBranchConfigAndPromptAnswerNo() throws Exception {
        // set up
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH, "branchType",
                "feature");
        when(promptControllerMock.prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Clean-up of branch configs aborted by user.", null);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH);
        git.assertLastCommitsInRemoteBranchMissing(repositorySet, CONFIG_BRANCH, COMMIT_MESSAGE_CLEANUP);
    }

    @Test
    public void testExecuteOrphanedMultipleBranchConfigs() throws Exception {
        // set up
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH, "branchType",
                "feature");
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_RELEASE_BRANCH, "branchType",
                "release");
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_CUSTOM_BRANCH, "dummy", "dummy");
        when(promptControllerMock.prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_CONFIRMATION, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, NOT_EXISTING_RELEASE_BRANCH);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, NOT_EXISTING_CUSTOM_BRANCH);
        git.assertLastCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH, COMMIT_MESSAGE_CLEANUP);
    }

    @Test
    public void testExecuteOrphanedMultipleBranchConfigsInBatchMode() throws Exception {
        // set up
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH, "branchType",
                "feature");
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_RELEASE_BRANCH, "branchType",
                "release");
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, NOT_EXISTING_CUSTOM_BRANCH, "dummy", "dummy");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, NOT_EXISTING_FEATURE_BRANCH);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, NOT_EXISTING_RELEASE_BRANCH);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, NOT_EXISTING_CUSTOM_BRANCH);
        git.assertLastCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH, COMMIT_MESSAGE_CLEANUP);
    }

    @Test
    public void testExecuteWithFetchRemoteFalse() throws Exception {
        // set up
        userProperties.setProperty("flow.fetchRemote", "false");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "Clean-up of branch configs can be executed only if fetchRemote=true.",
                null);
    }

    @Test
    public void testExecuteWithPushRemoteFalse() throws Exception {
        // set up
        userProperties.setProperty("flow.push", "false");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "Clean-up of branch configs can be executed only if pushRemote=true.",
                null);
    }

}
