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
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.components.interactivity.Prompter;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * The helper class to execute some goals of the plugin.
 *
 * @author Volodymyr Medvid
 */
public class ExecutorHelper {

    private static final String LS = System.getProperty("line.separator");

    public static final String EPIC_START_PROMPT_EPIC_BRANCH_NAME = "What is a name of epic branch? epic/";

    public static final String FEATURE_START_PROMPT_FEATURE_BRANCH_NAME = "What is a name of feature branch? feature/";

    public static final String HOTFIX_START_POM_NEXT_RELEASE_VERSION = TestProjects.BASIC.nextReleaseVersion;

    public static final String HOTFIX_START_PROMPT_HOTFIX_VERSION = "What is the hotfix version? ["
            + HOTFIX_START_POM_NEXT_RELEASE_VERSION + "]";

    public static final String RELEASE_START_POM_RELEASE_VERSION = TestProjects.BASIC.releaseVersion;

    public static final String RELEASE_START_PROMPT_RELEASE_VERSION = "What is the release version?";

    public static final String MAINTENANCE_START_PROMPT_SELECTING_RELEASE = "Release:" + LS + "0. <current commit>" + LS
            + "T. <prompt for explicit tag name>" + LS
            + "Choose release to create the maintenance branch from or enter a custom tag or release name";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION = "What is the maintenance version?";

    public static final String MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION = "What is the first version on the maintenance branch?";

    public static final String MAINTENANCE_START_MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    public static void executeEpicStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String epicName) throws Exception {
        executeEpicStart(testCase, repositorySet, epicName, null);
    }

    public static void executeEpicStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String epicName, Properties properties) throws Exception {
        Properties userProperties = properties != null ? properties : new Properties();
        userProperties.setProperty("epicName", epicName);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "epic-start", userProperties);
    }

    public static MavenExecutionResult executeEpicUpdateWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "epic-update", userProperties);
    }

    public static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String featureName) throws Exception {
        executeFeatureStart(testCase, repositorySet, featureName, null);
    }

    public static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String featureName, Properties properties) throws Exception {
        Properties userProperties = properties != null ? properties : new Properties();
        userProperties.setProperty("featureName", featureName);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-start", userProperties);
    }

    public static MavenExecutionResult executeFeatureRebaseWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet) throws Exception {
        return executeFeatureRebaseWithResult(testCase, repositorySet, new Properties());
    }

    public static MavenExecutionResult executeFeatureRebaseWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "feature-rebase", userProperties);
    }

    public static MavenExecutionResult executeFeatureFinishWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet) throws Exception {
        return executeFeatureFinishWithResult(testCase, repositorySet, new Properties());
    }

    public static MavenExecutionResult executeFeatureFinishWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "feature-finish", userProperties);
    }

    public static MavenExecutionResult executeFeatureFinishWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties, Prompter prompter) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "feature-finish", userProperties,
                prompter);
    }

    public static void executeFeatureFinish(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            Properties userProperties) throws Exception {
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-finish", userProperties);
    }

    public static void executeFeatureFinish(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            Properties userProperties, Prompter prompter) throws Exception {
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-finish", userProperties, prompter);
    }

    public static MavenExecutionResult executeFeatureIntegrateWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "feature-integrate", userProperties);
    }

    public static void executeFeatureAbort(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet)
            throws Exception {
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-abort");
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
        executeReleaseStart(testCase, repositorySet, releaseVersion, RELEASE_START_POM_RELEASE_VERSION, null);
    }

    public static void executeReleaseStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String releaseVersion, String calculatedReleaseVersion) throws Exception {
        executeReleaseStart(testCase, repositorySet, releaseVersion, calculatedReleaseVersion, null);
    }

    public static void executeReleaseStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String releaseVersion, String calculatedReleaseVersion, Properties properties) throws Exception {
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(RELEASE_START_PROMPT_RELEASE_VERSION, calculatedReleaseVersion))
                .thenReturn(releaseVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "release-start", properties, promptControllerMock);
        verify(promptControllerMock).prompt(RELEASE_START_PROMPT_RELEASE_VERSION, calculatedReleaseVersion);
        verifyNoMoreInteractions(promptControllerMock);
    }

    public static MavenExecutionResult executeReleaseFinishWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet) throws Exception {
        return executeReleaseFinishWithResult(testCase, repositorySet, null);
    }

    public static MavenExecutionResult executeReleaseFinishWithResult(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, Properties userProperties) throws Exception {
        return testCase.executeMojoWithResult(repositorySet.getWorkingDirectory(), "release-finish", userProperties);
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
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION,
                TestProjects.BASIC.maintenanceVersion)).thenReturn(maintenanceVersion);
        when(promptControllerMock.prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION,
                TestProjects.BASIC.nextSnapshotVersion)).thenReturn(maintenanceSnapshotVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "maintenance-start", promptControllerMock);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_SELECTING_RELEASE, Arrays.asList("0", "T"));
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION,
                TestProjects.BASIC.maintenanceVersion);
        verify(promptControllerMock).prompt(MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION,
                TestProjects.BASIC.nextSnapshotVersion);
        verifyNoMoreInteractions(promptControllerMock);
    }

    public static void executeSetVersion(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String newVersion) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("newVersion", newVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "set-version", properties);
    }
}
