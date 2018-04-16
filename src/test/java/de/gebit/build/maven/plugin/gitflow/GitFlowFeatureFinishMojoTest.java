//
// GitFlowFeatureFinishMojoTest.java
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
import org.junit.Ignore;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME = FEATURE_ISSUE + "-someDescription";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_ISSUE + "-SNAPSHOT";

    private static final String FEATURE_NUMBER_2 = TestProjects.BASIC.jiraProject + "-4711";

    private static final String FEATURE_BRANCH_2 = "feature/" + FEATURE_NUMBER_2;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-4711";

    private static final String EPIC_NAME = EPIC_ISSUE + "-someDescription";

    private static final String EPIC_BRANCH = "epic/" + EPIC_NAME;

    private static final String EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + EPIC_ISSUE + "-SNAPSHOT";

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + FEATURE_BRANCH + " into " + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_EPIC = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH + " into " + EPIC_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_ISSUE + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_REVERT_VERSION = FEATURE_ISSUE
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_EPIC = EPIC_ISSUE
            + ": updating versions for epic branch";

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String PROMPT_MERGE_WITHOUT_REBASE = "Base branch '" + MASTER_BRANCH
            + "' has changes that are not yet" + " included in feature branch '" + FEATURE_BRANCH
            + "'. If you continue it will be tryed to merge the changes."
            + " But it is strongly recomended to run 'mvn flow:feature-rebase' first and then run"
            + " 'mvn flow:feature-finish' again. Are you sure you want to continue?";

    private static final String PROMPT_MAINTENANCE_MERGE_WITHOUT_REBASE = "Base branch '" + MAINTENANCE_BRANCH
            + "' has changes that are not yet included in feature branch '" + FEATURE_BRANCH
            + "'. If you continue it will be tryed to merge the changes."
            + " But it is strongly recomended to run 'mvn flow:feature-rebase' first and then run"
            + " 'mvn flow:feature-finish' again. Are you sure you want to continue?";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic rebase failed.\nGit error message:\n\\E.*",
            "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again. "
                    + "Do NOT run 'git rebase --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-finish' to continue feature finish process\\E");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again. "
                    + "Do NOT run 'git merge --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-finish' to continue feature finish process\\E");

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
    public void testExecuteOnFeatureBranchOneFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
        assertArtifactNotInstalled();
    }

    private void assertFeatureFinishedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnMaintenanceBranch_GBLD283() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
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

        Set<String> addedFiles = git.status(repositorySet).getAdded();
        assertEquals("number of added files is wrong", 1, addedFiles.size());
        assertEquals("added file is wrong", GitExecution.TESTFILE_NAME, addedFiles.iterator().next());
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

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteOnFeatureBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS + "2. " + FEATURE_BRANCH_2 + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:feature-finish' can be executed only on a feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the feature branch",
                "'mvn flow:feature-finish' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteTychoBuildAndSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteKeepFeatureBranchTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.keepFeatureBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalseAndFeatureNameWithDescription() throws Exception {
        // set up
        final String FEATURE_NAME_WITH_DESCRIPTION = FEATURE_NAME + "-someDescription";
        final String COMMIT_MESSAGE_MERGE_WITH_DESCRIPTION = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch feature/" + FEATURE_NAME_WITH_DESCRIPTION;
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME_WITH_DESCRIPTION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_WITH_DESCRIPTION,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalseAndVersionWithoutFeatureName() throws Exception {
        // set up
        Properties properties = new Properties();
        properties.setProperty("flow.skipFeatureVersion", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, properties);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndVersionWithoutFeatureName() throws Exception {
        // set up
        Properties properties = new Properties();
        properties.setProperty("flow.skipFeatureVersion", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, properties);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndHasMergeCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        String COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH = "Merging master to feature branch";
        git.mergeAndCommit(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch()
            throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteOnMasterBranchFinishStartedOnMaintenanceBranchOnSameCommitAsMasterBranch() throws Exception {
        // set up
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, TestProjects.BASIC.version);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, OTHER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMainteanceBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceIntegrationBranch() throws Exception {
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
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENACE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteFeatureWithoutChanges() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in feature branch '" + FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch "
                        + "and 'mvn flow:feature-finish' to run the feature finish again");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteFeatureWithoutChangesAndRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in feature branch '" + FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch "
                        + "and 'mvn flow:feature-finish' to run the feature finish again");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalWithMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_LOCAL_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteBaseBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH
                        + "' and then include these changes in the feature branch '" + FEATURE_BRANCH
                        + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'",
                "'git checkout " + FEATURE_BRANCH
                        + "' and 'mvn flow:feature-rebase' to include these changes in the feature branch '"
                        + FEATURE_BRANCH + "'");

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureFinishedCorrectlyOffline();
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureFinishedCorrectlyOffline();
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureFinishedCorrectlyOffline();
    }

    private void assertFeatureFinishedCorrectlyOffline() throws Exception {
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
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
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithRemovingVersionCommitRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        ExecutorHelper.executeSetVersion(this, repositorySet, "2.0.0-SNAPSHOT");
        git.commitAll(repositorySet, "new version");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
    }

    @Test
    public void testExecuteContinueRebaseAfterResolvedRemovingVersionCommitRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath("pom.xml").call();
        repositorySet.getLocalRepoGit().add().addFilepattern("pom.xml").call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_NEW_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteContinueRebaseAfterRemovingVersionCommitRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath("pom.xml").call();
        repositorySet.getLocalRepoGit().add().addFilepattern("pom.xml").call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
    }

    @Test
    public void testExecuteContinueRebaseAfterNotResolvedRemovingVersionCommitRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath("pom.xml").call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after rebase.\nGit error message:\n\\E.*",
                        "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again. "
                                + "Do NOT run 'git rebase --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-finish' to continue feature finish process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInBatchMode() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' has changes that are not yet included in feature branch '"
                        + FEATURE_BRANCH + "'.",
                "Rebase the feature branch first in order to proceed.",
                "'mvn flow:feature-rebase' to rebase the feature branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' has changes that are not yet included in feature branch '"
                        + FEATURE_BRANCH + "'.",
                "Rebase the feature branch first in order to proceed.",
                "'mvn flow:feature-rebase' to rebase the feature branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerYes() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerYesAndConflicts() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteContinueAfterMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
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
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n")).thenReturn("y");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MERGE_WITHOUT_REBASE, Arrays.asList("y", "n"), "n");
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
                        "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again. "
                                + "Do NOT run 'git merge --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-finish' to continue feature finish process\\E"));
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to find base branch for feature branch '" + FEATURE_BRANCH
                        + "'. This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
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
        assertGitFlowFailureException(result, "Failed to find base branch for feature branch '" + FEATURE_BRANCH + "'.",
                "Set 'fetchRemote' parameter to true in order to search for base branch also in remote repository.");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
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
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to find base branch for feature branch '" + FEATURE_BRANCH
                        + "'. This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
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
        assertGitFlowFailureException(result, "Failed to find base branch for feature branch '" + FEATURE_BRANCH + "'.",
                "Set 'fetchRemote' parameter to true in order to search for base branch also in remote repository.");
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteWithMaintenanceBranchStartedAfterFeatureStartedOnMasterBranch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE_0 = "MASTER 0: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_1 = "MASTER 1: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_2 = "MASTER 2: Unit test dummy file commit";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile0.txt", COMMIT_MESSAGE_MASTER_TESTFILE_0);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile1.txt", COMMIT_MESSAGE_MASTER_TESTFILE_1);
        git.createAndCommitTestfile(repositorySet, "master_testfile2.txt", COMMIT_MESSAGE_MASTER_TESTFILE_2);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE_2, COMMIT_MESSAGE_MASTER_TESTFILE_1, COMMIT_MESSAGE_MASTER_TESTFILE_0);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranch() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersion() throws Exception {
        // set up
        final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";
        Properties userPropertiesForEpic = new Properties();
        userPropertiesForEpic.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME, userPropertiesForEpic);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.createAndCommitTestfile(repositorySet, "epic-testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_EPIC_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchAndRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutFeatureVersion() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        Properties userPropertiesForFeatureStart = new Properties();
        userPropertiesForFeatureStart.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userPropertiesForFeatureStart);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersionAndWithoutFeatureVersion()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";
        Properties userPropertiesForEpicAndFeatureStart = new Properties();
        userPropertiesForEpicAndFeatureStart.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME, userPropertiesForEpicAndFeatureStart);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.createAndCommitTestfile(repositorySet, "epic-testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userPropertiesForEpicAndFeatureStart);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_EPIC_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutFeatureVersionAndRebaseWithoutVersionChangeFalse()
            throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);
        Properties userPropertiesForFeatureStart = new Properties();
        userPropertiesForFeatureStart.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userPropertiesForFeatureStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersionAndWithoutFeatureVersionAndRebaseWithoutVersionChangeFalse()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";
        Properties userPropertiesForEpicAndFeatureStart = new Properties();
        userPropertiesForEpicAndFeatureStart.setProperty("flow.tychoBuild", "true");
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME, userPropertiesForEpicAndFeatureStart);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.createAndCommitTestfile(repositorySet, "epic-testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME, userPropertiesForEpicAndFeatureStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_MERGE_INTO_EPIC,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_EPIC_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
    }

}
