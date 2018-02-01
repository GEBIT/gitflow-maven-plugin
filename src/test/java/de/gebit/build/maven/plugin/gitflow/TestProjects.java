//
// Projects.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;

/**
 * Class with constants for test project directories.
 *
 * @author VMedvid
 */
public class TestProjects {

    /**
     * The base directory for all projects.
     */
    private static final File PROJECTS_BASEDIR = new File("src/test/resources/projects");

    /**
     * The directory of the basic test project.
     */
    public static final File BASIC = getProjectBasedir("basic-project-to-test");

    private static File getProjectBasedir(String projectName) {
        return new File(PROJECTS_BASEDIR, projectName);
    }
}
