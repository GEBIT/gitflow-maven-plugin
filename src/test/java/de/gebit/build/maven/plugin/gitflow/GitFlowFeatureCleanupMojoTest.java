//
// GitFlowFeatureCleanupMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureCleanupMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase-cleanup";

    private static final String FEATURE_NAME = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_NAME + "-SNAPSHOT";

    private static final String FEATURE_NAME_2 = TestProjects.BASIC.jiraProject + "-4711";

    private static final String FEATURE_BRANCH_2 = "feature/" + FEATURE_NAME_2;

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NAME + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_TESTFILE_MODIFIED = "Unit test dummy file modified";

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT = "Feature branches:" + LS + "1. " + FEATURE_BRANCH
            + LS + "Choose feature branch to clean up";

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
        MavenExecutionResult result = executeMojoWithCommandLineExceptionInInteractiveMode(
                repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    private void startFeatureAndAddTwoCommits() throws Exception, GitAPIException, IOException {
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("pick", "fixup");
    }

    private void assertCleanedUpCorrectly() throws GitAPIException, IOException, FileNotFoundException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        assertRebasedCorrectly();
    }

    private void assertRebasedCorrectly() throws GitAPIException, IOException, FileNotFoundException {
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
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

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteOnFeatureBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH_2);
        startFeatureAndAddTwoCommits();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        assertRebasedCorrectly();
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH_2);
        startFeatureAndAddTwoCommits();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS + "2. " + FEATURE_BRANCH_2 + LS
                + "Choose feature branch to clean up";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        assertRebasedCorrectly();
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        startFeatureAndAddTwoCommits();
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "'mvn flow:feature-rebase-cleanup' can be executed only in interactive mode.",
                "Please run in interactive mode.", "'mvn flow:feature-rebase-cleanup' to run in interactive mode");
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "'mvn flow:feature-rebase-cleanup' can be executed only in interactive mode.",
                "Please run in interactive mode.", "'mvn flow:feature-rebase-cleanup' to run in interactive mode");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteDeleteRemoteBranchOnRebaseTrue() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.deleteRemoteBranchOnRebase", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
        // TODO: verify if remote branch was really deleted before rebase
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

}
