//
// GitFlowBranchConfigMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Properties;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowBranchConfigMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "branch-config";

    private static final String PROMPT_PROPERTY_NAME = "Which property to modify?";

    private static final String PROMPT_PROPERTY_VALUE = "Set the value to (empty to delete)";

    private static final String PROPERTY_NAME = "test.prop.name";

    private static final String PROPERTY_VALUE = "42";

    private static final String CONFIG_BRANCH_NAME = "branch-config";

    private static final String CONFIG_BRANCH_DIR = ".branch-config";

    private static final String EXPECTED_COMMIT_MESSAGE = "NO-ISSUE: branch configuration update";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_PROPERTY_NAME)).thenReturn(PROPERTY_NAME);
            when(promptControllerMock.prompt(PROMPT_PROPERTY_VALUE)).thenReturn(PROPERTY_VALUE);
            git.switchToBranch(repositorySet, CONFIG_BRANCH_NAME, true);
            git.push(repositorySet);
            git.switchToBranch(repositorySet, MASTER_BRANCH);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_PROPERTY_NAME);
            verify(promptControllerMock).prompt(PROMPT_PROPERTY_VALUE);
            verifyNoMoreInteractions(promptControllerMock);

            assertFalse("config branch directory not removed",
                    new File(repositorySet.getWorkingDirectory(), CONFIG_BRANCH_DIR).exists());
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, CONFIG_BRANCH_NAME, CONFIG_BRANCH_NAME);
            git.assertCommitsInRemoteBranch(repositorySet, CONFIG_BRANCH_NAME, EXPECTED_COMMIT_MESSAGE);

            Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH_NAME,
                    MASTER_BRANCH);
            assertEquals(PROPERTY_VALUE, branchConfig.getProperty(PROPERTY_NAME));
        }
    }

}
