//
// GitFlowEpicFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowEpicFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-finish";

    private static final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String EPIC_NAME = EPIC_ISSUE + "-someDescription";

    private static final String EPIC_BRANCH = "epic/" + EPIC_NAME;

    private static final String EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + EPIC_ISSUE + "-SNAPSHOT";

    private static final String EPIC_NUMBER_2 = TestProjects.BASIC.jiraProject + "-4711";

    private static final String EPIC_BRANCH_2 = "epic/" + EPIC_NUMBER_2;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + EPIC_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch epic/" + EPIC_NAME + " into " + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = EPIC_ISSUE + ": updating versions for epic branch";

    private static final String COMMIT_MESSAGE_REVERT_VERSION = EPIC_ISSUE
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String PROMPT_MERGE_WITHOUT_UPDATE = "Base branch '" + MASTER_BRANCH
            + "' has changes that are not yet included in epic branch '" + EPIC_BRANCH
            + "'. If you continue it will be tryed to merge the changes."
            + " But it is strongly recomended to run 'mvn flow:epic-update' first and then run"
            + " 'mvn flow:epic-finish' again. Are you sure you want to continue?";

    private static final String PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE = "Base branch '" + MAINTENANCE_BRANCH
            + "' has changes that are not yet included in epic branch '" + EPIC_BRANCH
            + "'. If you continue it will be tryed to merge the changes."
            + " But it is strongly recomended to run 'mvn flow:epic-update' first and then run"
            + " 'mvn flow:epic-finish' again. Are you sure you want to continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:epic-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:epic-finish' again. "
                    + "Do NOT run 'git merge --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:epic-finish' to continue epic finish process\\E");

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
        MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecuteOnEpicBranchOneEpicBranch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
        assertArtifactNotInstalled();
    }

    private void assertEpicFinishedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnEpicBranchStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);

        verifyZeroInteractions(promptControllerMock);
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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositories();

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    private void assertNoChanges() throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories();
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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositoriesExceptCommitedTestfile();

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoEpicBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "There are no epic branches in your repository.",
                "Please start an epic first.", "'mvn flow:epic-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteOnEpicBranchTwoEpicBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH_2, OTHER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH_2, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoEpicBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "2. " + EPIC_BRANCH_2 + LS
                + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH_2, OTHER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH_2, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithBatchModeOnEpicBranch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:epic-finish' can be executed only on an epic branch.",
                "Please switch to an epic branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the epic branch",
                "'mvn flow:epic-finish' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteTychoBuildAndSkipTestProjectFalse() throws Exception {
        // set up
        Properties userPropertiesForEpicStart = new Properties();
        userPropertiesForEpicStart.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME, userPropertiesForEpicStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteKeepEpicBranchTrue() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.keepEpicBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMasterBranchOnSameCommitAsMaitenanceBranch()
            throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMasterBranchOnSameCommitAsMaitenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranchOnSameCommitAsMasterBranch() throws Exception {
        // set up
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, TestProjects.BASIC.version);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, OTHER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMainteanceBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, OTHER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteEpicStartedOnMaintenanceBranchThatIsNotAvailableLocally() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        final String COMMIT_MESSAGE_MAINTENACE_TESTFILE = "MAINTEANCE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENACE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.push(repositorySet);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MAINTENANCE_BRANCH, MAINTENANCE_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MAINTENANCE_BRANCH, MAINTENANCE_BRANCH,
                CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENACE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteEpicWithoutChanges() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in epic branch '" + EPIC_BRANCH + "'.",
                "Delete the epic branch or commit some changes first.",
                "'mvn flow:epic-abort' to delete the epic branch",
                "'git add' and 'git commit' to commit some changes into epic branch "
                        + "and 'mvn flow:epic-finish' to run the epic finish again");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteCurrentLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteEpicBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteEpicBranchAheadOfLocalWithMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_LOCAL_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteBaseBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH + "' and then include these changes in the epic "
                        + "branch '" + EPIC_BRANCH + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'",
                "'git checkout " + EPIC_BRANCH + "' and 'mvn flow:epic-update' to include these changes in the epic "
                        + "branch '" + EPIC_BRANCH + "'");

        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicFinishedCorrectlyOffline();
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicFinishedCorrectlyOffline();
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicFinishedCorrectlyOffline();
    }

    private void assertEpicFinishedCorrectlyOffline() throws Exception {
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
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
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInBatchMode() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' has changes that are not yet included in epic branch '"
                        + EPIC_BRANCH + "'.",
                "Merge the changes into epic branch first in order to proceed.",
                "'mvn flow:epic-update' to merge the changes into epic branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' has changes that are not yet included in epic branch '"
                        + EPIC_BRANCH + "'.",
                "Merge the changes into epic branch first in order to proceed.",
                "'mvn flow:epic-update' to merge the changes into epic branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerYes() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerYesAndConflicts() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteContinueAfterMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of epic finish aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after merge.\nGit error message:\n\\E.*",
                        "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:epic-finish' again. "
                                + "Do NOT run 'git merge --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
                        "\\Q'mvn flow:epic-finish' to continue epic finish process\\E"));
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranchStartedRemotely() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for epic branch '" + EPIC_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
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
                "Base branch '" + MASTER_BRANCH + "' for epic branch '" + EPIC_BRANCH + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
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
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for epic branch '" + EPIC_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
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
                "Base branch '" + MAINTENANCE_BRANCH + "' for epic branch '" + EPIC_BRANCH + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteWithMaintenanceBranchStartedAfterEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE_0 = "MASTER 0: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_1 = "MASTER 1: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_2 = "MASTER 2: Unit test dummy file commit";
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile0.txt", COMMIT_MESSAGE_MASTER_TESTFILE_0);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile1.txt", COMMIT_MESSAGE_MASTER_TESTFILE_1);
        git.createAndCommitTestfile(repositorySet, "master_testfile2.txt", COMMIT_MESSAGE_MASTER_TESTFILE_2);
        String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + EPIC_BRANCH + LS + "Choose epic branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE_0,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE_2, COMMIT_MESSAGE_MASTER_TESTFILE_1, COMMIT_MESSAGE_MASTER_TESTFILE_0);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteAllowFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("allowFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REVERT_VERSION,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

}
