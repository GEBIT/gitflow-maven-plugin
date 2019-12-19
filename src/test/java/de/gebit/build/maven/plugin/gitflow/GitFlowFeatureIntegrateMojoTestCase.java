//
// GitFlowFeatureIntegrateMojoTestCase.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.io.ModelParseException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;
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
public class GitFlowFeatureIntegrateMojoTestCase extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-integrate";

    private static final String FEATURE_BRANCH_PREFIX = BasicConstants.TWO_FEATURE_BRANCHES_PREFIX;
    private static final String SOURCE_FEATURE_NAME = BasicConstants.FIRST_FEATURE_NAME;
    private static final String SOURCE_FEATURE_BRANCH = BasicConstants.FIRST_FEATURE_BRANCH;
    private static final String TARGET_FEATURE_NAME = BasicConstants.SECOND_FEATURE_NAME;
    private static final String TARGET_FEATURE_BRANCH = BasicConstants.SECOND_FEATURE_BRANCH;
    private static final String TMP_SOURCE_FEATURE_BRANCH = "tmp-" + SOURCE_FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SOURCE_TESTFILE = "SOURCE: Unit test dummy file commit";
    private static final String COMMIT_MESSAGE_TARGET_TESTFILE = "TARGET: Unit test dummy file commit";
    private static final String COMMIT_MESSAGE_REVERT_MODULE_VERSION = BasicConstants.FIRST_FEATURE_ISSUE
            + ": reverting versions for development branch";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. "
            + "If you run 'mvn flow:feature-integrate' before and rebase had conflicts you can continue. "
            + "In other case it is better to clarify the reason of rebase in process. Continue?";

    private RepositorySet repositorySet;
    private Properties userProperties = new Properties();

    @Before
    public void setUp() throws Exception {
        userProperties.setProperty("flow.featureBranchPrefix", FEATURE_BRANCH_PREFIX);
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, SOURCE_FEATURE_BRANCH);
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

    private void prepareSourceFeature() throws Exception {
        git.createAndCommitTestfile(repositorySet, "source_testfile.txt", COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
    }

    private void prepareTargetFeature() throws Exception {
        String currentBranch = git.currentBranch(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "target_testfile.txt", COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, currentBranch);
    }

    private void prepareFeatures() throws Exception {
        prepareSourceFeature();
        prepareTargetFeature();
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFeatureIntegratedSuccessfully();
    }

    protected void assertFeatureIntegratedSuccessfully()
            throws GitAPIException, IOException, ComponentLookupException, ModelParseException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactNotInstalled();

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, SOURCE_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
    }

    @Test
    public void testExecuteNotOnFeatureBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:feature-integrate' can be executed only on a feature branch that "
                        + "should be integrated into target feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the feature branch",
                "'mvn flow:feature-integrate' to run in interactive mode");
    }

    @Test
    public void testExecuteNoFeatureBranchesExceptCurrent() throws Exception {
        // set up
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        git.switchToBranch(repositorySet, BasicConstants.SINGLE_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "source_testfile.txt", COMMIT_MESSAGE_SOURCE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "There are no feature branches except source feature branch in your " + "repository.",
                "Please start a feature first which the source feature branch '" + BasicConstants.SINGLE_FEATURE_BRANCH
                        + "' should be integrated into.",
                "'mvn flow:feature-start'");
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, SOURCE_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local source feature branches '" + SOURCE_FEATURE_BRANCH + "' diverge.",
                "Rebase the changes in local feature branch '" + SOURCE_FEATURE_BRANCH + "' first.",
                "'git rebase' to rebase the changes in local feature branch");
    }

    @Test
    public void testExecuteSourceFeatureWithoutChanges() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "There are no real changes in current feature branch '" + SOURCE_FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch "
                        + "and 'mvn flow:feature-integrate' to run the feature integration again");
    }

    @Test
    public void testExecuteWithoutFeatureNameInBatchMode() throws Exception {
        // set up
        prepareSourceFeature();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Property 'featureName' or 'targetBranch' is required in non-interactive mode but was not set.",
                "Specify a target featureName or run in interactive mode.",
                "'mvn flow:feature-integrate -DfeatureName=XXX -B'", "'mvn flow:feature-integrate'");
    }

    @Test
    public void testExecuteWithBlankFeatureNameInBatchMode() throws Exception {
        // set up
        prepareSourceFeature();
        userProperties.setProperty("featureName", " ");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Property 'featureName' or 'targetBranch' is required in non-interactive mode but was not set.",
                "Specify a target featureName or run in interactive mode.",
                "'mvn flow:feature-integrate -DfeatureName=XXX -B'", "'mvn flow:feature-integrate'");
    }

    @Test
    public void testExecuteWithNonExistingTargetFeatureBranch() throws Exception {
        // set up
        final String NON_EXISTING_FEATURE_NAME = "non-existing-feature";
        final String NON_EXISTING_FEATURE_BRANCH = FEATURE_BRANCH_PREFIX + NON_EXISTING_FEATURE_NAME;
        prepareSourceFeature();
        userProperties.setProperty("featureName", NON_EXISTING_FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Target feature branch '" + NON_EXISTING_FEATURE_BRANCH + "' doesn't exist.",
                "Either provide featureName of an existing feature branch or run without 'featureName' in interactive "
                        + "mode.",
                "'mvn flow:feature-integrate' to run in intractive mode and select an existing target branch");
    }

    @Test
    public void testExecuteWithTargetFeatureSameAsCurrentFeature() throws Exception {
        // set up
        prepareSourceFeature();
        userProperties.setProperty("featureName", SOURCE_FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The target feature branch can not be the same as current feature branch.",
                "Please select a target feature branch different from current feature branch '" + SOURCE_FEATURE_BRANCH
                        + "'.",
                "'mvn flow:feature-integrate'");
    }

    @Test
    public void testExecuteTargetFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        prepareSourceFeature();
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, TARGET_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local target feature branches '" + TARGET_FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + TARGET_FEATURE_BRANCH + "' first.",
                "'git checkout " + TARGET_FEATURE_BRANCH + "' and 'git rebase' to rebase the changes in local "
                        + "target feature branch");
    }

    @Test
    public void testExecuteCurrentAndTargetFeatureBranchesHaveDifferentBaseBranches() throws Exception {
        // set up
        userProperties.remove("flow.featureBranchPrefix");
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_FEATURE_BRANCH);
        prepareSourceFeature();
        userProperties.setProperty("featureName", BasicConstants.FEATURE_ON_MAINTENANCE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The current and target feature branches have different base branches:\n" + "'"
                        + BasicConstants.EXISTING_FEATURE_BRANCH + "' -> '" + MASTER_BRANCH + "'\n" + "'"
                        + BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH + "' -> '"
                        + BasicConstants.EXISTING_MAINTENANCE_BRANCH + "'\n",
                "Please select a target feature branch that has the same base branch '" + MASTER_BRANCH
                        + "' as current feature branch.",
                "'mvn flow:feature-integrate'");
    }

    @Test
    public void testExecuteSourceFeatureBranchHasMergeCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH = "Merging master to feature branch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.mergeAndCommit(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH);
        git.push(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Source feature branch '" + SOURCE_FEATURE_BRANCH
                        + "' contains merge commits. Integration of this feature branch is not possible.",
                "Finish the source feature without integration.",
                "'mvn flow:feature-finish' to finish the feature and marge it to the development branch");
    }

    @Test
    public void testExecuteWithRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "Automatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                                + TARGET_FEATURE_BRANCH + "' failed.\nCONFLICT (added on " + TARGET_FEATURE_BRANCH
                                + " and on " + SOURCE_FEATURE_BRANCH + "): " + TESTFILE_NAME,
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-integrate' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-integrate' to continue feature integration process",
                        "'mvn flow:feature-integrate-abort' to abort feature integration process"));

        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TARGET_FEATURE_BRANCH,
                TMP_SOURCE_FEATURE_BRANCH);

        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.rebase");
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "targetFeatureBranch",
                TARGET_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_SOURCE_TESTFILE_2 = "SOURCE2: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "source_testfile.txt", COMMIT_MESSAGE_SOURCE_TESTFILE_2);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "Automatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                                + TARGET_FEATURE_BRANCH + "' failed.\nCONFLICT (added on " + TARGET_FEATURE_BRANCH
                                + " and on " + SOURCE_FEATURE_BRANCH + "): " + TESTFILE_NAME,
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-integrate' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-integrate' to continue feature integration process",
                        "'mvn flow:feature-integrate-abort' to abort feature integration process"));
        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        userProperties.remove("featureName");
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "Automatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                                + TARGET_FEATURE_BRANCH + "' failed.\nCONFLICT (added on " + TARGET_FEATURE_BRANCH
                                + " and on " + SOURCE_FEATURE_BRANCH + "): " + TESTFILE_NAME,
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-integrate' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-integrate' to continue feature integration process",
                        "'mvn flow:feature-integrate-abort' to abort feature integration process"));

        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TARGET_FEATURE_BRANCH,
                TMP_SOURCE_FEATURE_BRANCH);

        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.rebase");
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        git.assertBranchLocalConfigValue(repositorySet, TMP_SOURCE_FEATURE_BRANCH, "targetFeatureBranch",
                TARGET_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_SOURCE_TESTFILE_2 = "SOURCE2: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "source_testfile.txt", COMMIT_MESSAGE_SOURCE_TESTFILE_2);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result,
                new GitFlowFailureInfo(
                        "Automatic rebase of feature branch '" + SOURCE_FEATURE_BRANCH + "' on top of feature branch '"
                                + TARGET_FEATURE_BRANCH + "' failed.\nCONFLICT (added on " + TARGET_FEATURE_BRANCH
                                + " and on " + SOURCE_FEATURE_BRANCH + "): " + TESTFILE_NAME,
                        "Fix the rebase conflicts and mark them as resolved by using 'git add'. After that, run "
                                + "'mvn flow:feature-integrate' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!",
                        "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved",
                        "'mvn flow:feature-integrate' to continue feature integration process",
                        "'mvn flow:feature-integrate-abort' to abort feature integration process"));
        git.assertRebaseBranchInProcess(repositorySet, TMP_SOURCE_FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        userProperties.remove("featureName");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_SOURCE_TESTFILE_2, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteInstallProjectTrueAndInstallProjectOnFeatureIntegrateFalse() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("flow.installProjectOnFeatureIntegrate", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteInstallProjectFalseAndInstallProjectOnFeatureIntegrateTrue() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.installProject", "false");
        userProperties.setProperty("flow.installProjectOnFeatureIntegrate", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareFeatures();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertInstallProjectFailureException(result, GOAL, TARGET_FEATURE_BRANCH, "feature integration");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_SOURCE_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_SOURCE_TESTFILE, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);

        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        prepareFeatures();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertInstallProjectFailureException(result, GOAL, TARGET_FEATURE_BRANCH, "feature integration");
        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        userProperties.remove("featureName");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_SOURCE_TESTFILE, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactInstalled();

        git.assertBranchLocalConfigValueMissing(repositorySet, TARGET_FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, TARGET_FEATURE_BRANCH, "sourceFeatureBranch");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareFeatures();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertInstallProjectFailureException(result, GOAL, TARGET_FEATURE_BRANCH, "feature integration");
        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "breakpoint",
                "featureIntegrate.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, TARGET_FEATURE_BRANCH, "sourceFeatureBranch",
                SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("flow.installProject", "false");
        userProperties.remove("featureName");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_SOURCE_TESTFILE, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, TARGET_FEATURE_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, TARGET_FEATURE_BRANCH, "sourceFeatureBranch");
    }

    @Test
    public void testExecuteWithoutFeatureNameInInteractivemode() throws Exception {
        // set up
        final String PROMPT_CHOOSE_BRANCH = "Feature branches:" + LS + "1. " + TARGET_FEATURE_BRANCH + LS
                + "Choose the target feature branch which the source feature branch should be integrated into";
        prepareFeatures();
        when(promptControllerMock.prompt(PROMPT_CHOOSE_BRANCH, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_CHOOSE_BRANCH, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteTargetFeatureNotExistingLocally() throws Exception {
        // set up
        prepareFeatures();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, TARGET_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteTargetFeatureNotExistingRemotely() throws Exception {
        // set up
        prepareFeatures();
        git.deleteRemoteBranch(repositorySet, TARGET_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteSourceFeatureBranchVisoutVersionChange() throws Exception {
        // set up
        final String USED_SOURCE_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_TARGET_FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;
        userProperties.remove("flow.featureBranchPrefix");
        git.switchToBranch(repositorySet, USED_TARGET_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "target_testfile.txt", COMMIT_MESSAGE_TARGET_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_SOURCE_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "source_testfile.txt", COMMIT_MESSAGE_SOURCE_TESTFILE);
        git.push(repositorySet);
        userProperties.setProperty("featureName", BasicConstants.EXISTING_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_SOURCE_FEATURE_BRANCH, "tmp-" + USED_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_TARGET_FEATURE_BRANCH,
                USED_TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_FEATURE_VERSION);
    }

    @Test
    public void testExecuteOffline() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.fetchRemote", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        git.assertCommitsInRemoteBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteWithKeepFeatureBranchTrue() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.keepFeatureBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, TMP_SOURCE_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteWithSquashNewModuleVersionFixCommitTrueAndOneCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_NEW_MODULE = "add new module";
        createNewModule();
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_MODULE);
        git.push(repositorySet);
        prepareTargetFeature();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.squashNewModuleVersionFixCommit", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_NEW_MODULE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"),
                BasicConstants.SECOND_FEATURE_VERSION);
    }

    public void createNewModule() throws IOException {
        File workingDir = repositorySet.getWorkingDirectory();
        File moduleDir = new File(workingDir, "module");
        moduleDir.mkdir();
        FileUtils.fileWrite(new File(moduleDir, "pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "       xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
                        + "       <modelVersion>4.0.0</modelVersion>\n" + "       <parent>\n"
                        + "               <groupId>de.gebit.build.maven.test</groupId>\n"
                        + "               <artifactId>basic-project</artifactId>\n" + "               <version>"
                        + BasicConstants.FIRST_FEATURE_VERSION + "</version>\n" + "       </parent>\n"
                        + "       <artifactId>basic-module</artifactId>\n" + "</project>\n");
        File pom = new File(workingDir, "pom.xml");
        String pomContents = FileUtils.fileRead(pom);
        pomContents = pomContents.replaceAll("</project>",
                "\t<modules><module>module</module></modules>\n\t<packaging>pom</packaging>\n</project>");
        FileUtils.fileWrite(pom, pomContents);
    }

    @Test
    public void testExecuteWithSquashNewModuleVersionFixCommitTrueAndTwoCommits() throws Exception {
        // set up
        final String COMMIT_MESSAGE_NEW_MODULE = "add new module";
        prepareFeatures();
        createNewModule();
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_MODULE);
        git.push(repositorySet);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("flow.squashNewModuleVersionFixCommit", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_REVERT_MODULE_VERSION,
                COMMIT_MESSAGE_NEW_MODULE, COMMIT_MESSAGE_SOURCE_TESTFILE, COMMIT_MESSAGE_TARGET_TESTFILE,
                BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"),
                BasicConstants.SECOND_FEATURE_VERSION);
    }

    @Test
    public void testExecuteLocalMasterBehindRemoteAndRebasedFeature_GBLD696() throws Exception {
        // set up
        prepareFeatures();
        git.useClonedRemoteRepository(repositorySet);
        git.fetch(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createBranchFromRemote(repositorySet, SOURCE_FEATURE_BRANCH);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        assertFalse(result.hasExceptions());
        git.createBranchFromRemote(repositorySet, TARGET_FEATURE_BRANCH);
        result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet, userProperties);
        assertFalse(result.hasExceptions());
        git.useLocalRepository(repositorySet);
        git.resetToRemote(repositorySet);
        git.deleteLocalBranch(repositorySet, TARGET_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, TARGET_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, SOURCE_FEATURE_BRANCH, TMP_SOURCE_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, TARGET_FEATURE_BRANCH, TARGET_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, TARGET_FEATURE_BRANCH, COMMIT_MESSAGE_SOURCE_TESTFILE,
                COMMIT_MESSAGE_TARGET_TESTFILE, BasicConstants.SECOND_FEATURE_VERSION_COMMIT_MESSAGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.SECOND_FEATURE_VERSION);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, SOURCE_FEATURE_BRANCH);
    }

    @Test
    public void testExecuteTargetFeatureBranchBehindSourceFeatureBranch() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        MavenExecutionResult result = ExecutorHelper.executeFeatureRebaseWithResult(this, repositorySet,
                userProperties);
        assertFalse(result.hasExceptions());
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The branch point of the target feature branch is behind the branch point of the current feature "
                        + "branch.",
                "Please rebase the target feature branch '" + TARGET_FEATURE_BRANCH + "' first in order to proceed.",
                "'git checkout " + TARGET_FEATURE_BRANCH
                        + "' and 'mvn flow:feature-rebase' to rebase the target feature branch",
                "'git checkout " + SOURCE_FEATURE_BRANCH
                        + "' and 'mvn flow:feature-integrate' to start the feature integration process again");
    }

    @Test
    public void testExecuteWithSourceBranchCurrentFeature() throws Exception {
        // set up
        prepareFeatures();
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("sourceBranch", SOURCE_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteWithSourceBranchNotCurrentFeature() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("sourceBranch", SOURCE_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteWithSourceBranchNotFeature() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        userProperties.setProperty("sourceBranch", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Branch '" + OTHER_BRANCH + "' defined in 'sourceBranch' property is not a feature branch.",
                "Please define a feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithSourceBranchNotExistingFeature() throws Exception {
        // set up
        final String NON_EXISTING_FEATURE_BRANCH = FEATURE_BRANCH_PREFIX + "nonExisting";
        userProperties.setProperty("sourceBranch", NON_EXISTING_FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Feature branch '" + NON_EXISTING_FEATURE_BRANCH + "' defined in 'sourceBranch' property doesn't exist.",
                "Please define an existing feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithSourceBranchNotExistingLocalFeature() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        userProperties.setProperty("sourceBranch", SOURCE_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteNotOnFeatureBranchInInteractivemode() throws Exception {
        // set up
        final String PROMPT_CHOOSE_BRANCH = "Feature branches:" + LS + "1. " + SOURCE_FEATURE_BRANCH + LS + "2. "
                + TARGET_FEATURE_BRANCH + LS
                + "Choose source feature branch to be integrated into target feature branch";
        prepareFeatures();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("featureName", TARGET_FEATURE_NAME);
        when(promptControllerMock.prompt(PROMPT_CHOOSE_BRANCH, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_CHOOSE_BRANCH, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
    }

    @Test
    public void testExecuteWithTargetBranchCurrentFeature() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, TARGET_FEATURE_BRANCH);
        userProperties.setProperty("sourceBranch", SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("targetBranch", TARGET_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteWithTargetBranchNotCurrentFeature() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        userProperties.setProperty("targetBranch", TARGET_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

    @Test
    public void testExecuteWithTargetBranchNotFeature() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        prepareFeatures();
        userProperties.setProperty("targetBranch", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Branch '" + OTHER_BRANCH + "' defined in 'targetBranch' property is not a feature branch.",
                "Please define a feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithTargetBranchNotExistingFeature() throws Exception {
        // set up
        final String NON_EXISTING_FEATURE_BRANCH = FEATURE_BRANCH_PREFIX + "nonExisting";
        prepareFeatures();
        userProperties.setProperty("targetBranch", NON_EXISTING_FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Feature branch '" + NON_EXISTING_FEATURE_BRANCH + "' defined in 'targetBranch' property doesn't exist.",
                "Please define an existing feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithTargetBranchNotExistingLocalFeature() throws Exception {
        // set up
        prepareFeatures();
        git.switchToBranch(repositorySet, SOURCE_FEATURE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, TARGET_FEATURE_BRANCH);
        userProperties.setProperty("targetBranch", TARGET_FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureIntegratedSuccessfully();
    }

}
