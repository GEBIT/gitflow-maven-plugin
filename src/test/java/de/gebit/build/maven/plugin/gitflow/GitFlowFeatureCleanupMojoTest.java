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

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureCleanupMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-cleanup";

    private static final String DEPRECATED_GOAL = "feature-rebase-cleanup";

    private static final String FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.SINGLE_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_TESTFILE_MODIFIED = "Unit test dummy file modified";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String SQUASH_COMMIT_MESSAGE = "SQUASH: first line\\n\\nsecond line\\nthird line";

    private static final String PREPARED_SQUASH_MESSAGE = prepareSquashMessage(SQUASH_COMMIT_MESSAGE);

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT = "Feature branches:" + LS + "1. " + FEATURE_BRANCH
            + LS + "Choose feature branch to clean up";

    private static final String PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_SELECT = "Feature branches:"
            + LS + "1. " + BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH + LS
            + "Choose feature branch to clean up";

    private static final String PROMPT_REBASE_CONTINUE = "You have an interactive rebase in process on your current "
            + "branch. If you run 'mvn flow:feature-cleanup' before and rebase was paused or had conflicts you "
            + "can continue. In other case it is better to clarify the reason of rebase in process. Continue?";

    private RepositorySet repositorySet;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
        userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);

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
        addTwoCommitsToFeatureBranch();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteWithDeprecatedGoal() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), DEPRECATED_GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    private void addTwoCommitsToFeatureBranch() throws Exception, GitAPIException, IOException {
        addTwoCommitsToFeatureBranch(null);
    }

    private void addTwoCommitsToFeatureBranch(String featureBranch) throws Exception, GitAPIException, IOException {
        if (featureBranch != null) {
            git.switchToBranch(repositorySet, featureBranch);
        }
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("pick", "fixup");
    }

    private void assertCleanedUpCorrectly() throws GitAPIException, IOException, FileNotFoundException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        assertRebasedCorrectly(FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    private void assertRebasedCorrectly(String featureBranch, String commitMessageSetVersion)
            throws GitAPIException, IOException, FileNotFoundException {
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, featureBranch, featureBranch);
        git.assertCommitsInLocalBranch(repositorySet, featureBranch, COMMIT_MESSAGE_FOR_TESTFILE,
                commitMessageSetVersion);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
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
        // set up
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
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
        addTwoCommitsToFeatureBranch(BasicConstants.FIRST_FEATURE_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FIRST_FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        assertRebasedCorrectly(BasicConstants.FIRST_FEATURE_BRANCH,
                BasicConstants.FIRST_FEATURE_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        addTwoCommitsToFeatureBranch(BasicConstants.FIRST_FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + BasicConstants.FIRST_FEATURE_BRANCH + LS + "2. "
                + BasicConstants.SECOND_FEATURE_BRANCH + LS + "Choose feature branch to clean up";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FIRST_FEATURE_VERSION);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        assertRebasedCorrectly(BasicConstants.FIRST_FEATURE_BRANCH,
                BasicConstants.FIRST_FEATURE_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "'mvn flow:feature-cleanup' can be executed in non-interactive mode only to squash all feature "
                        + "commits.",
                "Please either run in interactive mode or enable commit squashing to squash all feature commits.",
                "'mvn flow:feature-cleanup' to run in interactive mode",
                "'mvn flow:feature-cleanup -B -Dflow.squash=true -DsquashMessage=XXXX' to squash all feature "
                        + "commits");
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:feature-cleanup' can be executed only on a feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the feature branch",
                "'mvn flow:feature-cleanup' to run in interactive mode");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteInstallProjectTrueAndInstallProjectOnFeatureCleanupFalse() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("flow.installProjectOnFeatureCleanup", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteInstallProjectFalseAndInstallProjectOnFeatureCleanupTrue() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.installProject", "false");
        userProperties.setProperty("flow.installProjectOnFeatureCleanup", "true");
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
        addTwoCommitsToFeatureBranch();
        git.push(repositorySet);
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
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_COMMIT_BRANCH;
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH;
        addTwoCommitsToFeatureBranch(USED_FEATURE_BRANCH);
        git.switchToBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_SELECT,
                Arrays.asList("1"))).thenReturn("1");
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_SELECT,
                Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, USED_MASTER_BRANCH, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);

        assertVersionsInPom(repositorySet.getWorkingDirectory(),
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_COMMIT_BRANCH;
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH;
        addTwoCommitsToFeatureBranch(USED_FEATURE_BRANCH);
        git.deleteLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_MAINTENANCE_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, USED_MASTER_BRANCH, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, USED_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, BasicConstants.MASTER_WITH_COMMIT_MESSAGE);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);

        assertVersionsInPom(repositorySet.getWorkingDirectory(),
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
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
        addTwoCommitsToFeatureBranch();
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
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
        addTwoCommitsToFeatureBranch();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        final String FEATURE_TESTFILE_NAME = "feature_testfile.txt";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, FEATURE_TESTFILE_NAME,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup");
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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
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
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocal() throws Exception {
        addTwoCommitsToFeatureBranch();
        git.remoteCreateTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        final String COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE = "REMOTE: Unit test dummy file commit";
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.remoteCreateTestfile(repositorySet, "remote_testfile.txt", COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteFeatureStartetWithoutVersionChange() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        addTwoCommitsToFeatureBranch(USED_FEATURE_BRANCH);
        userProperties.clear();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithRebasePaused() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("pick", "edit");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithConflictOnRebase() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithErrorOnInteractiveRebase() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.prepareErrorWhileUsingGitEditor(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
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
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
        git.assertTestfileContent(repositorySet, FEATURE_TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterRebasePausedAndPromptAnswerNo() throws Exception {
        // set up
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "fixup", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature clean up aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterRebasePausedTwice() throws Exception {
        // set up
        final String FEATURE_TESTFILE_NAME = "feature-testfile.txt";
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("pick", "edit", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(FEATURE_TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(FEATURE_TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("edit", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(FEATURE_TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(FEATURE_TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.defineRebaseTodoCommands("drop", "pick", "edit");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureException(result, "Interactive rebase is paused.",
                "Perform your changes and run 'mvn flow:feature-cleanup' again in order to proceed. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertNoRebaseInProcess(repositorySet);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, FEATURE_TESTFILE_NAME, COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.modifyTestfile(repositorySet, FEATURE_TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick", "drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE_MODIFIED, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        git.prepareErrorWhileUsingGitEditor(repositorySet);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of interactive rebase failed.",
                "Fix the problem described above or consult a gitflow expert on how to fix this!");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedConflictOnRebase() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.defineRebaseTodoCommands("drop", "pick");

        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        assertGitFlowFailureException(result, "Automatic rebase after interaction failed beacause of conflicts.",
                "Fix the rebase conflicts and mark them as resolved. After that, run "
                        + "'mvn flow:feature-cleanup' again. Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process",
                "'git rebase --abort' to abort feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are unresolved conflicts after rebase.",
                "Fix the rebase conflicts and mark them as resolved. "
                        + "After that, run 'mvn flow:feature-cleanup' again. "
                        + "Do NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts "
                        + "as resolved",
                "'mvn flow:feature-cleanup' to continue feature clean up process");
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteBaseBranchNotExistingRemotely() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseBranchNotExistingLocally() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, MASTER_BRANCH);

        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteBaseBranchNotExistingRemotelyAndLocally() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base commit for feature branch '" + FEATURE_BRANCH + "' can't be estimated because the base branch '"
                        + MASTER_BRANCH + "' doesn't exist.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteBaseBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
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
                "Base commit for feature branch '" + FEATURE_BRANCH + "' can't be estimated because the base branch '"
                        + MASTER_BRANCH + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteCentralBranchConfigMissing() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "An upgrade of central branch config is required in order to use new version of gitflow!",
                "Please run 'mvn flow:upgrade' first to upgrade central branch config.",
                "'mvn flow:upgrade' to upgrade central branch config");
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.defineRebaseTodoCommands("pick", "fixup", "pick");
        userProperties.setProperty("flow.installProject", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature cleanup");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertTestfileContentModified(repositorySet);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureCleanup.cleanInstall");
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.defineRebaseTodoCommands("pick", "fixup", "pick");
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature cleanup");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureCleanup.cleanInstall");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertTestfileContentModified(repositorySet);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TESTFILE_MODIFIED);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.defineRebaseTodoCommands("pick", "fixup", "pick");
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature cleanup");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureCleanup.cleanInstall");
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);

        git.assertCommitMessagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_TESTFILE_MODIFIED, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertTestfileContentModified(repositorySet);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteCleanupSquash() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        userProperties.setProperty("squashMessage", SQUASH_COMMIT_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, PREPARED_SQUASH_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCleanupSquashInBatchMode() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        userProperties.setProperty("squashMessage", SQUASH_COMMIT_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, PREPARED_SQUASH_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCleanupSquashWithoutSquashMessage() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        git.defineCommitEditMsg(PREPARED_SQUASH_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, PREPARED_SQUASH_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCleanupSquashWithoutSquashMessageAndEnterdEmptyMessage() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        git.defineCommitEditMsg("");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, new GitFlowFailureInfo(
                "\\QFailed to squash feature commits.\nReason: \\E.*",
                "\\QPlease try again with a valid squash commit message or consult a gitflow expert on how to fix this!"
                        + "\\E"));
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCleanupSquashWithEmptySquashMessage() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        userProperties.setProperty("squashMessage", "");
        git.defineCommitEditMsg(PREPARED_SQUASH_MESSAGE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, PREPARED_SQUASH_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteCleanupSquashWithoutSquashMessageInBatchMode() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("flow.cleanupSquash", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo(
                        "Feature commits can't be squashed without squashMessage in non-interactive mode.",
                        "Please either provide squashMessage or run in interactive mode.",
                        "'mvn flow:feature-cleanup -B -Dflow.squash=true -DsquashMessage=XXXX' to squash all "
                                + "feature commits using squash commit message",
                        "'mvn flow:feature-cleanup -Dflow.squash=true' to run in interactive mode"));
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_TESTFILE_MODIFIED,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteWithBranchNameCurrentFeature() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotCurrentFeature() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotFeature() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        userProperties.setProperty("branchName", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Branch '" + OTHER_BRANCH + "' defined in 'branchName' property is not a feature branch.",
                "Please define a feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingFeature() throws Exception {
        // set up
        final String NON_EXISTING_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX + "nonExisting";
        userProperties.setProperty("branchName", NON_EXISTING_FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Feature branch '" + NON_EXISTING_FEATURE_BRANCH + "' defined in 'branchName' property doesn't exist.",
                "Please define an existing feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingLocalFeature() throws Exception {
        // set up
        addTwoCommitsToFeatureBranch();
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, FEATURE_BRANCH);
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertCleanedUpCorrectly();
    }

}
