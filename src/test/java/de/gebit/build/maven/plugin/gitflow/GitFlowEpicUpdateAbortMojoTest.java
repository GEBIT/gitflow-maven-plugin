//
// GitFlowEpicUpdateAbortMojoTest.java
//
// Copyright (C) 2019
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;
import de.gebit.xmlxpath.XML;

/**
 * @author Volodja Medvid
 */
public class GitFlowEpicUpdateAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "epic-update-abort";

    private static final String PROJECT_VERSION = TestProjects.BASIC.version;

    private static final String EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;

    private static final String TMP_EPIC_BRANCH = "tmp-" + EPIC_BRANCH;

    private static final String EPIC_VERSION = BasicConstants.EXISTING_EPIC_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String MAINTENANCE_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String PROMPT_REBASE_ABORT = "You have a rebase in process on your current branch.\n"
            + "Are you sure you want to abort the epic update process?";

    private static final String PROMPT_MERGE_ABORT = "You have a merge in process on your current branch.\n"
            + "Are you sure you want to abort the epic update process?";

    private static final String PROMPT_INSTALL_PROJECT_ABORT = "You have an interrupted epic update process on your "
            + "current branch because project installation failed after update.\n"
            + "Are you sure you want to abort the epic update process and rollback the rebase/merge?";

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
    public void testExecuteNoReabseAndMergeInProcess() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "No interrupted epic update process detected. Nothing to abort.", null);
    }

    private void assertMissingAllLocalConfigValuesForBranch(String branch) {
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "baseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldEpicHEAD");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldEpicVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, branch, "oldVersionChangeCommit");
    }

    @Test
    public void testExecuteReabseOntoInProcess() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_TMP_EPIC_BRANCH = "tmp-" + USED_EPIC_BRANCH;
        final String USED_EPIC_VERSION = BasicConstants.SINGLE_EPIC_VERSION;
        final String USED_COMMIT_MESSAGE_SET_VERSION = BasicConstants.SINGLE_EPIC_VERSION_COMMIT_MESSAGE;
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentEpicAndMasterWithConflicts(USED_EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        final String EXPECTED_EPIC_HEAD = git.currentCommit(repositorySet);
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH, USED_TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), USED_EPIC_VERSION);
        git.assertCurrentCommit(repositorySet, EXPECTED_EPIC_HEAD);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, USED_EPIC_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(USED_COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(USED_EPIC_BRANCH);
    }

    private String createDivergentEpicAndMasterWithConflicts() throws Exception, IOException, GitAPIException {
        return createDivergentEpicAndMasterWithConflicts(EPIC_BRANCH);
    }

    private String createDivergentEpicAndMasterWithConflicts(String epicBranch)
            throws Exception, IOException, GitAPIException {
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, epicBranch);
        String versionChangeCommit = git.currentCommit(repositorySet);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        return versionChangeCommit;
    }

    private String createDivergentEpicAndMaintenanceWithConflicts(String epicBranch)
            throws Exception, IOException, GitAPIException {
        return createDivergentEpicAndMaintenanceWithConflicts(epicBranch, MAINTENANCE_BRANCH);
    }

    private String createDivergentEpicAndMaintenanceWithConflicts(String epicBranch, String maintenanceBranch)
            throws Exception, IOException, GitAPIException {
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, maintenanceBranch);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, epicBranch);
        String versionChangeCommit = git.currentCommit(repositorySet);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_EPIC_TESTFILE);
        return versionChangeCommit;
    }

    @Test
    public void testExecuteReabseOntoInProcessAndPromptAnswerNo() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.SINGLE_EPIC_BRANCH;
        final String USED_TMP_EPIC_BRANCH = "tmp-" + USED_EPIC_BRANCH;
        createDivergentEpicAndMasterWithConflicts(USED_EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX);
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH, USED_TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting epic update process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH, USED_TMP_EPIC_BRANCH);

        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
    }

    @Test
    public void testExecuteReabseInProcess() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH;
        final String USED_TMP_EPIC_BRANCH = "tmp-" + USED_EPIC_BRANCH;
        createDivergentEpicAndMaintenanceWithConflicts(USED_EPIC_BRANCH);
        final String EXPECTED_EPIC_HEAD = git.currentCommit(repositorySet);
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, null);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_VERSION);
        git.assertCurrentCommit(repositorySet, EXPECTED_EPIC_HEAD);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, USED_EPIC_BRANCH);
        assertEquals(MAINTENANCE_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(USED_EPIC_BRANCH);
    }

    @Test
    public void testExecuteReabseInProcessAndPromptAnswerNo() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH;
        final String USED_TMP_EPIC_BRANCH = "tmp-" + USED_EPIC_BRANCH;
        createDivergentEpicAndMaintenanceWithConflicts(USED_EPIC_BRANCH);
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, null);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting epic update process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_TMP_EPIC_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_EPIC_BRANCH, "oldVersionChangeCommit");
    }

    @Test
    public void testExecuteMergeInProcess() throws Exception {
        // set up
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentEpicAndMasterWithConflicts();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        final String EXPECTED_EPIC_HEAD = git.currentCommit(repositorySet);
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCurrentCommit(repositorySet, EXPECTED_EPIC_HEAD);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(EPIC_BRANCH);
    }

    @Test
    public void testExecuteMergeInProcessAndPromptAnswerNo() throws Exception {
        // set up
        createDivergentEpicAndMasterWithConflicts();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting epic update process aborted by user.", null);

        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
    }

    @Test
    public void testExecuteMergeInProcessAndNewMasterVersion() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "MASTER: update version";
        final String EXPECTED_VERSION_CHANGE_COMMIT = createDivergentEpicAndMasterWithConflicts();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        final String EXPECTED_EPIC_HEAD = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertTrue("an exception expected because of rebase conflict", result.hasExceptions());
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCurrentCommit(repositorySet, EXPECTED_EPIC_HEAD);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(EPIC_BRANCH);
    }

    @Test
    public void testExecuteAfterInstallProjectFailed() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "MASTER: invalid java file and new version";
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        final String EXPECTED_EPIC_HEAD = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertInstallProjectFailureException(result, "epic-update", EPIC_BRANCH, "epic update");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        git.assertCurrentCommit(repositorySet, EXPECTED_EPIC_HEAD);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));

        assertMissingAllLocalConfigValuesForBranch(EPIC_BRANCH);
    }

    private void replaceProjectVersion(String newVersion) throws IOException {
        File pom = new File(repositorySet.getWorkingDirectory(), "pom.xml");
        XML pomXML = XML.load(pom);
        pomXML.setValue("/project/version", newVersion);
        pomXML.setValue("/project/properties/version.build", newVersion);
        pomXML.store();
    }

    @Test
    public void testExecuteAfterInstallProjectFailedAndPromptAnswerNo() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "MASTER: invalid java file and new version";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertInstallProjectFailureException(result, "epic-update", EPIC_BRANCH, "epic update");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Aborting epic update process aborted by user.", null);
    }

    @Test
    public void testExecuteAfterInstallProjectFailedAndPushedManually() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER = "MASTER: invalid java file and new version";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceProjectVersion(NEW_MASTER_VERSION);
        git.createTestfile(repositorySet, "src/main/java/InvalidJavaFile.java");
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateEpicWithMerge", "true");
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = ExecutorHelper.executeEpicUpdateWithResult(this, repositorySet, userProperties);
        assertInstallProjectFailureException(result, "epic-update", EPIC_BRANCH, "epic update");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newBaseVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newStartCommitMessage");
        git.assertBranchLocalConfigValueMissing(repositorySet, EPIC_BRANCH, "newVersionChangeCommit");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicHEAD");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldEpicVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldBaseVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldStartCommitMessage");
        git.assertBranchLocalConfigValueExists(repositorySet, EPIC_BRANCH, "oldVersionChangeCommit");
        git.assertExistingLocalBranches(repositorySet, EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_EPIC_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, EPIC_BRANCH, "breakpoint", "epicUpdate.cleanInstall");
        git.push(repositorySet, true);
        when(promptControllerMock.prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_INSTALL_PROJECT_ABORT, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "The state of current local and remote branches is unexpected for an interrupted epic update "
                        + "process.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteOnNonEpicBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "No interrupted epic update process detected. Nothing to abort.", null);
    }

}
