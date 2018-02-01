//
// DummyEditor.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * @author VMedvid
 */
public class GitDummyEditor {

    /**
     * The property key for base directory where dummy editor stores files.
     */
    public static final String PROPERTY_KEY_TARGET_BASEDIR = "DummyEditor.target.basedir";

    /**
     * The relative path to the file passed to the dummy editor. Relative to the
     * base directory.
     */
    public static final String EDIT_FILE_RELATIVE_PATH = "__editor/file.txt";

    public static void main(String[] args) {
        System.out.println("Dummy Editor: executed with args: " + Arrays.toString(args));
        try {
            if (args != null && args.length == 1 && args[0] != null) {
                File txtFile = new File(args[0]);
                if (txtFile.exists() && txtFile.isFile()) {
                    String targetBasedir = System.getProperty(PROPERTY_KEY_TARGET_BASEDIR);
                    if (targetBasedir != null) {
                        File target = new File(targetBasedir, EDIT_FILE_RELATIVE_PATH);
                        if (!target.exists()) {
                            File targetParentDir = target.getParentFile();
                            if (!targetParentDir.exists()) {
                                targetParentDir.mkdirs();
                            }
                            System.out.println("Dummy Editor: copying file from [" + txtFile.getAbsolutePath()
                                    + "] to [" + target.getAbsolutePath() + "]");
                            Files.copy(txtFile.toPath(), target.toPath());
                        } else {
                            System.err.println("Dummy Editor: file already created");
                            System.exit(1);
                        }
                    } else {
                        System.err.println(
                                "Dummy Editor: system property '" + PROPERTY_KEY_TARGET_BASEDIR + "' not found");
                        System.exit(2);
                    }
                }
            }
        } catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }
        System.exit(0);
    }
}
