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

    public static final String FEATURE_START_FEATURE_NUMBER = "GBLD-42";

    public static final String FEATURE_START_COMMIT_MESSAGE_SET_VERSION =
            FEATURE_START_FEATURE_NUMBER + ": updating versions for feature branch";

    public static final String MAINTENANCE_START_PROMPT_SELECTING_RELEASE = "Release:" + LS
            + "0. <current commit>" + LS
            + "T. <prompt for explicit tag name>" + LS
            + "Choose release to create the maintenance branch from or enter a custom tag or release name";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION = "What is the maintenance version? [1.2]";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION =
            "What is the first version on the maintenance branch? [1.2.4-SNAPSHOT]";

    public static final String MAINTENANCE_START_MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT]";

    public static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet)
            throws Exception {
        executeFeatureStart(testCase, repositorySet, FEATURE_START_FEATURE_NUMBER);
    }

    public static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String featureNumber) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(FEATURE_START_PROMPT_FEATURE_BRANCH_NAME)).thenReturn(featureNumber);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-start", promptControllerMock);
        verify(promptControllerMock).prompt(FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
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
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_SELECTING_RELEASE, Arrays.asList("0", "T"))
                ).thenReturn("0");
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION)).thenReturn(
                maintenanceVersion);
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION)).thenReturn(
                maintenanceSnapshotVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "maintenance-start", promptControllerMock);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_SELECTING_RELEASE,
                Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
    }
}
