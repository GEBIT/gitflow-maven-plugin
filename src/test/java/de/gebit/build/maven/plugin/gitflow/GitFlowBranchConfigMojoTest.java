//
// GitFlowBranchConfigMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowBranchConfigMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "branch-config";

    private static final String PROMPT_PROPERTY_NAME = "Which property to modify?";

    private static final String PROMPT_PROPERTY_VALUE = "Set the value to (empty to delete)";

    private static final String PROPERTY_NAME = "test.prop.name";

    private static final String PROPERTY_NAME2 = "test.prop.name2";

    private static final String PROPERTY_VALUE = "42";

    private static final String PROPERTY_VALUE2 = "4711";

    private static final String CONFIG_BRANCH_NAME = "branch-config";

    private static final String CONFIG_BRANCH_DIR = ".branch-config";

    private static final String EXPECTED_COMMIT_MESSAGE = "NO-ISSUE: branch configuration update";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
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
    public void testExecuteRemoteConfigBranchExists() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);

        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteRemoteConfigBranchNotExisting() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);

        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteTwice() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME);
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractionsAndReset(promptControllerMock);

        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME2);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE2);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractionsAndReset(promptControllerMock);

        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
        assertEquals(PROPERTY_VALUE2, branchConfig.getProperty(PROPERTY_NAME2));
    }

}
