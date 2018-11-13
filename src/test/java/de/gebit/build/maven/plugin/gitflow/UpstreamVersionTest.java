//
// UpstreamVersionTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelParseException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.WithUpstreamConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;
import de.gebit.xmlxpath.XML;

/**
 * @author Volodymyr Medvid
 */
public class UpstreamVersionTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL_FEATURE_START = "feature-start";

    private static final String GOAL_FEATURE_REBASE = "feature-rebase";

    private static final String GOAL_FEATURE_FINISH = "feature-finish";

    private static final String GOAL_RELEASE_START = "release-start";

    private static final String GOAL_RELEASE_FINISH = "release-finish";

    private static final String GOAL_RELEASE = "release";

    private static final String PROJECT_VERSION = TestProjects.WITH_UPSTREAM.version;

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String CALCULATED_RELEASE_VERSION = TestProjects.WITH_UPSTREAM.releaseVersion;

    private static final String CALCULATED_NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String OTHER_DEVELOPMENT_VERSION = "3.0.0-SNAPSHOT";

    private static final String FEATURE_BRANCH = WithUpstreamConstants.FEATURE_BRANCH;

    private static final String FEATURE_VERSION = WithUpstreamConstants.FEATURE_VERSION;

    private static final String FEATURE_WITHOUT_VERSION_BRANCH = WithUpstreamConstants.FEATURE_WITHOUT_VERSION_BRANCH;

    private static final String EXPECTED_UPSTREAM_VERSION = WithUpstreamConstants.EXPECTED_UPSTREAM_VERSION;

    private static final String NEW_UPSTREAM_VERSION = WithUpstreamConstants.NEW_UPSTREAM_VERSION;

    private static final String OTHER_PROJECT_VERSION = "1.3.5-SNAPSHOT";

    private static final String OTHER_FEATURE_VERSION = "1.3.5-" + WithUpstreamConstants.FEATURE_ISSUE + "-SNAPSHOT";

    private static final String OTHER_UPSTREAM_VERSION = "2.1.0";

    private static final String PROMPT_UPSTREAM_VERSION_PREFIX = "Enter the version of the Test Parent Pom project to reference.";

    private static final String PROMPT_UPSTREAM_VERSION_SUFFIX = "Enter the version:";

    private static final String PROMPT_UPSTREAM_VERSION_FEATURE_START = PROMPT_UPSTREAM_VERSION_PREFIX + LS + "Hints:\n"
            + "- if you have corresponding feature branch for Test Parent Pom, enter its feature branch version\n"
            + "- if you want to reference in your feature branch a specific version of Test Parent Pom, then enter the version you want to use\n"
            + "- in other case enter the current version of Test Parent Pom on development branch" + LS
            + PROMPT_UPSTREAM_VERSION_SUFFIX;

    private static final String PROMPT_UPSTREAM_VERSION_FEATURE_REBASE = PROMPT_UPSTREAM_VERSION_PREFIX + LS
            + "Hints:\n"
            + "- if you have corresponding feature branch for Test Parent Pom, enter its current feature branch version (ensure the upstream project was rebased before)\n"
            + "- if you reference in your feature branch a specific version of Test Parent Pom, then enter the version you want to use\n"
            + "- in other case enter the current version of Test Parent Pom on development branch" + LS
            + PROMPT_UPSTREAM_VERSION_SUFFIX;

    // private static final String PROMPT_UPSTREAM_VERSION_FEATURE_FINISH =
    // PROMPT_UPSTREAM_VERSION_PREFIX + LS + "Hints:\n"
    // + "- if you referenced in your feature branch a specific version of
    // ${project.upstream}, enter the version you want to use now on development
    // branch\n"
    // + "- in other case enter the current version of ${project.upstream} on
    // development branch" + LS
    // + PROMPT_UPSTREAM_VERSION_SUFFIX;

    private static final String PROMPT_UPSTREAM_VERSION_RELEASE_START = PROMPT_UPSTREAM_VERSION_PREFIX + LS + "Hints:\n"
            + "- if you released Test Parent Pom before, enter its release version\n"
            + "- if the project references a specific non-snapshot version of Test Parent Pom, enter this version\n"
            + "- do not enter SNAPSHOT and I-Build versions here!" + LS + PROMPT_UPSTREAM_VERSION_SUFFIX;

    private static final String PROMPT_UPSTREAM_VERSION_RELEASE_FINISH = PROMPT_UPSTREAM_VERSION_PREFIX + LS
            + "Hints:\n"
            + "- if you want to reference in your development branch a specific version of Test Parent Pom, then enter the version you want to use\n"
            + "- in other case enter the current version of Test Parent Pom on development branch" + LS
            + PROMPT_UPSTREAM_VERSION_SUFFIX;

    // private static final String PROMPT_UPSTREAM_VERSION_ON_RELEASE_START_BRANCH =
    // "On release branch: "
    // + PROMPT_UPSTREAM_VERSION_PREFIX;
    //
    // private static final String PROMPT_UPSTREAM_VERSION_ON_RELEASE_FINISH_BRANCH
    // = "Next development version: "
    // + PROMPT_UPSTREAM_VERSION_PREFIX;

    private static final String PROMPT_RELEASE_VERSION = ExecutorHelper.RELEASE_START_PROMPT_RELEASE_VERSION;

    private static final String PROMPT_NEXT_DEVELOPMENT_VERSION = "What is the next development version?";

    private static final String COMMIT_MESSAGE_SET_VERSION = WithUpstreamConstants.FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_FEATURE_WITHOUT_VERSION_SET_VERSION = WithUpstreamConstants.FEATURE_WITHOUT_VERSION_ISSUE
            + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development "
            + "version";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.WITH_UPSTREAM);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    private void executeMojoInteractive(String goal) throws Exception {
        executeMojoInteractive(goal, null);
    }

    private void executeMojoInteractive(String goal, Properties userProperties) throws Exception {
        executeMojo(repositorySet.getWorkingDirectory(), goal, userProperties, promptControllerMock);
    }

    private void executeMojoInBatchMode(String goal) throws Exception {
        executeMojoInBatchMode(goal, null);
    }

    private void executeMojoInBatchMode(String goal, Properties userProperties) throws Exception {
        executeMojo(repositorySet.getWorkingDirectory(), goal, userProperties);
    }

    private void assertVersions(String projectVersion, String parentVersion)
            throws ModelParseException, ComponentLookupException, IOException {
        Model pom = readPom(repositorySet.getWorkingDirectory());
        assertProjectVersionInPom(pom, projectVersion);
        assertVersionBuildPropertyInPom(pom, projectVersion);
        assertParentVersionsInPom(pom, parentVersion);
        for (String module : pom.getModules()) {
            Model pomModule = readPom(new File(repositorySet.getWorkingDirectory(), module));
            assertProjectVersionInPom(pomModule, projectVersion);
            assertParentVersionsInPom(pomModule, projectVersion);
        }
    }

    @Test
    public void testExecuteFeatureStart() throws Exception {
        // set up
        final String USED_FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";
        final String USED_FEATURE_NAME = USED_FEATURE_ISSUE + "-someDescription";
        final String USED_FEATURE_BRANCH = "feature/" + USED_FEATURE_NAME;
        final String EXPECTED_FEATURE_VERSION = TestProjects.WITH_MODULES.releaseVersion + "-" + USED_FEATURE_ISSUE
                + "-SNAPSHOT";
        when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                .thenReturn(USED_FEATURE_NAME);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_FEATURE_START, EXPECTED_UPSTREAM_VERSION))
                .thenReturn(NEW_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_START);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_FEATURE_START, EXPECTED_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersions(EXPECTED_FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertEquals(NEW_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));
    }

    @Test
    public void testExecuteFeatureRebaseSameVersionAndFeatureWithoutCommits() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);

        assertVersions(FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureRebaseSameVersion() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        assertVersions(FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureRebaseChangedVersion() throws Exception {
        // set up
        setVersion(OTHER_PROJECT_VERSION);
        git.commitAll(repositorySet, "update project version");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_FEATURE_REBASE, NEW_UPSTREAM_VERSION))
                .thenReturn(OTHER_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_FEATURE_REBASE, NEW_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        assertVersions(OTHER_FEATURE_VERSION, OTHER_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(OTHER_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(OTHER_PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    private void setVersion(String version) throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(repositorySet.getWorkingDirectory());
        pom.setVersion(version);
        pom.getProperties().setProperty("version.build", version);
        writePom(repositorySet.getWorkingDirectory(), pom);
        for (String module : pom.getModules()) {
            File basedirModule = new File(repositorySet.getWorkingDirectory(), module);
            Model pomModule = readPom(basedirModule);
            pomModule.setVersion(version);
            pomModule.getParent().setVersion(version);
            writePom(basedirModule, pomModule);
        }
    }

    private void setUpstreamVersion(String version) throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(repositorySet.getWorkingDirectory());
        pom.getParent().setVersion(version);
        writePom(repositorySet.getWorkingDirectory(), pom);
    }

    @Test
    public void testExecuteFeatureRebaseChangedUpstreamVersion() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        setUpstreamVersion(OTHER_UPSTREAM_VERSION);
        git.commitAll(repositorySet, "update upstream version");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        assertVersions(FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureRebaseNewModule() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        addNewModule("module3", "module1");
        git.commitAll(repositorySet, "added module3");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        assertVersions(FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    private void addNewModule(String newModule, String moduleToBeCopied) throws Exception {
        File newModuleDir = new File(repositorySet.getWorkingDirectory(), newModule);
        newModuleDir.mkdirs();

        Model pomModule = readPom(new File(repositorySet.getWorkingDirectory(), moduleToBeCopied));
        pomModule.setArtifactId(newModule);
        writePom(newModuleDir, pomModule);

        Model pom = readPom(repositorySet.getWorkingDirectory());
        pom.addModule(newModule);
        writePom(repositorySet.getWorkingDirectory(), pom);
    }

    @Test
    public void testExecuteFeatureRebaseNewModuleTwiceAndChangedVersion() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        addNewModule("module3", "module1");
        git.commitAll(repositorySet, "added module3");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        executeMojoInteractive(GOAL_FEATURE_REBASE);

        git.createAndCommitTestfile(repositorySet, "feature-testfile2.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        addNewModule("module4", "module1");
        setVersion(OTHER_PROJECT_VERSION);
        git.commitAll(repositorySet, "added module4 and changed version");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_FEATURE_REBASE, NEW_UPSTREAM_VERSION))
                .thenReturn(OTHER_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_FEATURE_REBASE, NEW_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD~2");

        assertVersions(OTHER_FEATURE_VERSION, OTHER_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(OTHER_UPSTREAM_VERSION, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(OTHER_PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureRebaseSameVersionStartedWithoutFeatureVersion() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);

        assertVersions(PROJECT_VERSION, EXPECTED_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                FEATURE_WITHOUT_VERSION_BRANCH);
        assertEquals(null, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_FEATURE_WITHOUT_VERSION_SET_VERSION,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureRebaseChangedVersionStartedWithoutFeatureVersion() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        setVersion(OTHER_PROJECT_VERSION);
        setUpstreamVersion(OTHER_UPSTREAM_VERSION);
        git.commitAll(repositorySet, "update project and upstream versions");
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_WITHOUT_VERSION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verifyZeroInteractions(promptControllerMock);

        assertVersions(OTHER_PROJECT_VERSION, OTHER_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                FEATURE_WITHOUT_VERSION_BRANCH);
        assertEquals(null, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_FEATURE_WITHOUT_VERSION_SET_VERSION,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteReleaseStartWithUpstreamProperty() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_RELEASE_START, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        assertVersions(RELEASE_VERSION, EXPECTED_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseStartWithUpstreamPropertyAndProcessAdditionalVersionCommandsTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInteractive(GOAL_RELEASE_START, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseStartWithoutUpstreamPropertyAndProcessAdditionalVersionCommandsTrue()
            throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_RELEASE_START, EXPECTED_UPSTREAM_VERSION))
                .thenReturn(NEW_UPSTREAM_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInteractive(GOAL_RELEASE_START, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_RELEASE_START, EXPECTED_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseStartWithUpstreamPropertyAndProcessAdditionalVersionCommandsTrueInBatchMode()
            throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInBatchMode(GOAL_RELEASE_START, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseStartWithoutUpstreamPropertyAndProcessAdditionalVersionCommandsTrueInBatchMode()
            throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInBatchMode(GOAL_RELEASE_START, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        assertVersions(RELEASE_VERSION, EXPECTED_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseFinishWithUpstreamProperty() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = WithUpstreamConstants.EXISTING_RELEASE_BRANCH;
        final String USED_RELEASE_TAG = "gitflow-tests-" + WithUpstreamConstants.EXISTING_RELEASE_VERSION;
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION,
                WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION)).thenReturn(OTHER_DEVELOPMENT_VERSION);
        // test
        executeMojoInteractive(GOAL_RELEASE_FINISH, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION,
                WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, USED_RELEASE_TAG);
        git.assertRemoteTags(repositorySet, USED_RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        assertVersions(OTHER_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteReleaseFinishWithoutUpstreamProperty() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = WithUpstreamConstants.EXISTING_RELEASE_BRANCH;
        final String USED_RELEASE_TAG = "gitflow-tests-" + WithUpstreamConstants.EXISTING_RELEASE_VERSION;
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION,
                WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION)).thenReturn(OTHER_DEVELOPMENT_VERSION);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_RELEASE_FINISH, EXPECTED_UPSTREAM_VERSION))
                .thenReturn(NEW_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_RELEASE_FINISH);
        // verify
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION,
                WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_RELEASE_FINISH, EXPECTED_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, USED_RELEASE_TAG);
        git.assertRemoteTags(repositorySet, USED_RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        assertVersions(OTHER_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteReleaseFinishWithUpstreamPropertyInBatchMode() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = WithUpstreamConstants.EXISTING_RELEASE_BRANCH;
        final String USED_RELEASE_TAG = "gitflow-tests-" + WithUpstreamConstants.EXISTING_RELEASE_VERSION;
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        // test
        executeMojoInBatchMode(GOAL_RELEASE_FINISH, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, USED_RELEASE_TAG);
        git.assertRemoteTags(repositorySet, USED_RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        assertVersions(WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteReleaseFinishWithoutUpstreamPropertyInBatchMode() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = WithUpstreamConstants.EXISTING_RELEASE_BRANCH;
        final String USED_RELEASE_TAG = "gitflow-tests-" + WithUpstreamConstants.EXISTING_RELEASE_VERSION;
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojoInBatchMode(GOAL_RELEASE_FINISH);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, USED_RELEASE_TAG);
        git.assertRemoteTags(repositorySet, USED_RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        assertVersions(WithUpstreamConstants.EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION, EXPECTED_UPSTREAM_VERSION);

        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteReleaseWithUpstreamProperty() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION))
                .thenReturn(OTHER_DEVELOPMENT_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_RELEASE, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(OTHER_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, EXPECTED_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseWithUpstreamPropertyAndProcessAdditionalVersionCommandsTrue() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION))
                .thenReturn(OTHER_DEVELOPMENT_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInteractive(GOAL_RELEASE, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(OTHER_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseWithoutUpstreamPropertyAndProcessAdditionalVersionCommandsTrue() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_RELEASE_START, EXPECTED_UPSTREAM_VERSION))
                .thenReturn(NEW_UPSTREAM_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION))
                .thenReturn(OTHER_DEVELOPMENT_VERSION);
        when(promptControllerMock.prompt(eq(PROMPT_UPSTREAM_VERSION_RELEASE_FINISH), anyString()))
                .thenReturn(OTHER_UPSTREAM_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInteractive(GOAL_RELEASE, userProperties);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, CALCULATED_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_RELEASE_START, EXPECTED_UPSTREAM_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, CALCULATED_NEW_DEVELOPMENT_VERSION);
        verify(promptControllerMock).prompt(eq(PROMPT_UPSTREAM_VERSION_RELEASE_FINISH), anyString());
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(OTHER_DEVELOPMENT_VERSION, OTHER_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseWithUpstreamPropertyInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        // test
        executeMojoInBatchMode(GOAL_RELEASE, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(CALCULATED_NEW_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, EXPECTED_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseWithUpstreamPropertyAndProcessAdditionalVersionCommandsTrueInBatchMode()
            throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("version.upstream", NEW_UPSTREAM_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInBatchMode(GOAL_RELEASE, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(CALCULATED_NEW_DEVELOPMENT_VERSION, NEW_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, NEW_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteReleaseWithoutUpstreamPropertyAndProcessAdditionalVersionCommandsTrueInBatchMode()
            throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("flow.additionalVersionCommands.contexts", "VERSION,INTERNAL,RELEASE");
        // test
        executeMojoInBatchMode(GOAL_RELEASE, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_RELEASE);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseBranch");

        assertVersions(CALCULATED_NEW_DEVELOPMENT_VERSION, EXPECTED_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        assertVersions(RELEASE_VERSION, EXPECTED_UPSTREAM_VERSION);
    }

    @Test
    public void testExecuteFeatureFinishAfterChangedParentVersion_GBLD514() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = WithUpstreamConstants.FEATURE_WITH_OLD_UPSTREAM_BRANCH;
        final String COMMIT_MESSAGE_UPSTREAM_UPDATE = "update parent version";
        final String COMMIT_MESSAGE_MERGE = TestProjects.WITH_UPSTREAM.jiraProject + "-NONE: Merge branch "
                + USED_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);

        File pom = new File(repositorySet.getWorkingDirectory(), "pom.xml");
        XML pomXML = XML.load(pom);
        pomXML.setValue("/project/parent/version", WithUpstreamConstants.UPSTREAM_FEATURE_VERSION);
        pomXML.setValue("/project/parent/relativePath", WithUpstreamConstants.UPSTREAM_FEATURE_RELATIVE_PATH);
        pomXML.store();
        git.commitAll(repositorySet, COMMIT_MESSAGE_UPSTREAM_UPDATE);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojoInteractive(GOAL_FEATURE_FINISH);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_UPSTREAM_UPDATE);
        assertVersions(PROJECT_VERSION, WithUpstreamConstants.UPSTREAM_FEATURE_VERSION);
    }
}
