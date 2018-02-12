//
// ExecutorHelper.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.codehaus.plexus.components.interactivity.Prompter;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * The helper class to execute some goals of the plugin.
 *
 * @author VMedvid
 */
public class ExecutorHelper {

    private static final String LS = System.getProperty("line.separator");

    public static final String FEATURE_START_PROMPT_FEATURE_BRANCH_NAME = "What is a name of feature branch? feature/";

    public static final String HOTFIX_START_POM_NEXT_RELEASE_VERSION = TestProjects.BASIC.nextReleaseVersion;

    public static final String HOTFIX_START_PROMPT_HOTFIX_VERSION = "What is the hotfix version? ["
            + HOTFIX_START_POM_NEXT_RELEASE_VERSION + "]";

    public static final String RELEASE_START_POM_RELEASE_VERSION = TestProjects.BASIC.releaseVersion;

    public static final String RELEASE_START_PROMPT_RELEASE_VERSION = "What is the release version? ["
            + RELEASE_START_POM_RELEASE_VERSION + "]";

    public static final String MAINTENANCE_START_PROMPT_SELECTING_RELEASE = "Release:" + LS + "0. <current commit>" + LS
            + "T. <prompt for explicit tag name>" + LS
            + "Choose release to create the maintenance branch from or enter a custom tag or release name";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION = "What is the maintenance version? ["
            + TestProjects.BASIC.maintenanceVersion + "]";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION = "What is the first version on the maintenance branch? ["
            + TestProjects.BASIC.nextSnepshotVersion + "]";

    public static final String MAINTENANCE_START_MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    public static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String featureNumber) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(FEATURE_START_PROMPT_FEATURE_BRANCH_NAME)).thenReturn(featureNumber);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-start", promptControllerMock);
        verify(promptControllerMock).prompt(FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
        verifyNoMoreInteractions(promptControllerMock);
    }

    public static void executeHotfixStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String hotfixVersion) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(HOTFIX_START_PROMPT_HOTFIX_VERSION)).thenReturn(hotfixVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "hotfix-start", promptControllerMock);
        verify(promptControllerMock).prompt(HOTFIX_START_PROMPT_HOTFIX_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
    }

    public static void executeReleaseStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String releaseVersion) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(RELEASE_START_PROMPT_RELEASE_VERSION)).thenReturn(releaseVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "release-start", promptControllerMock);
        verify(promptControllerMock).prompt(RELEASE_START_PROMPT_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
    }

    public static void executeMaintenanceStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String maintenanceVersion) throws Exception {
        executeMaintenanceStart(testCase, repositorySet, maintenanceVersion,
                MAINTENANCE_START_MAINTENANCE_FIRST_VERSION);
    }

    public static void executeMaintenanceStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String maintenanceVersion, String maintenanceSnapshotVersion) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_SELECTING_RELEASE, Arrays.asList("0", "T")))
                .thenReturn("0");
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION)).thenReturn(maintenanceVersion);
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION))
                .thenReturn(maintenanceSnapshotVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "maintenance-start", promptControllerMock);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_SELECTING_RELEASE, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
    }
}
