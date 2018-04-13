//
// GitFlowBuildVersionMojoTest.java
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
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowBuildVersionMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "build-version";

    private static final String PROMPT_BUILD_VERSION = "What is build version? ";

    private static final String BUILD_VERSION = "BUILD42";

    private static final String NEW_VERSION = TestProjects.BASIC.releaseVersion + "-" + BUILD_VERSION;

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_BUILD_VERSION)).thenReturn(BUILD_VERSION);

            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_BUILD_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            Set<String> modified = git.status(repositorySet).getModified();
            assertEquals("wrong number of modified and not commited files", 1, modified.size());
            assertEquals("pom.xml should be modified and not commited", "pom.xml", modified.iterator().next());
            assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
        }
    }
}
