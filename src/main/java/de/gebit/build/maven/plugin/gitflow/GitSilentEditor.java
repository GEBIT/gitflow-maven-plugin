//
// GitSilentEditor.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;

/**
 * Silent editor for git commit messages etc. It can be called by a git process
 * started by GEBIT flow. It "accepts" message suggestion and lets git continue
 * without user interaction.
 * <p>
 * This editor can be configured via environment variable
 * <code>GIT_EDITOR</code>:
 * 
 * <pre>
 * envVars.put("GIT_EDITOR", GitSilentEditor.getExecutable());
 * 
 * <pre>
 * </p>
 * 
 * @author Volodja
 */
public class GitSilentEditor {

    private static String executable;

    public static void main(String[] args) {
        System.exit(0);
    }

    /**
     * Returns command line needed to start the editor form another java
     * process. It includes path to the java executable used by current java
     * process, required classpath with only gitflow jar and main class of the
     * editor.
     * 
     * @return Command line needed to start the editor.
     */
    public static String getExecutable() {
        if (executable == null) {
            StringBuilder executableBuilder = new StringBuilder(System.getProperty("java.home"));
            executableBuilder.append(File.separator);
            executableBuilder.append("bin");
            executableBuilder.append(File.separator);
            executableBuilder.append("java");
            if (executableBuilder.indexOf(" ") != -1) {
                executableBuilder.insert(0, '"');
                executableBuilder.append('"');
            }
            executableBuilder.append(" -cp ");
            String classpath;
            try {
                classpath = new File(GitSilentEditor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getPath();
            } catch (Exception exc) {
                throw new IllegalStateException("Git Silent Editor executable can't be determined", exc);
            }
            if (classpath.contains(" ")) {
                executableBuilder.append('"');
                executableBuilder.append(classpath);
                executableBuilder.append('"');
            } else {
                executableBuilder.append(classpath);
            }
            executableBuilder.append(" ");
            executableBuilder.append(GitSilentEditor.class.getName());
            executable = executableBuilder.toString().replace("\\", "\\\\");
        }
        return executable;
    }
}
