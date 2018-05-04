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

    /**
     * The directory of the test project with invalid project version.
     */
    public static final TestProjectData INVALID_VERSION = new TestProjectData("invalid-version-project",
            "invalid-version", "GFTST");

    /**
     * The directory of the test project with snapshot dependencies.
     */
    public static final TestProjectData SNAPSHOT_DEPENDENCIES = new TestProjectData("with-snapshot-dependencies",
            "1.2.3-SNAPSHOT", "GFTST");
    /**
     * The directory of the test project with non-snapshot dependencies.
     */
    public static final TestProjectData NON_SNAPSHOT_DEPENDENCIES = new TestProjectData("with-dependencies",
            "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with modules.
     */
    public static final TestProjectData WITH_MODULES = new TestProjectData("with-modules", "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with modules and upstream project configured.
     */
    public static final TestProjectData WITH_UPSTREAM = new TestProjectData("with-upstream", "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with tycho version.
     */
    public static final TestProjectData TYCHO_PROJECT = new TestProjectData("tycho-project", "1.2.3.4-SNAPSHOT", "GFTST");

    public static final String PROFILE_SET_VERSION_WITHOUT_ADDITIONAL_VERSION_COMMANDS = "setVersionWithoutAdditionalVersionCommands";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_DEFAULT = "setVersionAdditionalVersionCommandWithoutDefault";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_NEW_LINE_CHARACTERS = "setVersionAdditionalVersionCommandWithNewLineCharacters";

    public static final String PROFILE_SET_VERSION_WITH_UPSTREAM = "setVersionWithUpstream";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_PROMPT = "setVersionAdditionalVersionCommandWithoutPrompt";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_ENABLED_BY_PROMPT = "setVersionAdditionalVersionCommandEnabledByPrompt";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE = "setVersionAdditionalVersionCommandWithInterpolationCycle";

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
        public final String buildName;

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject) {
            this(aProjectName, aVersion, aJiraProject, "gitflow-tests");
        }

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject, String aBuildName) {
            basedir = getProjectBasedir(aProjectName);
            artifactId = aProjectName;
            version = aVersion;
            releaseVersion = StringUtils.substringBeforeLast(version, "-SNAPSHOT");
            maintenanceVersion = StringUtils.substringBeforeLast(releaseVersion, ".");
            String tempNextReleaseVersion;
            try {
                tempNextReleaseVersion = new DefaultVersionInfo(version).getNextVersion().getReleaseVersionString();
            } catch (VersionParseException exc) {
                tempNextReleaseVersion = releaseVersion;
            }
            nextReleaseVersion = tempNextReleaseVersion;
            nextSnepshotVersion = nextReleaseVersion + "-SNAPSHOT";
            jiraProject = aJiraProject;
            buildName = aBuildName;
        }

    }

}
