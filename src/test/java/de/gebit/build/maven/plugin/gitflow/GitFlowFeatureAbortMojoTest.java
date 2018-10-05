//
// GitFlowFeatureAbortMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
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
public class GitFlowFeatureAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-abort";

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT = "Feature branches:" + LS + "1. "
            + BasicConstants.SINGLE_FEATURE_BRANCH + LS + "Choose feature branch to abort";

    private static final String PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_SELECT = "Feature branches:" + LS + "1. "
            + BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH + LS + "Choose feature branch to abort";

    private static final String PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION = "You have some uncommitted files. "
            + "If you continue any changes will be discarded. Continue?";

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
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
    public void testExecuteOnFeatureBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureAbortedCorrectly();
    }

    private void assertFeatureAbortedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnFeatureBranch() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureAbortedCorrectly();
        git.assertTestfileMissing(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnFeatureBranchConfirmationNo() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-abort because of uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnFeatureBranchInBatchMode() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed or run in interactive mode.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes",
                "'mvn flow:feature-abort' to run in interactive mode");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnBaseBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnFeatureBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureAbortedCorrectly();
        git.assertTestfileMissing(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnFeatureBranchInBatchMode() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed or run in interactive mode.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes",
                "'mvn flow:feature-abort' to run in interactive mode");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnBaseBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChangesOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + BasicConstants.FIRST_FEATURE_BRANCH + LS + "2. "
                + BasicConstants.SECOND_FEATURE_BRANCH + LS + "Choose feature branch to abort";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + BasicConstants.FIRST_FEATURE_BRANCH + LS + "2. "
                + BasicConstants.SECOND_FEATURE_BRANCH + LS + "Choose feature branch to abort";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertFeatureAbortedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:feature-abort' can be executed only on a feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout INTERNAL' to switch to the feature branch",
                "'mvn flow:feature-abort' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_SELECT, Arrays.asList("1")))
                .thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteBaseBranchAheadOfLocal() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_SELECT, Arrays.asList("1")))
                .thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH,
                MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureAbortedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteBaseBranchConfigMissing() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "An upgrade of central branch config is required in order to use new version of gitflow!",
                "Please run 'mvn flow:upgrade' first to upgrade central branch config.",
                "'mvn flow:upgrade' to upgrade central branch config");
    }

    @Test
    public void testExecuteBaseBranchConfigMissingAndFetchRemoteFalse() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "An upgrade of central branch config is required in order to use new version of gitflow!",
                "Please run 'mvn flow:upgrade' first to upgrade central branch config.",
                "'mvn flow:upgrade' to upgrade central branch config");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH,
                MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '"
                        + BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        git.switchToBranch(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '"
                        + BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteWhileRebaseInProcess() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "A rebase of the feature branch '" + FEATURE_BRANCH + "' is in process. Cannot abort feature now.",
                "Finish rebase process first in order to proceed.");
    }

    @Test
    public void testExecuteWhileMergeInProcess() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        assertTrue("an exception expected because of merge conflict", result.hasExceptions());
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "A merge into the current branch is in process. Cannot abort feature now.",
                "Finish merge process first in order to proceed.");
    }

    @Test
    public void testExecutePushRemoteTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureAbortedCorrectly();
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

}
