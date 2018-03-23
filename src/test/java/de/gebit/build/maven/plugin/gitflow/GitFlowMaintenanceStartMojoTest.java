//
// GitFlowMaintenanceStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowMaintenanceStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "maintenance-start";

    private static final String VERSION_TAG_PREFIX = "gitflow-tests-";

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    private static final String PROMPT_SELECTING_RELEASE_NO_TAGS = ExecutorHelper.MAINTENANCE_START_PROMPT_SELECTING_RELEASE;

    private static final String PROMPT_MAINTENANCE_VERSION = ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION;

    private static final String PROMPT_MAINTENANCE_FIRST_VERSION = ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

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
    public void testExecuteNoTags() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    protected void assertMaintenanceBranchCratedCorrectlyFromMaster() throws GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteBasedOffMasterWithTwoTags() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_TWO_TAGS = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2
                + LS + "2. " + TAG1 + LS + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteWithReleaseBranchFilter() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_ONE_TAG = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2 + LS
                + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseBranchFilter", "2.*");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteWithEmptyReleaseBranchFilter() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_TWO_TAGS = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2
                + LS + "2. " + TAG1 + LS + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseBranchFilter", "");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteWithReleaseBranchFilterRegEx() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_ONE_TAG = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2 + LS
                + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseBranchFilter", "^2\\.[0-9]+\\.0$");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteWithReleaseVersionLimit() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String TAG3 = VERSION_TAG_PREFIX + "3.0.0";
        final String PROMPT_SELECTING_RELEASE_TWO_TAGS = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG3
                + LS + "2. " + TAG2 + LS + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2, TAG3);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersionLimit", "2");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteWithReleaseVersionLimitSameAsTagsCount() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_TWO_TAGS = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2
                + LS + "2. " + TAG1 + LS + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        git.createTags(repositorySet, TAG1, TAG2);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersionLimit", "2");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteExplicitTag() throws Exception {
        // set up
        final String TAG = "v1.2.3";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        final String PROMPT_EXPLICIT_TAG = "Enter explicit tag name";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("T");
        when(promptControllerMock.prompt(PROMPT_EXPLICIT_TAG)).thenReturn(TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_EXPLICIT_TAG);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
    }

    @Ignore
    @Test
    public void testExecuteExplicitNotExistingTag() throws Exception {
        // set up
        final String NOT_EXISTING_TAG = "NotExistingTag";
        final String PROMPT_EXPLICIT_TAG = "Enter explicit tag name";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("T");
        when(promptControllerMock.prompt(PROMPT_EXPLICIT_TAG)).thenReturn(NOT_EXISTING_TAG);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_EXPLICIT_TAG);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "", "");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Ignore
    @Test
    public void testExecuteExplicitEmptyTag() throws Exception {
        // set up
        final String PROMPT_EXPLICIT_TAG = "Enter explicit tag name";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("T");
        when(promptControllerMock.prompt(PROMPT_EXPLICIT_TAG)).thenReturn("");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_EXPLICIT_TAG);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "", "");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSelectedTag() throws Exception {
        // set up
        final String TAG1 = VERSION_TAG_PREFIX + "1.0.0";
        final String TAG2 = VERSION_TAG_PREFIX + "2.1.0";
        final String PROMPT_SELECTING_RELEASE_TWO_TAGS = "Release:" + LS + "0. <current commit>" + LS + "1. " + TAG2
                + LS + "2. " + TAG1 + LS + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG1_TESTFILE = "TAG1: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG2_TESTFILE = "TAG2: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag1-testfile.txt", COMMIT_MESSAGE_TAG1_TESTFILE);
        git.createTags(repositorySet, TAG1);
        git.createAndCommitTestfile(repositorySet, "tag2-testfile.txt", COMMIT_MESSAGE_TAG2_TESTFILE);
        git.createTags(repositorySet, TAG2);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T")))
                .thenReturn("2");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION)).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG2_TESTFILE, COMMIT_MESSAGE_TAG1_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG1_TESTFILE);
    }

}
