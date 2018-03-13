//
// DummyEditor.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import de.gebit.build.maven.plugin.gitflow.jgit.GitRebaseTodo.GitRebaseTodoEntry;

/**
 * The editor program that can be used instead of default git editor for eaxmple
 * for testing purposes. The program expects a path to the file to be edited as
 * parameter. The file will be copied to the target which bese directory
 * specified by system properties "DummyEditor.target.basedir" and relative path
 * is "__editor/file.txt".
 *
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

    /**
     * The relative path to the file passed to the dummy editor. Relative to the
     * base directory.
     */
    public static final String REBASE_TODO_COMMANDS_FILE_RELATIVE_PATH = "__editor/git-rebase-todo-commands.txt";

    public static void main(String[] args) {
        // test
        redirecteSystemOut();

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
                            processFile(txtFile, targetBasedir);
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

    private static void processFile(File txtFile, String targetBasedir) {
        try {
            if ("git-rebase-todo".equals(txtFile.getName())) {
                processRebase(txtFile, targetBasedir);
            }
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    private static void processRebase(File gitRebaseTodoFile, String targetBasedir) throws IOException {
        System.out.println("Process rebase todo...");
        final File rebaseTodoCommandsFile = new File(targetBasedir, REBASE_TODO_COMMANDS_FILE_RELATIVE_PATH);
        if (rebaseTodoCommandsFile.exists()) {
            GitRebaseTodo commands = GitRebaseTodo.load(rebaseTodoCommandsFile);
            GitRebaseTodo todo = GitRebaseTodo.load(gitRebaseTodoFile);
            List<GitRebaseTodoEntry> todoEntries = todo.getEntries();
            List<GitRebaseTodoEntry> commandsEntries = commands.getEntries();
            int maxChanges = Math.min(todoEntries.size(), commandsEntries.size());
            if (maxChanges > 0) {
                for (int i = 0; i < maxChanges; i++) {
                    todoEntries.get(i).setCommand(commandsEntries.get(i).getCommand());
                }
                System.out.println("...saving changes...");
                todo.saveToFile(gitRebaseTodoFile);
                System.out.println("...rebase todo file saved");
            } else {
                System.out.println("...nothing to change");
            }
        } else {
            System.out.println("...commands not provided");
        }
    }

    private static void redirecteSystemOut() {
        String targetBasedir = System.getProperty(PROPERTY_KEY_TARGET_BASEDIR);
        if (targetBasedir != null) {
            File target = new File(targetBasedir, "__editor/editor.log");
            File targetParentDir = target.getParentFile();
            if (!targetParentDir.exists()) {
                targetParentDir.mkdirs();
            }
            try {
                System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(target)), true));
                System.setErr(System.out);
            } catch (FileNotFoundException exc) {
                exc.printStackTrace();
            }
        }
    }
}
