//
// UpstreamVersionTest.java
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

import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class VersionlessTest extends AbstractGitFlowMojoTestCase {
    private static final String PROMPT_NEW_VERSION = "What is the new version?";
    private static final String POM_NEXT_RELEASE_VERSION = TestProjects.VERSIONLESS_PROJECT.nextReleaseVersion;
    private static final String SET_VERSION_GOAL = "set-version";
    private static final String NEW_VERSION = "1.42.0-SNAPSHOT";

    private RepositorySet repositorySet;

    @Override
    protected CoreExtension getCoreExtensionDescriptor() {
        CoreExtension desc = new CoreExtension();
        desc.setGroupId(TestProjects.VERSIONLESS_PROJECT.extensionGroupId);
        desc.setArtifactId(TestProjects.VERSIONLESS_PROJECT.extensionArtifactId);
        desc.setVersion(TestProjects.VERSIONLESS_PROJECT.extensionVersion);
        return desc;
    }

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.VERSIONLESS_PROJECT);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecuteInInteractiveMode() throws Exception {
        // set up
        when(promptControllerMock.prompt(PROMPT_NEW_VERSION, POM_NEXT_RELEASE_VERSION)).thenReturn(NEW_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), SET_VERSION_GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_NEW_VERSION, POM_NEXT_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertSetVersionMavenCommandExecution(NEW_VERSION, false);
        assertCommandsAfterVersionMavenCommandExecution(true);
        assertSetVersionTychoMavenCommandExecution(NEW_VERSION, false);
        git.assertModifiedFiles(repositorySet);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), "${revision}");
        assertEquals(NEW_VERSION, git.getBranchCentralConfigValue(repositorySet, "branch-config", git.currentBranch(repositorySet), "version"));
    }

    private void assertSetVersionMavenCommandExecution(String version, boolean executed) throws IOException {
        assertMavenCommandExecution("org.codehaus.mojo:versions-maven-plugin:2.5:set -DnewVersion=" + version
                + " -DgenerateBackupPoms=false", executed);
    }
    private void assertCommandsAfterVersionMavenCommandExecution(boolean executed) throws IOException {
        assertMavenCommandExecution("version-stamper:stamp -N", executed);
    }

    private void assertSetVersionTychoMavenCommandExecution(String version, boolean executed) throws IOException {
        assertMavenCommandExecution(
                "org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=" + version + " -Dtycho.mode=maven",
                executed);
    }
}
