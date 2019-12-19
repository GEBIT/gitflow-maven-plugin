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

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowEpicFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-finish";

    private static final String EPIC_ISSUE = BasicConstants.EXISTING_EPIC_ISSUE;

    private static final String EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;

    private static final String EPIC_VERSION = BasicConstants.EXISTING_EPIC_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + EPIC_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + BasicConstants.EPIC_ON_MAINTENANCE_BRANCH + " into " + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE;

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
            + "' has changes that are not yet included in epic branch '" + BasicConstants.EPIC_ON_MAINTENANCE_BRANCH
            + "'. If you continue it will be tryed to merge the changes."
            + " But it is strongly recomended to run 'mvn flow:epic-update' first and then run"
            + " 'mvn flow:epic-finish' again. Are you sure you want to continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:epic-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge failed.\nCONFLICT (added on " + MASTER_BRANCH + " and on " + EPIC_BRANCH + "): "
                    + GitExecution.TESTFILE_NAME,
            "Fix the merge conflicts and mark them as resolved by using 'git add'. "
                    + "After that, run 'mvn flow:epic-finish' again.\nDo NOT run 'git merge --continue'.",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:epic-finish' to continue epic finish process");

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, EPIC_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
    }

    @Test
    public void testExecuteOnEpicBranchStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
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
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", "no-epics/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "There are no epic branches in your repository.",
                "Please start an epic first.", "'mvn flow:epic-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnEpicBranchTwoEpicBranches() throws Exception {
        // set up
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + BasicConstants.FIRST_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.FIRST_EPIC_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.FIRST_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoEpicBranches() throws Exception {
        // set up
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + BasicConstants.FIRST_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.FIRST_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + BasicConstants.FIRST_EPIC_BRANCH + LS + "2. "
                + BasicConstants.SECOND_EPIC_BRANCH + LS + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.FIRST_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithBatchModeOnEpicBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        assertEpicFinishedCorrectly();
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
                "In non-interactive mode 'mvn flow:epic-finish' can be executed only on an epic branch.",
                "Please switch to an epic branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the epic branch",
                "'mvn flow:epic-finish' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSkipTestProjectFalse() throws Exception {
        // set up
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
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                BasicConstants.EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
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
    public void testExecuteInstallProjectTrueAndInstallProjectOnEpicFinishFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("flow.installProjectOnEpicFinish", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteInstallProjectFalseAndInstallProjectOnEpicFinishTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "false");
        userProperties.setProperty("flow.installProjectOnEpicFinish", "true");
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
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
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_EPIC_BRANCH + " into " + MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_EPIC_BRANCH + " into " + MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMasterBranchOnSameCommitAsMaitenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranchOnSameCommitAsMasterBranch() throws Exception {
        // set up
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_EPIC_BRANCH + " into " + USED_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE
                + ": reverting versions for development branch";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String USED_PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE = "Base branch '" + USED_MAINTENANCE_BRANCH
                + "' has changes that are not yet included in epic branch '" + USED_EPIC_BRANCH
                + "'. If you continue it will be tryed to merge the changes."
                + " But it is strongly recomended to run 'mvn flow:epic-update' first and then run"
                + " 'mvn flow:epic-finish' again. Are you sure you want to continue?";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(USED_PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix",
                BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verify(promptControllerMock).prompt(USED_PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_BRANCH, USED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH,
                USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE, USED_COMMIT_MESSAGE_REVERT_VERSION,
                BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String OTHER_BRANCH = "otherBranch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, OTHER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMainteanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_EPIC_BRANCH + " into " + MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        final String OTHER_BRANCH = "otherBranch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteEpicStartedOnMaintenanceBranchThatIsNotAvailableLocally() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        final String COMMIT_MESSAGE_MAINTENACE_TESTFILE = "MAINTEANCE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENACE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENACE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteEpicWithoutChanges() throws Exception {
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocal() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + USED_EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + USED_EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteCurrentLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
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
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_LOCAL_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteBaseBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
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
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
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
        assertGitFlowFailureException(result, "Remote and local epic branches '" + USED_EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + USED_EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInBatchMode() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerNo() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
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
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerYes() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNotUpdatedEpicBranchInInteractiveModeWithAnswerYesAndConflicts() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
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
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteContinueAfterMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
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
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_UPDATE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "There are unresolved conflicts after merge.\nCONFLICT (added on " + MASTER_BRANCH + " and on "
                                + EPIC_BRANCH + "): " + GitExecution.TESTFILE_NAME,
                        "Fix the merge conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:epic-finish' again.\nDo NOT run 'git merge --continue'.",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts "
                                + "as resolved",
                        "'mvn flow:epic-finish' to continue epic finish process"));
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranchStartedRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_EPIC_BRANCH + " into " + MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to finish";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
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
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_EPIC_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                USED_COMMIT_MESSAGE_REVERT_VERSION, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for epic branch '" + USED_EPIC_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
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
                "Base branch '" + MAINTENANCE_BRANCH + "' for epic branch '" + USED_EPIC_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteAllowFFTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.allowFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REVERT_VERSION,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "epic finish");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "epicFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointEpicBranch", EPIC_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "epic finish");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "epicFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointEpicBranch", EPIC_BRANCH);
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpointEpicBranch");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "epic finish");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "epicFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointEpicBranch", EPIC_BRANCH);
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpointEpicBranch");
    }

    @Test
    public void testExecuteWithBranchNameCurrentEpic() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotCurrentEpic() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotEpic() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Branch '" + OTHER_BRANCH + "' defined in 'branchName' property is not an epic branch.",
                "Please define an epic branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingEpic() throws Exception {
        // set up
        final String NON_EXISTING_EPIC_BRANCH = "epic/nonExisting";
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", NON_EXISTING_EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Epic branch '" + NON_EXISTING_EPIC_BRANCH + "' defined in 'branchName' property doesn't exist.",
                "Please define an existing epic branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingLocalEpic() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicFinishedCorrectly();
    }

}
