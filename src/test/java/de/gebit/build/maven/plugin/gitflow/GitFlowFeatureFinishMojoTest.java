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
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.interactivity.PrompterException;
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
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_ISSUE = BasicConstants.EXISTING_FEATURE_ISSUE;

    private static final String FEATURE_NAME = BasicConstants.EXISTING_FEATURE_NAME;

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_REVERT_VERSION = FEATURE_ISSUE
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String PROMPT_MERGE_WITHOUT_REBASE = "Base branch ''{0}'' has changes that are not yet included "
            + "in feature branch ''{1}''." + LS + "You have following options:" + LS
            + "r. Rebase feature branch and continue feature finish process" + LS
            + "m. (NOT RECOMMENDED) Continue feature finish process by trying to merge feature branch into the base "
            + "branch" + LS + "a. Abort feature finish process" + LS + "Select how you want to continue:";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic rebase failed.\nCONFLICT (modified on base branch and on " + FEATURE_BRANCH + "): pom.xml",
            "Fix the rebase conflicts and mark them as resolved by using 'git add'. "
                    + "After that, run 'mvn flow:feature-finish' again.\nDo NOT run 'git rebase --continue'.",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:feature-finish' to continue feature finish process");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge failed.\nCONFLICT (added on " + MASTER_BRANCH + " and on " + FEATURE_BRANCH + "): "
                    + GitExecution.TESTFILE_NAME,
            "Fix the merge conflicts and mark them as resolved by using 'git add'. "
                    + "After that, run 'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'.",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:feature-finish' to continue feature finish process");

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnMaintenanceBranch_GBLD283() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);

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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchNewModuleWithOneCommitOnFeautreBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_NEW_MODULE = BasicConstants.SINGLE_FEATURE_ISSUE + ": new module";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);

        createNewModule();
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_MODULE);
        git.push(repositorySet);

        // make sure project and parent are installed
        executeMojoWithResult(repositorySet.getWorkingDirectory().getParentFile(), "#install");
        executeMojoWithResult(repositorySet.getWorkingDirectory(), "#install");

        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);

        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                "GFTST-103: reverting versions for development branch", COMMIT_MESSAGE_NEW_MODULE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchNewModuleWithOneCommitOnFeautreBranchAndSquashNewModuleVersionFixCommitTrue()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_NEW_MODULE = BasicConstants.SINGLE_FEATURE_ISSUE + ": new module";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);

        createNewModule();
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_MODULE);
        git.push(repositorySet);

        // make sure project and parent are installed
        executeMojoWithResult(repositorySet.getWorkingDirectory().getParentFile(), "#install");
        executeMojoWithResult(repositorySet.getWorkingDirectory(), "#install");

        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        userProperties.setProperty("flow.squashNewModuleVersionFixCommit", "true");

        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_NEW_MODULE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), TestProjects.BASIC.version);
    }

    private void createNewModule() throws IOException {
        File workingDir = repositorySet.getWorkingDirectory();
        File moduleDir = new File(workingDir, "module");
        moduleDir.mkdir();
        FileUtils.fileWrite(new File(moduleDir, "pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "       xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
                        + "       <modelVersion>4.0.0</modelVersion>\n" + "       <parent>\n"
                        + "               <groupId>de.gebit.build.maven.test</groupId>\n"
                        + "               <artifactId>basic-project</artifactId>\n"
                        + "               <version>1.2.3-GFTST-103-SNAPSHOT</version>\n" + "       </parent>\n"
                        + "       <artifactId>basic-module</artifactId>\n" + "</project>\n");
        File pom = new File(workingDir, "pom.xml");
        String pomContents = FileUtils.fileRead(pom);
        pomContents = pomContents.replaceAll("</project>",
                "\t<modules><module>module</module></modules>\n\t<packaging>pom</packaging>\n</project>");
        FileUtils.fileWrite(pom, pomContents);
    }

    @Test
    public void testExecuteOnMasterBranchNewModuleWithTwoCommitsOnFeautreBranchAndSquashNewModuleVersionFixCommitTrue()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_NEW_MODULE = BasicConstants.SINGLE_FEATURE_ISSUE + ": new module";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);

        createNewModule();
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_MODULE);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);

        // make sure project and parent are installed
        executeMojoWithResult(repositorySet.getWorkingDirectory().getParentFile(), "#install");
        executeMojoWithResult(repositorySet.getWorkingDirectory(), "#install");

        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        userProperties.setProperty("flow.squashNewModuleVersionFixCommit", "true");

        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, "GFTST-103: reverting versions for development branch",
                COMMIT_MESSAGE_NEW_MODULE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchMultipleFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + BasicConstants.FIRST_FEATURE_BRANCH + LS + "2. "
                + BasicConstants.SECOND_FEATURE_BRANCH + LS + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, BasicConstants.FIRST_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, BasicConstants.SECOND_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        assertFeatureFinishedCorrectly();
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
                "In non-interactive mode 'mvn flow:feature-finish' can be executed only on a feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the feature branch",
                "'mvn flow:feature-finish' to run in interactive mode");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
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
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteSkipTestProjectFalseAndSkipTestProjectOnFeatureFinishTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.skipTestProjectOnFeatureFinish", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteSkipTestProjectTrueAndSkipTestProjectOnFeatureFinishFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "true");
        userProperties.setProperty("flow.skipTestProjectOnFeatureFinish", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteSkipTestProjectFalseAndTestProjectGoalsSet() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.testProjectGoals", "validate -DskipTests");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("validate -DskipTests");
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteSkipTestProjectFalseAndTestProjectOptionsSet() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.testProjectOptions", "-T 4");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify -T 4");
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteSkipTestProjectFalseAndTestProjectGoalsAndOptionsSet() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.testProjectGoals", "validate -DskipTests");
        userProperties.setProperty("flow.testProjectOptions", "-T 4");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("validate -DskipTests -T 4");
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteTychoBuildAndSkipTestProjectFalse() throws Exception {
        // set up
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
    public void testExecuteInstallProjectTrueAndInstallProjectOnFeatureFinishFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("flow.installProjectOnFeatureFinish", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteInstallProjectFalseAndInstallProjectOnFeatureFinishTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "false");
        userProperties.setProperty("flow.installProjectOnFeatureFinish", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteInstallProjectGoalsOnFeatureFinishSet() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        userProperties.setProperty("flow.installProjectGoalsOnFeatureFinish", "validate");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("validate");
        assertMavenCommandNotExecuted("clean install");
    }

    @Test
    public void testExecuteTestProjectGoalsOnFeatureFinishSet() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.testProjectGoalsOnFeatureFinish", "validate");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("validate");
        assertMavenCommandNotExecuted("clean verify");
    }

    @Test
    public void testExecuteKeepFeatureBranchTrue() throws Exception {
        // set up
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
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
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalseAndVersionWithoutFeatureName() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndVersionWithoutFeatureName() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndHasMergeCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH = "Merging master to feature branch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.mergeAndCommit(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMaintenanceBranchOnSameCommitAsMasterBranch()
            throws Exception {
        // set up
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_FEATURE_BRANCH + " into " + USED_MAINTENANCE_BRANCH;
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        mockupPromptMessageWithoutRebase(USED_MAINTENANCE_BRANCH, USED_FEATURE_BRANCH, "m");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyPromptMessageWithoutRebase(USED_MAINTENANCE_BRANCH, USED_FEATURE_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_BRANCH, USED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH,
                USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMainteanceBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH;
        final String OTHER_BRANCH = "otherBranch";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        mockupPromptMessageWithoutRebase("m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    private void mockupPromptMessageWithoutRebase(String selection) throws PrompterException {
        mockupPromptMessageWithoutRebase(MASTER_BRANCH, FEATURE_BRANCH, selection);
    }

    private void mockupPromptMessageWithoutRebase(String baseBranch, String featureBranch, String selection)
            throws PrompterException {
        when(promptControllerMock.prompt(MessageFormat.format(PROMPT_MERGE_WITHOUT_REBASE, baseBranch, featureBranch),
                Arrays.asList("r", "m", "a"), "a")).thenReturn(selection);
    }

    private void verifyPromptMessageWithoutRebase() throws PrompterException {
        verifyPromptMessageWithoutRebase(MASTER_BRANCH, FEATURE_BRANCH);
    }

    private void verifyPromptMessageWithoutRebase(String baseBranch, String featureBranch) throws PrompterException {
        verify(promptControllerMock).prompt(
                MessageFormat.format(PROMPT_MERGE_WITHOUT_REBASE, baseBranch, featureBranch),
                Arrays.asList("r", "m", "a"), "a");
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
                + "-NONE: Merge branch " + USED_FEATURE_BRANCH + " into " + MAINTENANCE_BRANCH;
        final String COMMIT_MESSAGE_MAINTENACE_TESTFILE = "MAINTEANCE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENACE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        mockupPromptMessageWithoutRebase(MAINTENANCE_BRANCH, USED_FEATURE_BRANCH, "m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase(MAINTENANCE_BRANCH, USED_FEATURE_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MAINTENACE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteFeatureWithoutChanges() throws Exception {
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteFeatureWithoutChangesAndRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote and local feature branches '" + USED_FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + USED_FEATURE_BRANCH + "' first.",
                "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                BasicConstants.SINGLE_FEATURE_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
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
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteLocalBaseBranchAheadOfRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
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
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE,
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
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
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, USED_FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
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
        assertGitFlowFailureException(result,
                "Remote and local feature branches '" + USED_FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + USED_FEATURE_BRANCH + "' first.",
                "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertExistingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.SINGLE_FEATURE_VERSION_COMMIT_MESSAGE);
    }

    @Test
    public void testExecuteWithRemovingVersionCommitRebaseConflict() throws Exception {
        // set up
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_REBASE_CONFLICT_MESSAGE);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
    }

    @Test
    public void testExecuteContinueRebaseAfterResolvedRemovingVersionCommitRebaseConflict() throws Exception {
        // set up
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_REBASE_CONFLICT_MESSAGE);
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
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_NEW_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteContinueRebaseAfterRemovingVersionCommitRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_REBASE_CONFLICT_MESSAGE);
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
        final String NEW_VERSION = "2.0.0-SNAPSHOT";
        ExecutorHelper.executeSetVersion(this, repositorySet, NEW_VERSION);
        final String COMMIT_MESSAGE_NEW_VERSION = "new version";
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_REBASE_CONFLICT_MESSAGE);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath("pom.xml").call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, new GitFlowFailureInfo(
                "There are unresolved conflicts after rebase.\nCONFLICT (modified on base branch and on "
                        + FEATURE_BRANCH + "): pom.xml",
                "Fix the rebase conflicts and mark them as resolved by using 'git add'. "
                        + "After that, run 'mvn flow:feature-finish' again.\nDo NOT run 'git rebase --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-finish' to continue feature finish process"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, "pom.xml");
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInBatchMode() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerAbort() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("a");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.allowFF", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Feature finish aborted by user.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerRebase() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("r");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.allowFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteNotRebasedFeatureBranchInInteractiveModeWithAnswerMergeAndConflicts() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyPromptMessageWithoutRebase();
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, EXPECTED_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyPromptMessageWithoutRebase();
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
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteContinueAfterMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyPromptMessageWithoutRebase();
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
        assertGitFlowFailureException(result, "Continuation of feature finish aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        mockupPromptMessageWithoutRebase("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyPromptMessageWithoutRebase();
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
        assertGitFlowFailureException(result, new GitFlowFailureInfo(
                "There are unresolved conflicts after merge.\nCONFLICT (added on base branch and on " + FEATURE_BRANCH
                        + "): " + GitExecution.TESTFILE_NAME,
                "Fix the merge conflicts and mark them as resolved by using 'git add'. "
                        + "After that, run 'mvn flow:feature-finish' again.\nDo NOT run 'git merge --continue'.",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:feature-finish' to continue feature finish process"));
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcess(repositorySet, GitExecution.TESTFILE_NAME);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedRemotelyOnMaintenanceBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH;
        final String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
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
        assertFeatureFinishedCorrectly();
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
        assertFeatureFinishedCorrectly();
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
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
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
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_FEATURE_BRANCH, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndRemoteMaintenanceBranchMissing() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '" + USED_FEATURE_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
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
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '" + USED_FEATURE_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteWithMaintenanceBranchStartedAfterFeatureStartedOnMasterBranch() throws Exception {
        // set up
        final String USED_MAINTENANCE_VERSION = "38.9";
        final String USED_MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + USED_MAINTENANCE_VERSION;
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        final String COMMIT_MESSAGE_MASTER_TESTFILE_0 = "MASTER 0: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_1 = "MASTER 1: Unit test dummy file commit";
        final String COMMIT_MESSAGE_MASTER_TESTFILE_2 = "MASTER 2: Unit test dummy file commit";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile0.txt", COMMIT_MESSAGE_MASTER_TESTFILE_0);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, USED_MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile1.txt", COMMIT_MESSAGE_MASTER_TESTFILE_1);
        git.createAndCommitTestfile(repositorySet, "master_testfile2.txt", COMMIT_MESSAGE_MASTER_TESTFILE_2);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + USED_FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        mockupPromptMessageWithoutRebase(MASTER_BRANCH, USED_FEATURE_BRANCH, "m");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyPromptMessageWithoutRebase(MASTER_BRANCH, USED_FEATURE_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, USED_COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE_2, COMMIT_MESSAGE_MASTER_TESTFILE_1,
                COMMIT_MESSAGE_MASTER_TESTFILE_0);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_BRANCH, USED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE_0);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersion() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_EPIC_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchAndRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_MERGE_INTO_EPIC = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH + " into " + USED_EPIC_BRANCH;
        final String USED_COMMIT_MESSAGE_REVERT_VERSION = BasicConstants.FEATURE_ON_EPIC_ISSUE
                + ": reverting versions for development branch";
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, USED_COMMIT_MESSAGE_MERGE_INTO_EPIC,
                USED_COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.FEATURE_ON_EPIC_VERSION_COMMIT_MESSAGE,
                BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutFeatureVersion() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersionAndWithoutFeatureVersion()
            throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutFeatureVersionAndRebaseWithoutVersionChangeFalse()
            throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.EXISTING_EPIC_VERSION);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteOnFeatureBranchStartedOnEpicBranchWithoutEpicVersionAndWithoutFeatureVersionAndRebaseWithoutVersionChangeFalse()
            throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITHOUT_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_EPIC_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                BasicConstants.EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        verifyZeroInteractions(promptControllerMock);
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
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
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
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "feature finish");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "featureFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointFeatureBranch", FEATURE_BRANCH);
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
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "feature finish");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "featureFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointFeatureBranch", FEATURE_BRANCH);
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpointFeatureBranch");
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
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "feature finish");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "featureFinish.cleanInstall");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpointFeatureBranch", FEATURE_BRANCH);
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "breakpointFeatureBranch");
    }

    @Test
    public void testExecuteWithDeletedModuleOnFeatureBranch_GBLD648() throws Exception {
        final String MODULE_TO_DELETE = "module1";
        final String COMMIT_MESSAGE_DELETE_MODULE = FEATURE_ISSUE + ": delete module";
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            ExecutorHelper.executeFeatureStart(this, otherRepositorySet, FEATURE_NAME);
            removeModule(otherRepositorySet, MODULE_TO_DELETE);
            git.commitAll(otherRepositorySet, COMMIT_MESSAGE_DELETE_MODULE);
            git.createAndCommitTestfile(otherRepositorySet);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL);
            // verify
            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertMissingLocalBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(otherRepositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, MASTER_BRANCH, COMMIT_MESSAGE_DELETE_MODULE,
                    COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), TestProjects.WITH_MODULES.version);

            assertFalse("module1 directory shouldn't exist",
                    new File(otherRepositorySet.getWorkingDirectory(), MODULE_TO_DELETE).exists());

            Model workingPom = readPom(otherRepositorySet.getWorkingDirectory());
            List<String> modules = workingPom.getModules();
            assertEquals(1, modules.size());
            assertEquals("module2", modules.get(0));

            git.assertRemoteFileMissing(otherRepositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        }
    }

    @Test
    public void testExecuteWithDeletedTwoModulesOnFeatureBranch_GBLD648() throws Exception {
        final String MODULE1_TO_DELETE = "module1";
        final String MODULE2_TO_DELETE = "module2";
        final String COMMIT_MESSAGE_DELETE_MODULE1 = FEATURE_ISSUE + ": delete module " + MODULE1_TO_DELETE;
        final String COMMIT_MESSAGE_DELETE_MODULE2 = FEATURE_ISSUE + ": delete module " + MODULE2_TO_DELETE;
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            ExecutorHelper.executeFeatureStart(this, otherRepositorySet, FEATURE_NAME);
            removeModule(otherRepositorySet, MODULE1_TO_DELETE);
            git.commitAll(otherRepositorySet, COMMIT_MESSAGE_DELETE_MODULE1);
            git.createAndCommitTestfile(otherRepositorySet);
            removeModule(otherRepositorySet, MODULE2_TO_DELETE);
            git.commitAll(otherRepositorySet, COMMIT_MESSAGE_DELETE_MODULE2);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL);
            // verify
            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertMissingLocalBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, FEATURE_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(otherRepositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, MASTER_BRANCH, COMMIT_MESSAGE_DELETE_MODULE2,
                    COMMIT_MESSAGE_DELETE_MODULE1, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE);
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), TestProjects.WITH_MODULES.version);

            assertFalse("module1 directory shouldn't exist",
                    new File(otherRepositorySet.getWorkingDirectory(), MODULE1_TO_DELETE).exists());
            assertFalse("module2 directory shouldn't exist",
                    new File(otherRepositorySet.getWorkingDirectory(), MODULE2_TO_DELETE).exists());

            Model workingPom = readPom(otherRepositorySet.getWorkingDirectory());
            List<String> modules = workingPom.getModules();
            assertEquals(0, modules.size());

            git.assertRemoteFileMissing(otherRepositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        }
    }

    @Test
    public void testExecuteFailureOnTestProjectBeforeFinish_GBLD710() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_TESTFILE_MASTER = "MASTER: test file";
        final String TESTFILE_MASTER = "testfile_master.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_MASTER, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.rebase", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertTestProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature finish");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.testProjectAfterRebase");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureHEAD");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedBeforeFinish", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
    }

    @Test
    public void testExecuteContinueAfterFailureOnTestProjectBeforeFinish_GBLD710() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        final String COMMIT_MESSAGE_TESTFILE_MASTER = "MASTER: test file";
        final String TESTFILE_MASTER = "testfile_master.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_MASTER, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertTestProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature finish");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.testProjectAfterRebase");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureHEAD");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedBeforeFinish", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_TESTFILE_MASTER,
                COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteContinueWithSkipTestTrueAfterFailureOnTestProjectBeforeFinish_GBLD710() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_TESTFILE_MASTER = "MASTER: test file";
        final String TESTFILE_MASTER = "testfile_master.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_MASTER, COMMIT_MESSAGE_TESTFILE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.rebase", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertTestProjectFailureException(result, GOAL, FEATURE_BRANCH, "feature finish");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint",
                "featureFinish.testProjectAfterRebase");
        git.assertBranchLocalConfigValueExists(repositorySet, FEATURE_BRANCH, "oldFeatureHEAD");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedBeforeFinish", "true");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "rebasedWithoutVersionChangeCommit", "true");
        userProperties.setProperty("flow.skipTestProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_TESTFILE_MASTER,
                COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteSingleCommitAndAllowFFFalse_GBLD735() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.allowFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteMultipleCommitsAndAllowFFFalse_GBLD735() throws Exception {
        // set up
        final String COMMIT_MESSAGE_FOR_TESTFILE2 = "Unit test dummy file 2 commit";
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "testfile2.txt", COMMIT_MESSAGE_FOR_TESTFILE2);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.allowFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE, COMMIT_MESSAGE_FOR_TESTFILE2,
                COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteWithBranchNameCurrentFeature() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotCurrentFeature() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBranchNameNotFeature() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        Properties userProperties = new Properties();
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
        final String NON_EXISTING_FEATURE_BRANCH = "feature/nonExisting";
        Properties userProperties = new Properties();
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
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
    }

}
