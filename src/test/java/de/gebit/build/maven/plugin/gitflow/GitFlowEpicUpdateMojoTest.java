//
// GitFlowEpicUpdateMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

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
public class GitFlowEpicUpdateMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-update";

    private static final String EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;

    private static final String PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. "
            + BasicConstants.SINGLE_EPIC_BRANCH + LS + "Choose epic branch to update";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:epic-update' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final String EPIC_VERSION = BasicConstants.EXISTING_EPIC_VERSION;

    private static final String MAINTENANCE_EPIC_VERSION = BasicConstants.EPIC_ON_MAINTENANCE_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + MASTER_BRANCH + " into " + EPIC_BRANCH;

    private static final String COMMIT_MESSAGE_MARGE_MAINTENANCE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + MAINTENANCE_BRANCH + " into " + BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved.\nIMPORTANT: be sure not to update the version in "
                    + "epic branch while resolving conflicts!\nAfter that, run 'mvn flow:epic-update' again. "
                    + "Do NOT run 'git merge --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:epic-update' to continue epic update process\\E");

    private static final String PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER = "The current commit on " + MASTER_BRANCH
            + " is not integrated. Update epic branch to the last integrated commit (" + INTEGRATION_MASTER_BRANCH
            + ")?";

    private static final String PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE = "The current commit on "
            + MAINTENANCE_BRANCH + " is not integrated. Update epic branch to the last integrated commit ("
            + INTEGRATION_MAINTENANCE_BRANCH + ")?";

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
        prepareEpicBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
        assertArtifactNotInstalled();
    }

    private void prepareEpicBranchDivergentFromMaster() throws Exception {
        prepareEpicBranchDivergentFromMaster(EPIC_BRANCH);
    }

    private void prepareEpicBranchDivergentFromMaster(String epicBranch) throws Exception {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, epicBranch);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
    }

    private void prepareEpicBranchDivergentFromMaintenance() throws Exception {
        prepareEpicBranchDivergentFromMaintenance(BasicConstants.EPIC_ON_MAINTENANCE_BRANCH);
    }

    private void prepareEpicBranchDivergentFromMaintenance(String epicBranch) throws Exception {
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, epicBranch);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
    }

    private void assertEpicMergedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        assertEpicMergedCorrectly(EPIC_BRANCH, EPIC_VERSION, COMMIT_MESSAGE_SET_VERSION);
    }

    private void assertEpicMergedCorrectly(String epicBranch, String epicBranchVersion,
            String epicBranchCommitMessageSetVersion) throws ComponentLookupException, GitAPIException, IOException {
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + epicBranch;
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, epicBranch);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, epicBranch, epicBranch);
        git.assertCommitsInLocalBranch(repositorySet, epicBranch, COMMIT_MESSAGE_EPIC_TESTFILE,
                epicBranchCommitMessageSetVersion, COMMIT_MESSAGE_MASTER_TESTFILE, USED_COMMIT_MESSAGE_MARGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), epicBranchVersion);
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

        Set<String> addedFiles = git.status(repositorySet).getAdded();
        assertEquals("number of added files is wrong", 1, addedFiles.size());
        assertEquals("added file is wrong", GitExecution.TESTFILE_NAME, addedFiles.iterator().next());
        git.assertTestfileContent(repositorySet);
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
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

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
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
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicMergedCorrectly(USED_EPIC_BRANCH, BasicConstants.SINGLE_EPIC_VERSION,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteOnEpicBranchTwoEpicBranches() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.FIRST_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.FIRST_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FIRST_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchTwoEpicBranchesAndOtherBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.FIRST_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        final String PROMPT_MESSAGE = "Epic branches:" + LS + "1. " + BasicConstants.FIRST_EPIC_BRANCH + LS + "2. "
                + BasicConstants.SECOND_EPIC_BRANCH + LS + "Choose epic branch to update";
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
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
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.FIRST_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FIRST_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchOneEpicBranchStartedRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicMergedCorrectly(USED_EPIC_BRANCH, BasicConstants.SINGLE_EPIC_VERSION,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteWithBatchModeOnEpicBranch() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertEpicMergedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:epic-update' can be executed only on an epic branch.",
                "Please switch to an epic branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the epic branch",
                "'mvn flow:epic-update' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to update";
        final String USED_COMMIT_MESSAGE_MARGE_MAINTENANCE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + MAINTENANCE_BRANCH + " into " + USED_EPIC_BRANCH;
        prepareEpicBranchDivergentFromMaintenance(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to update";
        final String USED_COMMIT_MESSAGE_MARGE_MAINTENANCE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + MAINTENANCE_BRANCH + " into " + USED_EPIC_BRANCH;
        prepareEpicBranchDivergentFromMaintenance(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchEpicStartedOnMaintenanceBranchOnSameCommitAsMasterBranch() throws Exception {
        // set up
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to update";
        final String USED_COMMIT_MESSAGE_MARGE_MAINTENANCE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_MAINTENANCE_BRANCH + " into " + USED_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix",
                BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_BRANCH, USED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(),
                BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_VERSION);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        prepareEpicBranchDivergentFromMaster(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnOtherBranchEpicStartedOnMainteanceBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH;
        final String USED_PROMPT_MESSAGE_ONE_EPIC_SELECT = "Epic branches:" + LS + "1. " + USED_EPIC_BRANCH + LS
                + "Choose epic branch to update";
        final String USED_COMMIT_MESSAGE_MARGE_MAINTENANCE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + MAINTENANCE_BRANCH + " into " + USED_EPIC_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        prepareEpicBranchDivergentFromMaintenance(USED_EPIC_BRANCH);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(USED_PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteEpicStartedOnMaintenanceBranchThatIsNotAvailableLocally() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        prepareEpicBranchDivergentFromMaintenance(USED_EPIC_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeIntegerated(repositorySet, INTEGRATION_MASTER_BRANCH);
        prepareEpicBranchDivergentFromMaster();
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MASTER_BRANCH,
                INTEGRATION_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMasterIntegrationBranchAndRebaseOnIntegrated() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeIntegerated(repositorySet, INTEGRATION_MASTER_BRANCH);
        prepareEpicBranchDivergentFromMaster();
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MASTER_BRANCH,
                INTEGRATION_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        ExecutorHelper.executeIntegerated(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                INTEGRATION_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteOnEpicBranchEpicStartedOnMaintenanceIntegrationBranchAndRebaseOnIntegrated()
            throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        ExecutorHelper.executeIntegerated(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                INTEGRATION_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteMasterWithoutChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicMergedCorrectly(USED_EPIC_BRANCH, BasicConstants.SINGLE_EPIC_VERSION,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocal() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "epic_testfile.txt",
                COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicMergedCorrectly(USED_EPIC_BRANCH, BasicConstants.SINGLE_EPIC_VERSION,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + USED_EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + USED_EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteSelectedLocalEpicBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MASTER_TESTFILE,
                USED_COMMIT_MESSAGE_MARGE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "epic_testfile.txt",
                COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_EPIC_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteSelectedRemoteEpicBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch " + MASTER_BRANCH
                + " into " + USED_EPIC_BRANCH;
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "epic_testfile.txt",
                COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SINGLE_EPIC_VERSION);
    }

    @Test
    public void testExecuteSelectedEpicBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, USED_EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"))).thenReturn("1");
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
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_EPIC_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + USED_EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + USED_EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteCurrentLocalEpicBranchAheadOfRemote() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteEpicBranchAheadOfLocal() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
    }

    @Test
    public void testExecuteCurrentEpicBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, EPIC_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local epic branches '" + EPIC_BRANCH + "' diverge.",
                "Rebase or merge the changes in local epic branch '" + EPIC_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemote() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Local base branch '" + MASTER_BRANCH + "' is ahead of remote branch. Pushing of the updated epic "
                        + "branch will create an inconsistent state in remote repository.",
                "Push the base branch '" + MASTER_BRANCH + "' first or set 'pushRemote' parameter to false in order to "
                        + "avoid inconsistent state in remote repository.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemoteAndPushRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocal() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'");
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
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
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemoteAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
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
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'");
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' doesn't exist remotely. Pushing of the updated epic branch "
                        + "will create an inconsistent state in remote repository.",
                "Push the base branch '" + MASTER_BRANCH + "' first or set 'pushRemote' parameter to false in order to "
                        + "avoid inconsistent state in remote repository.");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissingAndPushRemoteFalse() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        prepareEpicBranchDivergentFromMaster();
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
        prepareEpicBranchDivergentFromMaster();
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
        prepareEpicBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        prepareEpicBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        prepareEpicBranchDivergentFromMaintenance();
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
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_MARGE_MAINTENANCE,
                COMMIT_MESSAGE_EPIC_TESTFILE, BasicConstants.EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_EPIC_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_ON_MAINTENANCE_BRANCH;
        prepareEpicBranchDivergentFromMaintenance();
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
        prepareEpicBranchDivergentFromMaintenance();
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
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertEpicMergedCorrectly();
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of epic update aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after merge.\nGit error message:\n\\E.*",
                        "\\QFix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:epic-update' again. Do NOT run 'git merge --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts "
                                + "as resolved\\E",
                        "\\Q'mvn flow:epic-update' to continue epic update process\\E"));
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareEpicBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to execute 'mvn clean install' on the project on epic branch after update.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-update' again in order to continue.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_EPIC_TESTFILE, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_MARGE);
        git.assertCommitsInRemoteBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        prepareEpicBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to execute 'mvn clean install' on the project on epic branch after update.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-update' again in order to continue.");
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_EPIC_TESTFILE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_MARGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareEpicBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to execute 'mvn clean install' on the project on epic branch after update.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:epic-update' again in order to continue.");
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_EPIC_TESTFILE, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_MARGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "breakpoint");
    }

}
