//
// GitFlowBuildVersionMojoTest.java
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

import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowBuildVersionMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "build-version";

    private static final String BUILD_VERSION = "BUILD42";

    private static final String PROMPT_BUILD_VERSION_PREFIX = "What is build version? ";

    private static final String BUILD_VERSION_PREFIX = TestProjects.BASIC.releaseVersion + "-";

    private static final String TYCHO_BUILD_VERSION_PREFIX = TestProjects.BASIC.releaseVersion + ".";

    private static final String PROMPT_BUILD_VERSION = PROMPT_BUILD_VERSION_PREFIX + BUILD_VERSION_PREFIX;

    private static final String PROMPT_TYCHO_BUILD_VERSION = PROMPT_BUILD_VERSION_PREFIX + TYCHO_BUILD_VERSION_PREFIX;

    private static final String NEW_VERSION = BUILD_VERSION_PREFIX + BUILD_VERSION;

    private static final String NEW_TYCHO_VERSION = TYCHO_BUILD_VERSION_PREFIX + BUILD_VERSION;

    private static final String TYCHO_BUILD_FOUR_DIGITS_VERSION_PREFIX = TestProjects.TYCHO_PROJECT.releaseVersion
            + "_";

    private static final String PROMPT_TYCHO_BUILD_FOUR_DIGITS_VERSION = PROMPT_BUILD_VERSION_PREFIX
            + TYCHO_BUILD_FOUR_DIGITS_VERSION_PREFIX;

    private static final String NEW_TYCHO_FOUR_DIGITS_VERSION = TYCHO_BUILD_FOUR_DIGITS_VERSION_PREFIX + BUILD_VERSION;

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
    public void testExecute() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_BUILD_VERSION)).thenReturn(BUILD_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_BUILD_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteTychoBuildTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        when(promptControllerMock.prompt(PROMPT_TYCHO_BUILD_VERSION)).thenReturn(BUILD_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_TYCHO_BUILD_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_TYCHO_VERSION);
    }

    @Test
    public void testExecuteTychoBuildTrueAndFourDigitsVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.TYCHO_PROJECT.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_TYCHO_BUILD_FOUR_DIGITS_VERSION)).thenReturn(BUILD_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_TYCHO_BUILD_FOUR_DIGITS_VERSION);
            verifyNoMoreInteractions(promptControllerMock);
            git.assertModifiedFiles(otherRepositorySet, "pom.xml");
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), NEW_TYCHO_FOUR_DIGITS_VERSION);
        }
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Failed to calculate base version for build version. The project version '"
                            + TestProjects.INVALID_VERSION.version + "' can't be parsed.",
                    "Check the version of the project.\n"
                            + "'mvn flow:build-version' can not be used for projectes with invalid version.");
            git.assertClean(otherRepositorySet);
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), TestProjects.INVALID_VERSION.version);
        }
    }

    @Test
    public void testExecuteWithBuildVersion() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("buildVersion", BUILD_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteWithBuildVersionInBatchMode() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("buildVersion", BUILD_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertModifiedFiles(repositorySet, "pom.xml");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
    }

    @Test
    public void testExecuteWithoutBuildVersionInBatchMode() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Property 'buildVersion' is required in non-interactive mode but was not set.",
                "Specify a buildVersion or run in interactive mode.",
                "'mvn flow:build-version -DbuildVersion=XXX -B' to predefine build version",
                "'mvn flow:build-version' to run in interactive mode");
        git.assertClean(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }
}
