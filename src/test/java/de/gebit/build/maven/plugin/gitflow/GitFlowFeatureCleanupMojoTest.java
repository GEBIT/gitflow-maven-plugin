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
import org.eclipse.jgit.api.CheckoutCommand.Stage;
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

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_FEATURE_VERSION = "1.42.0-" + FEATURE_NAME + "-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NAME + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_TESTFILE_MODIFIED = "Unit test dummy file modified";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

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
        startFeatureAndAddTwoCommits(false);
    }

    private void startAndPushFeatureAndAddTwoCommits() throws Exception, GitAPIException, IOException {
        startFeatureAndAddTwoCommits(true);
    }

    private void startFeatureAndAddTwoCommits(boolean pushBeforeCommits)
            throws Exception, GitAPIException, IOException {
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        if (pushBeforeCommits) {
            git.push(repositorySet);
        }
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
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
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

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        startFeatureAndAddTwoCommits();
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        startFeatureAndAddTwoCommits();
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
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
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemote() throws Exception {
        // set up
        startAndPushFeatureAndAddTwoCommits();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocal() throws Exception {
        startAndPushFeatureAndAddTwoCommits();
        git.remoteCreateTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        startAndPushFeatureAndAddTwoCommits();
        final String COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote_testfile.txt", COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteFeatureStartetWithoutVersionChange() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipFeatureVersion", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("pick", "fixup");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithRebasePaused() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("pick", "edit");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithConflictOnRebase() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithErrorOnInteractiveRebase() throws Exception {
        // set up
        startFeatureAndAddTwoCommits();
        git.prepareErrorWhileUsingGitEditor(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Interactive rebase failed.", "Check the output above for the reason. "
                + "Fix the problem or consult a gitflow expert on how to fix this!");
        git.assertNoRebaseInProcess(repositorySet);
    }

    @Test
    public void testExecuteContinueAfterRebasePaused() throws Exception {
        // set up
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterRebasePausedTwice() throws Exception {
        // set up
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "edit", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterConflictOnRebase() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterConflictOnRebaseTwice() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED = "FEATURE: Unit test dummy file modified";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(FEATURE_TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(FEATURE_TESTFILE_NAME).call();
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
        git.assertTestfileContentModified(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterRebasePausedAndConflictOnRebase() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED = "FEATURE: Unit test dummy file modified";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("edit", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(FEATURE_TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(FEATURE_TESTFILE_NAME).call();
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContent(repositorySet);
        git.assertTestfileContentModified(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterConflictOnRebaseAndRebasePause() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("drop", "pick", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-rebase-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterConflictOnRebaseWithErrorOnInteractiveRebase() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED = "FEATURE: Unit test dummy file modified";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-rebase-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-rebase-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        git.prepareErrorWhileUsingGitEditor(repositorySet);
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, new GitFlowFailureInfo(
                "\\QContinuation of interactive rebase failed.\nGit error message:\n\\E.*",
                "\\QFix the problem described in git error message or consult a gitflow expert on how to fix this!\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

}
