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

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowBranchConfigMojoRemoveAllTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "branch-config";

    private static final String BRANCH_NAME = BasicConstants.EXISTING_FEATURE_BRANCH;

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
    public void testExecuteRemoveForExistingBranch() throws Exception {
        // set up
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("removeAllForBranch", BRANCH_NAME);
        when(promptControllerMock.prompt(
                "The branch '" + BRANCH_NAME + "' exists. "
                        + "Are you sure you want to remove all properties for existing branch?",
                Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(
                "The branch '" + BRANCH_NAME + "' exists. "
                        + "Are you sure you want to remove all properties for existing branch?",
                Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
    }

    @Test
    public void testExecuteRemoveForExistingBranchAndAnswerNo() throws Exception {
        // set up
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("removeAllForBranch", BRANCH_NAME);
        when(promptControllerMock.prompt(
                "The branch '" + BRANCH_NAME + "' exists. "
                        + "Are you sure you want to remove all properties for existing branch?",
                Arrays.asList("y", "n"), "n")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "Branch config process aborted by user.", null);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
    }

    @Test
    public void testExecuteRemoveForNotExistingBranch() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, BRANCH_NAME);
        git.deleteRemoteBranch(repositorySet, BRANCH_NAME);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("removeAllForBranch", BRANCH_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
    }

    @Test
    public void testExecuteRemoveForExistingBranchInBatchMode() throws Exception {
        // set up
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("removeAllForBranch", BRANCH_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The branch '" + BRANCH_NAME + "' exists. All properties for an "
                        + "existing branch can't be removed in non-interactive mode.",
                "Either remove branch locally and remotely first or run in interactive mode");
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
    }

    @Test
    public void testExecuteRemoveForNotExistingBranchInBatchMode() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, BRANCH_NAME);
        git.deleteRemoteBranch(repositorySet, BRANCH_NAME);
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("removeAllForBranch", BRANCH_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, BRANCH_NAME);
    }

}
