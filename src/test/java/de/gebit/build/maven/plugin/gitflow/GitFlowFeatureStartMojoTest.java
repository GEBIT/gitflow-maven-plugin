//
// GitFlowFeatureStartMojoTest.java
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

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-start";

    private static final String FEATURE_NUMBER = TestProjects.BASIC.jiraProject + "-42";

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NUMBER + ": updating versions for feature branch";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_BRANCH_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_NUMBER
            + "-SNAPSHOT";

    @Test
    public void testExecuteWithPrompt() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME))
                    .thenReturn(FEATURE_NUMBER);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.FEATURE_START_PROMPT_FEATURE_BRANCH_NAME);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            assertVersionInPom(repositorySet.getWorkingDirectory(), EXPECTED_BRANCH_VERSION);
        }
    }

}
