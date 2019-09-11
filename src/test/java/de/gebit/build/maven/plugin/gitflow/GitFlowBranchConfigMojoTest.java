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
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
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

    private static final String EXPECTED_INITIAL_COMMIT_MESSAGE = TestProjects.BASIC.jiraProject
            + "-NONE: initialization of config branch";

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
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.show", "true");
        MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(), GOAL,
                userProperties);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecuteConfigBranchNotExistingLocally() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchNotExistingLocallyAndRemotely() throws Exception {
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
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteTwice() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
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
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_COMMIT_MESSAGE, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
        assertEquals(PROPERTY_VALUE2, branchConfig.getProperty(PROPERTY_NAME2));
    }

    @Test
    public void testExecutePropertForOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, OTHER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteWithNoPropertyValue() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH, PROPERTY_NAME,
                PROPERTY_VALUE);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(null);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                GitExecution.COMMIT_MESSAGE_SET_CENTRAL_BRANCH_CONFIG, EXPECTED_INITIAL_COMMIT_MESSAGE);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
    }

    @Test
    public void testExecuteUsingParametersInInteractiveMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteUsingPropertyNameParameterInInteractiveMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteUsingPropertyValueParameterInInteractiveMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteInBatchModeAndMissingPropertyName() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Property 'propertyName' is required in non-interactive mode but was not set.",
                "Specify a propertyName or run in interactive mode.",
                "'mvn flow:branch-config -DpropertyName=XXX -B' to predefine property name",
                "'mvn flow:branch-config' to run in interactive mode");
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertMissingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
    }

    @Test
    public void testExecuteInBatchModeAndMissingPropertyValue() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH, PROPERTY_NAME,
                PROPERTY_VALUE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                GitExecution.COMMIT_MESSAGE_SET_CENTRAL_BRANCH_CONFIG, EXPECTED_INITIAL_COMMIT_MESSAGE);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
    }

    @Test
    public void testExecuteRemoveProperty() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH, PROPERTY_NAME,
                PROPERTY_VALUE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                GitExecution.COMMIT_MESSAGE_SET_CENTRAL_BRANCH_CONFIG, EXPECTED_INITIAL_COMMIT_MESSAGE);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
    }

    @Test
    public void testExecuteRemoveNotExistingProperty() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH, PROPERTY_NAME,
                PROPERTY_VALUE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME2);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                GitExecution.COMMIT_MESSAGE_SET_CENTRAL_BRANCH_CONFIG, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecutePropertForBranchWithDoubleDots() throws Exception {
        // set up
        final String OTHER_BRANCH = "../otherBranch";
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Invalid branch name '" + OTHER_BRANCH + "' detected.\nCentral branch config can't be changed.", null);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertNull("branch config exists but not expected", branchConfig);
    }

    @Test
    public void testExecuteForFeatureBranch() throws Exception {
        // set up
        final String FEATURE_BRANCH = "feature/GFTST-42";
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                FEATURE_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchNotExistingRemotely() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchExistsLocallyAndRemotely() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.switchToBranch(repositorySet, CONFIG_BRANCH_NAME);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteWithExistingWorktree() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
        when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
        git.createOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).mkdir();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
        verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
        verifyNoMoreInteractions(promptControllerMock);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertMissingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME,
                MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchNotExistingLocallyAndOfflineMode() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertExistingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchNotExistingLocallyAndOfflineModeWithPrefetch() throws Exception {
        // set up
        git.createRemoteOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertExistingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchNotExistingRemotelyAndOfflineMode() throws Exception {
        // set up
        git.createOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertExistingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertMissingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertCommitsInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

    @Test
    public void testExecuteConfigBranchExistsLocallyAndRemotelyAndOfflineMode() throws Exception {
        // set up
        git.createOrphanBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.switchToBranch(repositorySet, CONFIG_BRANCH_NAME);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("propertyName", PROPERTY_NAME);
        userProperties.setProperty("propertyValue", PROPERTY_VALUE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertFalse("config branch directory not removed",
                new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
        git.assertExistingLocalBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertExistingRemoteBranches(repositorySet, CONFIG_BRANCH_NAME);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
        git.assertCommitsInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE,
                EXPECTED_INITIAL_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_INITIAL_COMMIT_MESSAGE);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME, MASTER_BRANCH);
        assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
    }

}
