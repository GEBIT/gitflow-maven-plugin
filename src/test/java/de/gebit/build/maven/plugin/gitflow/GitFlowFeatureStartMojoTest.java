//
// GitFlowFeatureStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;

import org.apache.maven.model.Model;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-start";

    private static final String FEATURE_NUMBER = "GBLD-42";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_BRANCH_VERSION = "1.2.3-" + FEATURE_NUMBER + "-SNAPSHOT";

    private static final String EXPECTED_ARTIFACT_NAME = "basic-test-pom-" + EXPECTED_BRANCH_VERSION + ".jar";

    private static final String PROMPT_MESSAGE = "What is a name of feature branch? feature/";

    @Test
    public void testExecuteWithPrompt() throws Exception {
        try (RepositorySet repositorySet = prepareGitRepo(TestProjects.BASIC)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_MESSAGE)).thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingBasedir(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", gitStatus(repositorySet).isClean());
            assertEquals("current branch is wrong", FEATURE_BRANCH, gitCurrentBranch(repositorySet));
            assertLocalBranches(repositorySet, "master", FEATURE_BRANCH);
            assertRemoteBranches(repositorySet, "master");
            assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, "GBLD-42: updating versions for feature branch");
            // check if version in branch updated from 1.0.0 to 1.0.0-GBLD-42-SNAPSHOT
            Model workingPom = readPom(repositorySet.getWorkingBasedir());
            assertEquals("project version in local pom.xml file is wrong", EXPECTED_BRANCH_VERSION,
                    workingPom.getVersion());
            assertEquals("version.build property in local pom.xml file is wrong", EXPECTED_BRANCH_VERSION,
                    workingPom.getProperties().getProperty("version.build"));
            // check if project built (if artefact is in target directory)
            assertTrue("project is not built",
                    new File(repositorySet.getWorkingBasedir(), "target/" + EXPECTED_ARTIFACT_NAME).exists());
            verify(promptControllerMock).prompt(PROMPT_MESSAGE);
            verifyNoMoreInteractions(promptControllerMock);
        }
    }

}
