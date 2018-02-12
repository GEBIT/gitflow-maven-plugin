//
// GitFlowSetVersionMojoTest.java
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

import java.util.Properties;
import java.util.Set;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowSetVersionMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "set-version";

    private static final String POM_NEXT_RELEASE_VERSION = TestProjects.BASIC.nextReleaseVersion;

    private static final String NEW_VERSION = "1.42.0-SNAPSHOT";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            Properties properties = new Properties();
            properties.setProperty("newVersion", NEW_VERSION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, properties);
            // verify
            Set<String> modified = git.status(repositorySet).getModified();
            assertEquals("wrong number of modified and not commited files", 1, modified.size());
            assertEquals("pom.xml should be modified and not commited", "pom.xml", modified.iterator().next());
            assertVersionInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
        }
    }

    @Test
    public void testExecuteWithPrompter() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt("What is the new version?", POM_NEXT_RELEASE_VERSION))
                    .thenReturn(NEW_VERSION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt("What is the new version?", POM_NEXT_RELEASE_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            Set<String> modified = git.status(repositorySet).getModified();
            assertEquals("wrong number of modified and not commited files", 1, modified.size());
            assertEquals("pom.xml should be modified and not commited", "pom.xml", modified.iterator().next());
            assertVersionInPom(repositorySet.getWorkingDirectory(), NEW_VERSION);
        }
    }
}
