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

    private static final String FEATURE_NUMBER = ExecutorHelper.FEATURE_START_FEATURE_NUMBER;

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_BRANCH_VERSION = "1.2.3-" + FEATURE_NUMBER + "-SNAPSHOT";

    private static final String EXPECTED_ARTIFACT_NAME = "basic-test-pom-" + EXPECTED_BRANCH_VERSION + ".jar";

    @Test
    public void testExecuteWithPrompt() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME)).thenReturn(
                    FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", git.status(repositorySet).isClean());
            assertEquals("current branch is wrong", FEATURE_BRANCH, git.currentBranch(repositorySet));
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH,
                    ExecutorHelper.FEATURE_START_COMMIT_MESSAGE_SET_VERSION);
            // check if version in branch updated from 1.0.0 to 1.0.0-GBLD-42-SNAPSHOT
            Model workingPom = readPom(repositorySet.getWorkingDirectory());
            assertEquals("project version in local pom.xml file is wrong", EXPECTED_BRANCH_VERSION,
                    workingPom.getVersion());
            assertEquals("version.build property in local pom.xml file is wrong", EXPECTED_BRANCH_VERSION,
                    workingPom.getProperties().getProperty("version.build"));
            // check if project built (if artefact is in target directory)
            assertTrue("project is not built",
                    new File(repositorySet.getWorkingDirectory(), "target/" + EXPECTED_ARTIFACT_NAME).exists());
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);
        }
    }

}
