//
// WorkspaceUtils.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;

/**
 *
 * @author VMedvid
 */
public class WorkspaceUtils {

    public static File getWorkspaceTargetPath() {
        File eclipseTarget = new File("eclipse-target");
        File target = new File("target");
        File eclipseTargetClasses = new File(eclipseTarget, "classes");
        File targetClasses = new File(target, "classes");
        File eclipsePluginDescriptor = new File(eclipseTargetClasses, "META-INF/maven/plugin.xml");
        File pluginDescriptor = new File(targetClasses, "META-INF/maven/plugin.xml");
        if (eclipsePluginDescriptor.exists()) {
            if (pluginDescriptor.exists()) {
                if (eclipsePluginDescriptor.lastModified() > pluginDescriptor.lastModified()) {
                    return eclipseTarget;
                } else {
                    return target;
                }
            } else {
                return eclipseTarget;
            }
        }
        return target;
    }

    public static File getWorkspaceClasspath() {
        return new File(getWorkspaceTargetPath(), "classes");
    }

    public static File getWorkspaceTestClasspath() {
        return new File(getWorkspaceTargetPath(), "test-classes");
    }

    public static File getJavaExecutable() {
        return new File(new File(new File(System.getProperty("java.home")), "bin"), "java");
    }

}
