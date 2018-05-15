//
// GitFlowFeatureFinishWithPreRebaseMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureFinishWithPreRebaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_ISSUE = BasicConstants.EXISTING_FEATURE_ISSUE;

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_FEATURE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + MASTER_BRANCH + " into " + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_REVERT_VERSION = FEATURE_ISSUE
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic rebase of feature branch '" + FEATURE_BRANCH + "' on top of base branch '" + MASTER_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again.\n"
                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
            "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of base branch '" + MASTER_BRANCH + "' into feature branch '" + FEATURE_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' again.\n"
                    + "Do NOT run 'git merge --continue'!\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
            "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");

    private RepositorySet repositorySet;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
        userProperties = new Properties();
        userProperties.setProperty("rebase", "true");
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    private void executeMojo() throws Exception {
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
    }

    private MavenExecutionResult executeMojoWithResult() throws Exception {
        return executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
    }

    @Test
    public void testExecuteNoChangesOnMaster() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo();
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNoChangesOnFeatureExceptVersionChange() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in feature branch '" + FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch "
                        + "and 'mvn flow:feature-finish' to run the feature finish again");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithPreRebase() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithPreMerge() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        userProperties.setProperty("flow.updateWithMerge", "true");
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MERGE_INTO_FEATURE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
    }

    @Test
    public void testExecuteWithPreRebaseWithoutVersionChangeCommit() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo();
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
    }

    private void createFeatureBranchDivergentFromMaster() throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    @Test
    public void testExecuteWithRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteWithMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        userProperties.setProperty("flow.updateWithMerge", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteWithRebaseConflictOnPreRebaseWithoutVersionChangeCommit() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final GitFlowFailureInfo USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + USED_FEATURE_BRANCH + "' on top of base branch '"
                        + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' "
                        + "again.\nDo NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                        + "resolved\\E",
                "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult();
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, USED_FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo(
                        "\\QThere are unresolved conflicts after rebase of feature branch on top of base branch.\n"
                                + "Git error message:\n\\E.*",
                        "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final GitFlowFailureInfo USED_EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
                "\\QAutomatic merge of base branch '" + MASTER_BRANCH + "' into feature branch '" + USED_FEATURE_BRANCH
                        + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' "
                        + "again.\nDo NOT run 'git merge --continue'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                        + "resolved\\E",
                "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, USED_EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo(
                        "\\QThere are unresolved conflicts after merge of base branch into feature branch.\n"
                                + "Git error message:\n\\E.*",
                        "\\QFix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E"));
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, USED_FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictOnPreRebaseWithoutVersionChangeCommit()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final GitFlowFailureInfo USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + USED_FEATURE_BRANCH + "' on top of base branch '"
                        + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' "
                        + "again.\nDo NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                        + "resolved\\E",
                "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo(
                        "\\QThere are unresolved conflicts after rebase of feature branch on top of base branch.\n"
                                + "Git error message:\n\\E.*",
                        "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-finish' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, USED_FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictOnPreRebaseWithoutVersionChangeCommitAndPromptAnswerNo()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final GitFlowFailureInfo USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + USED_FEATURE_BRANCH + "' on top of base branch '"
                        + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' "
                        + "again.\nDo NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                        + "resolved\\E",
                "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValue(repositorySet, USED_FEATURE_BRANCH, "breakpoint",
                "featureFinish.rebaseBeforeFinish");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        userProperties.setProperty("flow.updateWithMerge", "true");
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MERGE_INTO_FEATURE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictOnPreRebaseWithoutVersionChangeCommitAndPromptAnswerNo()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final GitFlowFailureInfo USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
                "\\QAutomatic rebase of feature branch '" + USED_FEATURE_BRANCH + "' on top of base branch '"
                        + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
                "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-finish' "
                        + "again.\nDo NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as "
                        + "resolved\\E",
                "\\Q'mvn flow:feature-finish' to continue feature finish process\\E",
                "\\Q'mvn flow:feature-rebase-abort' to abort feature finish process\\E");
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult();
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, USED_EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteWithPreRebaseOnMaster() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo();
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_FEATURE_BRANCH,
                "rebasedWithoutVersionChangeCommit");
    }

}
