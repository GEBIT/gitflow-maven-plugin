//
// GitFlowUpgradeMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;
import java.io.IOException;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Convert a project to versionless operation
 *
 * @author Erwin Tratar
 * @since 2.2.0
 */
@Mojo(name = GitFlowMakeVersionlessMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowMakeVersionlessMojo extends AbstractGitFlowMojo {

    private static final String MVN_EXTENSIONS_XML = ".mvn/extensions.xml";

    static final String GOAL = "make-versionless";

    /**
     * Overwritten to disable default reset if not in versionless mode
     */
    @Override
    protected void initializeVersionlessMode() {
        // not calling super
    }

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting versionless conversion process");
        if (versionless) {
            throw new MojoFailureException("Project is already versionless");
        }

        // must not have uncommitted changes (otherwise they would be committed)
        checkUncommittedChanges();

        // remember the current version
        String currentVersion = getProject().getVersion();
        String extensionVersion = "1.1.0";
        Plugin extensionPlugin = getProject().getPlugin("de.gebit.build.maven:gebit-build-extension");
        if (extensionPlugin != null) {
            extensionVersion = extensionPlugin.getVersion();
        }

        getMavenLog().debug("activating core extension");
        File extensionsFile = new File(getBasedir(), MVN_EXTENSIONS_XML);
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
                "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
                "  xsi:schemaLocation=\"http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd\">\n" + 
                "  <extension>\n" + 
                "        <groupId>de.gebit.build.maven</groupId>\n" + 
                "        <artifactId>gebit-build-extension</artifactId>\n" + 
                "        <version>" + extensionVersion + "</version>\n" + 
                "  </extension>\n" + 
                "</extensions>\n";
        if (!extensionsFile.exists()) {
            try {
                FileUtils.fileWrite(extensionsFile, content);
            } catch (IOException exc) {
                throw new MojoFailureException("Failed to create core extension configuration.", exc);
            }
            getMavenLog().info(MVN_EXTENSIONS_XML + " created.");
        } else {
            getMavenLog().info("Please make sure this is in '" + MVN_EXTENSIONS_XML + "':\n\n" + content);
        }

        // convert to ${revision}
        getMavenLog().debug("converting POM files");
        versionless = false;
        VersionlessMode originalVersionlessMode = versionlessMode;
        versionlessMode = VersionlessMode.NONE;
        mvnSetVersions("${dollar}{revision}", GitFlowAction.MAKE_VERSIONLESS, "");

        getMavenLog().info("Creating commit");
        gitCommit("NO-ISSUE: converted to versionless mode");

        // now set the version in the new scheme
        getMavenLog().debug("re-applying version " + currentVersion);
        versionless = true;
        versionlessMode = originalVersionlessMode;
        mvnSetVersions(currentVersion, GitFlowAction.MAKE_VERSIONLESS, "");

        getMavenLog().info("Conversion finished");
    }

}
