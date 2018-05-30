//
// ExtMavenCli.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.cli.CLIManager;
import org.apache.maven.cli.ExtCliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * Extends {@link MavenCli} to have more control over maven request. Adds
 * workspace repository to the maven request to use workspace version of
 * gitflow-maven-plugin.
 *
 * @author VMedvid
 */
public class ExtMavenCli extends MavenCli {

    /**
     * The name of the file with executed maven commands.
     */
    public static final String MVN_CMDS_LOG_FILENAME = "mvn-cmds.log";

    /**
     * The system property key for the version of gitflow-maven-plugin to be
     * used.
     */
    public static final String PROPERTY_KEY_PLUGIN_VERSION = "test.gitflow-maven-plugin.version";

    /**
     * The system property key for the workspace directory of
     * gitflow-maven-plugin to be used.
     */
    public static final String PROPERTY_KEY_PLUGIN_BASEDIR = "test.gitflow-maven-plugin.basedir";

    /**
     * The system property key for the directory where outputs should be stored.
     */
    public static final String PROPERTY_KEY_OUTPUT_DIR = "test.gitflow-maven-plugin.outputDir";

    public static void main(String[] args) {
        int result = main(args, null);
        System.exit(result);
    }

    public static int main(String[] args, ClassWorld classWorld) {
        ExtMavenCli cli = new ExtMavenCli();
        MessageUtils.systemInstall();
        ExtCliRequest cliRequest = new ExtCliRequest(args, classWorld);
        prepareRequest(cliRequest);
        int result = cli.doMain(cliRequest);
        MessageUtils.systemUninstall();
        return result;
    }

    private static void prepareRequest(ExtCliRequest aCliRequest) {
        MavenExecutionRequest request = aCliRequest.getRequest();
        final String outputDir = System.getProperty(PROPERTY_KEY_OUTPUT_DIR);
        if (outputDir == null) {
            throw new IllegalArgumentException("Missing system property '" + PROPERTY_KEY_OUTPUT_DIR + "'");
        }
        logMavenCall(outputDir, aCliRequest.getArgs());
        final String pluginBasedir = System.getProperty(PROPERTY_KEY_PLUGIN_BASEDIR);
        if (pluginBasedir == null) {
            throw new IllegalArgumentException("Missing system property '" + PROPERTY_KEY_PLUGIN_BASEDIR + "'");
        }
        final String pluginVersion = System.getProperty(PROPERTY_KEY_PLUGIN_VERSION);
        if (pluginVersion == null) {
            throw new IllegalArgumentException("Missing system property '" + PROPERTY_KEY_PLUGIN_VERSION + "'");
        }
        String workingDirectory = System.getProperty("user.dir");
        final File basedir;
        String alternatePomFile = null;
        if (aCliRequest.getCommandLine().hasOption(CLIManager.ALTERNATE_POM_FILE)) {
            alternatePomFile = aCliRequest.getCommandLine().getOptionValue(CLIManager.ALTERNATE_POM_FILE);
            if (alternatePomFile != null) {
                File pom = ExtCliRequest.resolveFile(new File(alternatePomFile), workingDirectory);
                if (pom.isDirectory()) {
                    pom = new File(pom, "pom.xml");
                }
                if (pom.getParentFile() != null) {
                    basedir = pom.getParentFile();
                } else {
                    basedir = new File(workingDirectory, "").getAbsoluteFile();
                }
            } else {
                basedir = new File(workingDirectory, "").getAbsoluteFile();
            }
        } else {
            basedir = new File(workingDirectory, "").getAbsoluteFile();
        }
        WorkspaceReader workspaceReader = new WorkspaceReader() {

            WorkspaceRepository workspaceRepo = new WorkspaceRepository("ide", getClass());

            @Override
            public File findArtifact(Artifact aArtifact) {
                if (aArtifact.getArtifactId().equals("gitflow-maven-plugin")) {
                    if (aArtifact.getExtension().equals("pom")) {
                        return new File(pluginBasedir, "pom.xml");
                    } else {
                        File eclipseTargetClasses = new File(pluginBasedir, "eclipse-target/classes");
                        File targetClasses = new File(pluginBasedir, "target/classes");
                        File eclipsePluginDescriptor = new File(eclipseTargetClasses, "META-INF/maven/plugin.xml");
                        File pluginDescriptor = new File(targetClasses, "META-INF/maven/plugin.xml");
                        if (eclipsePluginDescriptor.exists()) {
                            if (pluginDescriptor.exists()) {
                                if (eclipsePluginDescriptor.lastModified() > pluginDescriptor.lastModified()) {
                                    return eclipseTargetClasses;
                                } else {
                                    return targetClasses;
                                }
                            } else {
                                return eclipseTargetClasses;
                            }
                        }
                        return targetClasses;
                    }
                } else if (aArtifact.getArtifactId().equals("upstream-pom")) {
                    if (aArtifact.getExtension().equals("pom")) {
                        return new File(basedir, "upstream-pom-" + aArtifact.getVersion() + ".xml").getAbsoluteFile();
                    } else {
                        return WorkspaceUtils.getWorkspaceClasspath();
                    }
                }
                return null;
            }

            @Override
            public List<String> findVersions(Artifact aArtifact) {
                ArrayList<String> versions = new ArrayList<String>();
                if (aArtifact.getArtifactId().equals("gitflow-maven-plugin")) {
                    versions.add(pluginVersion);
                }
                return versions;
            }

            @Override
            public WorkspaceRepository getRepository() {
                return workspaceRepo;
            }
        };
        request.setWorkspaceReader(workspaceReader);
    }

    private static void logMavenCall(String outputDir, String[] args) {
        StringBuilder content = new StringBuilder();
        boolean first = true;
        boolean skip = false;
        for (String arg : args) {
            if (skip) {
                skip = false;
                continue;
            }
            if ("-P".equals(arg) || "-s".equals(arg) || "-f".equals(arg)) {
                skip = true;
                continue;
            }
            if (arg.startsWith("-Dversion.gitflow-maven-plugin=")) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                content.append(" ");
            }
            content.append(arg);
        }
        content.append("\n");
        try {
            FileUtils.write(new File(new File(outputDir), MVN_CMDS_LOG_FILENAME), content, "UTF-8", true);
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }
}
