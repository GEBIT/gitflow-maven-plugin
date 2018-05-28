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
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class UpstreamVersionTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL_FEATURE_START = "feature-start";

    private static final String GOAL_FEATURE_REBASE = "feature-rebase";

    private static final String PROJECT_VERSION = TestProjects.WITH_UPSTREAM.version;

    private static final String FEATURE_BRANCH = WithUpstreamConstants.FEATURE_BRANCH;

    private static final String FEATURE_VERSION = WithUpstreamConstants.FEATURE_VERSION;

    private static final String FEATURE_WITHOUT_VERSION_BRANCH = WithUpstreamConstants.FEATURE_WITHOUT_VERSION_BRANCH;

    private static final String EXPECTED_UPSTREAM_VERSION = WithUpstreamConstants.EXPECTED_UPSTREAM_VERSION;

    private static final String NEW_UPSTREAM_VERSION = WithUpstreamConstants.NEW_UPSTREAM_VERSION;

    private static final String OTHER_PROJECT_VERSION = "1.3.5-SNAPSHOT";

    private static final String OTHER_FEATURE_VERSION = "1.3.5-" + WithUpstreamConstants.FEATURE_ISSUE + "-SNAPSHOT";

    private static final String OTHER_UPSTREAM_VERSION = "2.1.0";

    private static final String PROMPT_UPSTREAM_VERSION = "Enter the version of the Test Parent Pom project to reference";

    private static final String PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH = "On feature branch: "
            + PROMPT_UPSTREAM_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = WithUpstreamConstants.FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_FEATURE_WITHOUT_VERSION_SET_VERSION = WithUpstreamConstants.FEATURE_WITHOUT_VERSION_ISSUE
            + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

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
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, EXPECTED_UPSTREAM_VERSION))
                .thenReturn(NEW_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_START);
        // verify
        verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, EXPECTED_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        assertVersions(EXPECTED_FEATURE_VERSION, NEW_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, NEW_UPSTREAM_VERSION))
                .thenReturn(OTHER_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, NEW_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        assertVersions(OTHER_FEATURE_VERSION, OTHER_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, NEW_UPSTREAM_VERSION))
                .thenReturn(OTHER_UPSTREAM_VERSION);
        // test
        executeMojoInteractive(GOAL_FEATURE_REBASE);
        // verify
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, NEW_UPSTREAM_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD~2");

        assertVersions(OTHER_FEATURE_VERSION, OTHER_UPSTREAM_VERSION);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
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

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH,
                FEATURE_WITHOUT_VERSION_BRANCH);
        assertEquals(null, branchConfig.getProperty("additionalVersionParameter0"));

        assertEquals(PROJECT_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_FEATURE_WITHOUT_VERSION_SET_VERSION,
                branchConfig.getProperty("startCommitMessage"));
        assertEquals(null, branchConfig.getProperty("versionChangeCommit"));
    }

}
