//
// GitFlowMaintenanceStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
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

    private static final String CALCULATED_MAINTENANCE_VERSION = TestProjects.BASIC.maintenanceVersion;

    private static final String PROMPT_MAINTENANCE_FIRST_VERSION = ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION;

    private static final String CALCULATED_MAINTENANCE_FIRST_VERSION = TestProjects.BASIC.nextSnepshotVersion;

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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
        assertArtifactNotInstalled();
    }

    protected void assertMaintenanceBranchCratedCorrectlyFromMaster()
            throws GitAPIException, IOException, ComponentLookupException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_ONE_TAG, Arrays.asList("0", "1", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_EXPLICIT_TAG);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_EXPLICIT_TAG);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Tag '" + NOT_EXISTING_TAG + "' doesn't exist.",
                "Run 'mvn flow:maintenance-start' again and enter name of an existing tag or select another option "
                        + "from the list.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteExplicitEmptyTag() throws Exception {
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
        when(promptControllerMock.prompt(PROMPT_EXPLICIT_TAG)).thenReturn("", "", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock, times(3)).prompt(PROMPT_EXPLICIT_TAG);
        verify(promptControllerMock, times(2)).showMessage("Invalid value. A not blank value is required.");
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
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
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_TWO_TAGS, Arrays.asList("0", "1", "2", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG2_TESTFILE, COMMIT_MESSAGE_TAG1_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG1_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteReleaseVersion() throws Exception {
        // set up
        final String TAG = "v1.2.3";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteReleaseVersionWithVersionTagPrefix() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "v1.2.3";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteReleaseVersionWithoutVersionTagPrefix() throws Exception {
        // set up
        final String TAG = "v1.2.3";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, VERSION_TAG_PREFIX + TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteReleaseVersionNotExistingTag() throws Exception {
        // set up
        final String NOT_EXISTING_TAG = "NotExistingTag";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", NOT_EXISTING_TAG);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Tag '" + NOT_EXISTING_TAG + "' doesn't exist.",
                "Run 'mvn flow:maintenance-start' again with existing tag name in 'releaseVersion' parameter or run in "
                        + "interactive mode to select another option from the list.",
                "'mvn flow:maintenance-start -DreleaseVersion=XXX -B' to run with another tag name",
                "'mvn flow:maintenance-start' to run in interactive mode and to select another option from the list");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteWithMaintenanceVersionAndWithoutFirstMaintenanceVersion() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        userProperties.setProperty("maintenanceVersion", MAINTENANCE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or none.",
                "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                        + "'firstMaintenanceVersion' or none of them.",
                "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                        + "predefine default version used for the branch name and default first project version in "
                        + "maintenance branch",
                "'mvn flow:maintenance-start' to calculate default version used for the branch name and default first "
                        + "project version in maintenance branch automatically based on actual project version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteWithFirstMaintenanceVersionAndWithoutMaintenanceVersion() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or none.",
                "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                        + "'firstMaintenanceVersion' or none of them.",
                "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                        + "predefine default version used for the branch name and default first project version in "
                        + "maintenance branch",
                "'mvn flow:maintenance-start' to calculate default version used for the branch name and default first "
                        + "project version in maintenance branch automatically based on actual project version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteWithMaintenanceVersionAndFirstMaintenanceVersion() throws Exception {
        // set up
        final String PROMPT_PREDEFINED_MAINTENANCE_VERSION = "What is the maintenance version?";
        final String PROMPT_PREDEFINED_MAINTENANCE_FIRST_VERSION = "What is the first version on the maintenance "
                + "branch?";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("maintenanceVersion", MAINTENANCE_VERSION);
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_PREDEFINED_MAINTENANCE_VERSION, MAINTENANCE_VERSION)).thenReturn("");
        when(promptControllerMock.prompt(PROMPT_PREDEFINED_MAINTENANCE_FIRST_VERSION, MAINTENANCE_FIRST_VERSION))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_PREDEFINED_MAINTENANCE_VERSION, MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_PREDEFINED_MAINTENANCE_FIRST_VERSION, MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        // set up
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            git.createAndCommitTestfile(otherRepositorySet);
            git.push(otherRepositorySet);
            when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T")))
                    .thenReturn("0");
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
            verifyNoMoreInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Failed to calculate maintenance versions. The project version '"
                            + TestProjects.INVALID_VERSION.version + "' can't be parsed.",
                    "Check the version of the project or run 'mvn flow:maintenance-start' with specified parameters "
                            + "'maintenanceVersion' and 'firstMaintenanceVersion'.",
                    "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                            + "predefine default version used for the branch name and default first project version in "
                            + "maintenance branch");
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertLocalBranches(otherRepositorySet, MASTER_BRANCH, CONFIG_BRANCH);
            git.assertRemoteBranches(otherRepositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        }
    }

    @Test
    public void testExecuteOnTagWithInvalidProjectVersion() throws Exception {
        // set up
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            final String TAG = VERSION_TAG_PREFIX + "1.0.0";
            git.createTags(otherRepositorySet, TAG);
            git.createAndCommitTestfile(otherRepositorySet);
            git.push(otherRepositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("releaseVersion", TAG);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            verifyNoMoreInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Failed to calculate maintenance versions. The project version '"
                            + TestProjects.INVALID_VERSION.version + "' can't be parsed.",
                    "Check the version of the project or run 'mvn flow:maintenance-start' with specified parameters "
                            + "'maintenanceVersion' and 'firstMaintenanceVersion'.",
                    "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                            + "predefine default version used for the branch name and default first project version in "
                            + "maintenance branch");
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertLocalBranches(otherRepositorySet, MASTER_BRANCH, CONFIG_BRANCH);
            git.assertRemoteBranches(otherRepositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        }
    }

    @Test
    public void testExecuteMaintenanceBranchAlreadyExists() throws Exception {
        // set up
        git.createBranchWithoutSwitch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Maintenance branch '" + MAINTENANCE_BRANCH + "' already exists. Cannot start maintenance.",
                "Either checkout the existing maintenance branch or start a new maintenance with another maintenance "
                        + "version.",
                "'git checkout " + MAINTENANCE_BRANCH + "' to checkout the maintenance branch",
                "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteOnTagAndMaintenanceBranchAlreadyExists() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.createBranchWithoutSwitch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Maintenance branch '" + MAINTENANCE_BRANCH + "' already exists. Cannot start maintenance.",
                "Either checkout the existing maintenance branch or start a new maintenance with another maintenance "
                        + "version.",
                "'git checkout " + MAINTENANCE_BRANCH + "' to checkout the maintenance branch",
                "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteMaintenanceBranchAlreadyExistsRemotely() throws Exception {
        // set up
        git.createRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote maintenance branch '" + MAINTENANCE_BRANCH + "' already exists on the remote 'origin'. "
                        + "Cannot start maintenance.",
                "Either checkout the existing maintenance branch or start a new maintenance with another maintenance "
                        + "version.",
                "'git checkout " + MAINTENANCE_BRANCH + "' to checkout the maintenance branch",
                "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteOnTagAndMaintenanceBranchAlreadyExistsRemotely() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.createRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote maintenance branch '" + MAINTENANCE_BRANCH + "' already exists on the remote 'origin'. "
                        + "Cannot start maintenance.",
                "Either checkout the existing maintenance branch or start a new maintenance with another maintenance "
                        + "version.",
                "'git checkout " + MAINTENANCE_BRANCH + "' to checkout the maintenance branch",
                "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteWithFirstMaintenanceVersionSameAsProjectVersion() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(TestProjects.BASIC.version);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertMaintenanceBranchCratedCorrectlyFromMaster();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteInBatchMode() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, VERSION_TAG_PREFIX + TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        userProperties.setProperty("maintenanceVersion", MAINTENANCE_VERSION);
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteInBatchModeWithoutParameters() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        final String EXPECTED_MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + TestProjects.BASIC.maintenanceVersion;
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, VERSION_TAG_PREFIX + TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EXPECTED_MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EXPECTED_MAINTENANCE_BRANCH,
                EXPECTED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.nextSnepshotVersion);
    }

    @Test
    public void testExecuteInBatchModeWithoutParametersMaintenanceVersionAndFirstMaintenanceVersion() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        final String EXPECTED_MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + TestProjects.BASIC.maintenanceVersion;
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_TAG_TESTFILE = "TAG: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "tag-testfile.txt", COMMIT_MESSAGE_TAG_TESTFILE);
        git.createTags(repositorySet, VERSION_TAG_PREFIX + TAG);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EXPECTED_MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                COMMIT_MESSAGE_TAG_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EXPECTED_MAINTENANCE_BRANCH,
                EXPECTED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_TAG_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.nextSnepshotVersion);
    }

    @Test
    public void testExecuteInBatchModeWithoutParameterFirstMaintenanceVersion() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        userProperties.setProperty("maintenanceVersion", MAINTENANCE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or none.",
                "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                        + "'firstMaintenanceVersion' or none of them.",
                "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                        + "predefine default version used for the branch name and default first project version in "
                        + "maintenance branch",
                "'mvn flow:maintenance-start' to calculate default version used for the branch name and default first "
                        + "project version in maintenance branch automatically based on actual project version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteInBatchModeWithoutParameterMaintenanceVersion() throws Exception {
        // set up
        final String TAG = VERSION_TAG_PREFIX + "1.0.0";
        git.createTags(repositorySet, TAG);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", TAG);
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or none.",
                "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                        + "'firstMaintenanceVersion' or none of them.",
                "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                        + "predefine default version used for the branch name and default first project version in "
                        + "maintenance branch",
                "'mvn flow:maintenance-start' to calculate default version used for the branch name and default first "
                        + "project version in maintenance branch automatically based on actual project version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteInBatchModeWithoutParametersReleaseVersionAndMaintenanceVersion() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or none.",
                "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                        + "'firstMaintenanceVersion' or none of them.",
                "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                        + "predefine default version used for the branch name and default first project version in "
                        + "maintenance branch",
                "'mvn flow:maintenance-start' to calculate default version used for the branch name and default first "
                        + "project version in maintenance branch automatically based on actual project version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
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
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
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

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        git.createAndCommitTestfile(repositorySet);
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"))).thenReturn("0");
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION))
                .thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION))
                .thenReturn(MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_SELECTING_RELEASE_NO_TAGS, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_VERSION, CALCULATED_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_MAINTENANCE_FIRST_VERSION, CALCULATED_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to execute 'mvn clean install' on the project on maintenance branch after maintenance start.",
                "Maintenance branch was created successfully. No further steps with gitflow are required.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteAndCheckIfUpstreamSet() throws Exception {
        Properties userProperties = new Properties();
        userProperties.setProperty("maintenanceVersion", MAINTENANCE_VERSION);
        userProperties.setProperty("firstMaintenanceVersion", MAINTENANCE_FIRST_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertTrackingBranch(repositorySet, "origin/" + MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
    }

}
