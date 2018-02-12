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

import org.apache.commons.lang3.StringUtils;

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
    public static final TestProjectData BASIC = new TestProjectData("basic-project", "1.2.3-SNAPSHOT", "GFTST");

    private static File getProjectBasedir(String projectName) {
        return new File(PROJECTS_BASEDIR, projectName);
    }

    static class TestProjectData {
        public final File basedir;
        public final String artifactId;
        public final String version;
        public final String releaseVersion;
        public final String maintenanceVersion;
        public final String nextReleaseVersion;
        public final String nextSnepshotVersion;
        public final String jiraProject;

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject) {
            basedir = getProjectBasedir(aProjectName);
            artifactId = aProjectName;
            version = aVersion;
            releaseVersion = StringUtils.substringBeforeLast(version, "-SNAPSHOT");
            maintenanceVersion = StringUtils.substringBeforeLast(releaseVersion, ".");
            try {
                nextReleaseVersion = new DefaultVersionInfo(version).getNextVersion().getReleaseVersionString();
            } catch (VersionParseException exc) {
                throw new IllegalStateException("version [" + version + "] can't be parsed", exc);
            }
            nextSnepshotVersion = nextReleaseVersion + "-SNAPSHOT";
            jiraProject = aJiraProject;
        }

    }


}
