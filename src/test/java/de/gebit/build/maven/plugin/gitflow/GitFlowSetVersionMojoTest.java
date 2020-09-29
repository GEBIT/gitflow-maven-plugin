//
// GitFlowSetVersionMojoTest.java
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowSetVersionMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "set-version";

    private static final String POM_NEXT_RELEASE_VERSION = TestProjects.BASIC.nextReleaseVersion;

    private static final String NEW_VERSION = "1.42.0-SNAPSHOT";

    private static final String UPSTREAM_VERSION = "2.3.4-SNAPSHOT";

    private static final String EXPECTED_UPSTREAM_VERSION_DEFAULT = "3.3.3-default";

    private static final String EXPECTED_UPSTREAM_VERSION = "3.2.1";

    private static final String INTERPOLATION_CYCLE_PROPERTY_VALUE = "@{${interpolation.cycle.property}}";

    private static final String PROMPT_NEW_VERSION = "What is the new version?";

    private static final String PROMPT_UPSTREAM_VERSION = "Enter the version of the Test Parent Pom project to reference.";

    private static final String PROMPT_ENABLE_UPDATE = "Should property version.test-parent-pom be updated to project version?";

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
    public void testExecuteInInteractiveMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_NEW_VERSION, POM_NEXT_RELEASE_VERSION)).thenReturn(NEW_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_NEW_VERSION, POM_NEXT_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, true);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    private void assertSetVersionMavenCommandExecution(String version, boolean executed) throws IOException {
        assertMavenCommandExecution("org.codehaus.mojo:versions-maven-plugin:2.5:set -DnewVersion=" + version
                + " -DgenerateBackupPoms=false", executed);
    }

    private void assertCommandsAfterVersionMavenCommandExecution(boolean executed) throws IOException {
        assertMavenCommandExecution("version-stamper:stamp -N", executed);
    }

    private void assertSetVersionTychoMavenCommandExecution(String version, boolean executed) throws IOException {
        assertSetVersionTychoMavenCommandExecution(version, executed, "1.4.0");
    }

    private void assertSetVersionTychoMavenCommandExecution(String version, boolean executed, String tychoVersion) throws IOException {
        assertMavenCommandExecution("org.eclipse.tycho:tycho-versions-plugin:" + tychoVersion
                + ":set-version -DnewVersion=" + version + " -Dtycho.mode=maven", executed);
    }

    @Test
    public void testExecuteWithNewVersionInInteractiveMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, true);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteInBatchMode() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Property 'newVersion' is required in non-interactive mode but was not set.",
                "Specify a new version or run in interactive mode.",
                "'mvn flow:set-version -DnewVersion=X.Y.Z-SNAPSHOT -B' to predifine new version",
                "'mvn flow:set-version' to run in interactive mode");
        git.assertClean(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteWithNewVersionInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertSetVersionMavenCommandExecution(NEW_VERSION, true);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_NEW_VERSION)).thenReturn(NEW_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_NEW_VERSION);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
        }
    }

    @Test
    public void testExecuteTychoBuildTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteTychoBuildTrueAndTychoVersionUserProperty() throws Exception {
        // set up
        final String TYCHO_VERSION = "1.3.0";
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.tychoBuild", "true");
        userProperties.setProperty("version.tycho", TYCHO_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true, TYCHO_VERSION);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteTychoBuildTrueAndTychoVersionProperty() throws Exception {
        // set up
        final String TYCHO_VERSION = "1.3.0";
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.tychoBuild", "true");
        Model pom = readPom(repositorySet.getWorkingDirectory());
        pom.getProperties().setProperty("version.tycho", TYCHO_VERSION);
        writePom(repositorySet.getWorkingDirectory(), pom);
        git.commitAll(repositorySet, "added property");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true, TYCHO_VERSION);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteTychoBuildTrueAndTychoVersionInPluginManagement() throws Exception {
        // set up
        final String TYCHO_VERSION = "1.3.0";
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.tychoBuild", "true");
        Model pom = readPom(repositorySet.getWorkingDirectory());
        addTychoPluginToPluginManagement(pom, TYCHO_VERSION);
        writePom(repositorySet.getWorkingDirectory(), pom);
        git.commitAll(repositorySet, "added property");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true, TYCHO_VERSION);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    private void addTychoPluginToPluginManagement(Model pom, String tychoVersion) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        PluginManagement pluginManagement = build.getPluginManagement();
        if (pluginManagement == null) {
            pluginManagement = new PluginManagement();
            build.setPluginManagement(pluginManagement);
        }
        pluginManagement.addPlugin(createTychoPlugin(tychoVersion));
    }

    @Test
    public void testExecuteTychoBuildTrueAndTychoVersionInPlugins() throws Exception {
        // set up
        final String TYCHO_VERSION = "1.3.0";
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.tychoBuild", "true");
        Model pom = readPom(repositorySet.getWorkingDirectory());
        addTychoPluginToPlugins(pom, TYCHO_VERSION);
        writePom(repositorySet.getWorkingDirectory(), pom);
        git.commitAll(repositorySet, "added property");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true, TYCHO_VERSION);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    private void addTychoPluginToPlugins(Model pom, String tychoVersion) {
        Build build = pom.getBuild();
        if (build == null) {
            build = new Build();
            pom.setBuild(build);
        }
        List<Plugin> plugins = build.getPlugins();
        if (plugins == null) {
            plugins = new ArrayList<>();
            build.setPlugins(plugins);
        }
        plugins.add(createTychoPlugin(tychoVersion));
    }

    private Plugin createTychoPlugin(String tychoVersion) {
        Plugin tychoPlugin = new Plugin();
        tychoPlugin.setGroupId("org.eclipse.tycho");
        tychoPlugin.setArtifactId("tycho-versions-plugin");
        tychoPlugin.setVersion(tychoVersion);
        return tychoPlugin;
    }
    
    @Test
    public void testExecuteNoCommandsAfterVersion() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("newVersion", NEW_VERSION);
        userProperties.setProperty("flow.commandsAfterVersion", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertSetVersionMavenCommandExecution(NEW_VERSION, true);
        assertCommandsAfterVersionMavenCommandExecution(false);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
        assertProjectVersionInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
        assertVersionBuildPropertyInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnProjectWithModules() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
        }
    }

    @Test
    public void testExecuteTychoBuildTrueOnProjectWithModules() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("flow.tychoBuild", "true");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, false);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, true);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
        }
    }

    @Test
    public void testExecuteNoAdditionalVersionCommands() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties,
                    TestProjects.PROFILE_SET_VERSION_WITHOUT_ADDITIONAL_VERSION_COMMANDS);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
        }
    }

    @Test
    public void testExecuteWithAdditionalVersionCommandInBatchModeAndDefaultVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    EXPECTED_UPSTREAM_VERSION_DEFAULT);
        }
    }

    @Ignore("Enable after fix: @{value} used if devaultVersion=null in batch mode")
    @Test
    public void testExecuteWithAdditionalVersionCommandInBatchModeAndMissingDefaultVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_DEFAULT);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteWithAdditionalVersionCommandInBatchModeAndVersionInProperty() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            userProperties.setProperty("version.upstream", UPSTREAM_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    UPSTREAM_VERSION);
        }
    }

    @Test
    public void testExecuteWithAdditionalVersionCommandInInteractiveMode() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION, EXPECTED_UPSTREAM_VERSION_DEFAULT))
                    .thenReturn(UPSTREAM_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION, EXPECTED_UPSTREAM_VERSION_DEFAULT);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    UPSTREAM_VERSION);
        }
    }

    @Test
    public void testExecuteWithAdditionalVersionCommandInInteractiveModeAndMissingDefaultVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION)).thenReturn(UPSTREAM_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_DEFAULT);
            // verify
            verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    UPSTREAM_VERSION);
        }
    }

    @Test
    public void testExecuteWithNewLineCharactersInAdditionalVersionCommand() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("version.upstream.property", "version.test-parent-pom");
            userProperties.setProperty("version.upstream", UPSTREAM_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_NEW_LINE_CHARACTERS);
            // verify
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    UPSTREAM_VERSION);
        }
    }

    @Test
    public void testExecuteUpdatingParentVersionBasedOnUpstream() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION, EXPECTED_UPSTREAM_VERSION)).thenReturn("");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_WITH_UPSTREAM);
            // verify
            verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION, EXPECTED_UPSTREAM_VERSION);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, NEW_VERSION);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
            assertParentVersionsInPom(pom, EXPECTED_UPSTREAM_VERSION);
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithoutPrompt() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_PROMPT);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    NEW_VERSION);
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandEnabledByPromptAnswerYes() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            when(promptControllerMock.prompt(PROMPT_ENABLE_UPDATE)).thenReturn("yes");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_ENABLED_BY_PROMPT);
            // verify
            verify(promptControllerMock).prompt(PROMPT_ENABLE_UPDATE);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    NEW_VERSION);
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandEnabledByPromptAnswerTrue() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            when(promptControllerMock.prompt(PROMPT_ENABLE_UPDATE)).thenReturn("true");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_ENABLED_BY_PROMPT);
            // verify
            verify(promptControllerMock).prompt(PROMPT_ENABLE_UPDATE);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(otherRepositorySet.getWorkingDirectory(), "version.test-parent-pom",
                    NEW_VERSION);
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandEnabledByPromptAnswerNo() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            when(promptControllerMock.prompt(PROMPT_ENABLE_UPDATE)).thenReturn("no");
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_ENABLED_BY_PROMPT);
            // verify
            verify(promptControllerMock).prompt(PROMPT_ENABLE_UPDATE);
            verifyNoMoreInteractions(promptControllerMock);
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithInterpolationCycleInDefaultValueInBatchMode() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("avc.defaultValue", INTERPOLATION_CYCLE_PROPERTY_VALUE);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE);
            // verify
            assertGitFlowFailureException(result,
                    "Expression cycle detected in additionalVersionCommand parameter 'defaultValue'. "
                            + "Versions can't be updated.",
                    "Please modify the parameter value to avoid cylces.");
            assertSetVersionMavenCommandExecution(NEW_VERSION, false);
            assertCommandsAfterVersionMavenCommandExecution(false);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertClean(otherRepositorySet);
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"),
                    TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"),
                    TestProjects.BASIC.version);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithInterpolationCycleInDefaultValue() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("avc.defaultValue", INTERPOLATION_CYCLE_PROPERTY_VALUE);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Expression cycle detected in additionalVersionCommand parameter 'defaultValue'. "
                            + "Versions can't be updated.",
                    "Please modify the parameter value to avoid cylces.");
            assertSetVersionMavenCommandExecution(NEW_VERSION, false);
            assertCommandsAfterVersionMavenCommandExecution(false);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertClean(otherRepositorySet);
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"),
                    TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"),
                    TestProjects.BASIC.version);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithInterpolationCycleInPrompt() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("avc.prompt", INTERPOLATION_CYCLE_PROPERTY_VALUE);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Expression cycle detected in additionalVersionCommand parameter 'prompt'. "
                            + "Versions can't be updated.",
                    "Please modify the parameter value to avoid cylces.");
            assertSetVersionMavenCommandExecution(NEW_VERSION, false);
            assertCommandsAfterVersionMavenCommandExecution(false);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertClean(otherRepositorySet);
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"),
                    TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"),
                    TestProjects.BASIC.version);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithInterpolationCycleInCommand() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("avc.command", INTERPOLATION_CYCLE_PROPERTY_VALUE);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE);
            // verify
            assertGitFlowFailureException(result,
                    "Expression cycle detected in additionalVersionCommand parameter 'command'. "
                            + "Versions can't be updated.",
                    "Please modify the parameter value to avoid cylces.");
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(false);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertProjectVersionInPom(pom, NEW_VERSION);
            assertVersionBuildPropertyInPom(pom, TestProjects.BASIC.version);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }

    @Test
    public void testExecuteAdditionalVersionCommandWithNotExecutableCommand() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.WITH_MODULES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("newVersion", NEW_VERSION);
            userProperties.setProperty("avc.command", "notExecutableCommand");
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties,
                    TestProjects.PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE);
            // verify
            assertGitFlowFailureExceptionRegEx(result,
                    new GitFlowFailureInfo(
                            "\\QFailed to execute additional version maven command: notExecutableCommand\n"
                                    + "Maven error message:\n\\E.*",
                            "\\QPlease specify executable additional version maven command.\\E"));
            assertSetVersionMavenCommandExecution(NEW_VERSION, true);
            assertCommandsAfterVersionMavenCommandExecution(true);
            assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml", "module1/pom.xml", "module2/pom.xml");
            Model pom = readPom(otherRepositorySet.getWorkingDirectory());
            assertVersionsInPom(pom, NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module1"), NEW_VERSION);
            assertProjectVersionInPom(new File(otherRepositorySet.getWorkingDirectory(), "module2"), NEW_VERSION);
            assertCustomVersionPropertyInPom(pom, "version.test-parent-pom", "");
        }
    }
}
