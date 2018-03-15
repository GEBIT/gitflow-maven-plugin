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
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
     * The relative path to the dummy editor working directory. Relative to the
     * base directory.
     */
    private static final String RELATIVE_PATH_TO_EDITOR_WORKING_DIR = "__editor";

    /**
     * The name of the git-rebase-todo file.
     */
    private static final String FILE_NAME_GIT_REBASE_TODO = "git-rebase-todo";

    /**
     * The name of the COMMIT_EDITMSG file.
     */
    private static final String FILE_NAME_COMMIT_EDITMSG = "COMMIT_EDITMSG";

    /**
     * The name of the input file git-rebase-todo-commands.
     */
    private static final String INPUT_FILE_NAME_GIT_REBASE_TODO_COMMANDS = "INPUT_git-rebase-todo-commands.txt";

    /**
     * The name of the input file COMMIT_EDITMSG.
     */
    private static final String INPUT_FILE_NAME_COMMIT_EDITMSG = "INPUT_COMMIT_EDITMSG.txt";

    /**
     * The relative path to the git-rebase-todo file passed to the dummy editor.
     * Relative to the base directory.
     */
    public static final String RELATIVE_PATH_TO_GIT_REBASE_TODO = new File(RELATIVE_PATH_TO_EDITOR_WORKING_DIR,
            FILE_NAME_GIT_REBASE_TODO).getPath();

    /**
     * The relative path to the input file git-rebase-todo-commands used by
     * dummy editor. Relative to the base directory.
     */
    public static final String REBASE_TODO_COMMANDS_FILE_RELATIVE_PATH = new File(RELATIVE_PATH_TO_EDITOR_WORKING_DIR,
            INPUT_FILE_NAME_GIT_REBASE_TODO_COMMANDS).getPath();

    private static boolean allowToOverwriteFiles = true;

    public static void main(String[] args) {
        // test
        redirecteSystemOut();
        log("----------------------------------------------------------");
        log("Dummy Editor: executed with args: " + Arrays.toString(args));
        try {
            if (args != null && args.length == 1 && args[0] != null) {
                File editorFile = new File(args[0]);
                if (editorFile.exists() && editorFile.isFile()) {
                    String targetBasedir = System.getProperty(PROPERTY_KEY_TARGET_BASEDIR);
                    if (targetBasedir != null) {
                        File targetDir = new File(targetBasedir, RELATIVE_PATH_TO_EDITOR_WORKING_DIR);
                        if (!targetDir.exists()) {
                            targetDir.mkdirs();
                        }
                        File target = new File(targetDir, editorFile.getName());
                        if (allowToOverwriteFiles || !target.exists()) {
                            log("Dummy Editor: copying file from [" + editorFile.getAbsolutePath() + "] to ["
                                    + target.getAbsolutePath() + "]");
                            if (allowToOverwriteFiles) {
                                Files.copy(editorFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                Files.copy(editorFile.toPath(), target.toPath());
                            }
                            processFile(editorFile, targetDir);
                        } else {
                            logError("Dummy Editor: file [" + editorFile.getName() + "] already created");
                            System.exit(1);
                        }
                    } else {
                        logError("Dummy Editor: system property '" + PROPERTY_KEY_TARGET_BASEDIR + "' not found");
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

    private static void processFile(File txtFile, File targetDir) {
        try {
            if (FILE_NAME_GIT_REBASE_TODO.equals(txtFile.getName())) {
                processRebase(txtFile, targetDir);
            } else if (FILE_NAME_COMMIT_EDITMSG.equals(txtFile.getName())) {
                processCommitEditMsg(txtFile, targetDir);
            } else {
                log("Processing of file [" + txtFile.getName() + "] is not supported");
            }
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    private static void processRebase(File editorFile, File targetDir) throws IOException {
        log("Processing git-rebase-todo...");
        File rebaseTodoCommandsFile = new File(targetDir, INPUT_FILE_NAME_GIT_REBASE_TODO_COMMANDS);
        if (rebaseTodoCommandsFile.exists()) {
            GitRebaseTodo commands = GitRebaseTodo.load(rebaseTodoCommandsFile);
            GitRebaseTodo todo = GitRebaseTodo.load(editorFile);
            List<GitRebaseTodoEntry> todoEntries = todo.getEntries();
            List<GitRebaseTodoEntry> commandsEntries = commands.getEntries();
            int maxChanges = Math.min(todoEntries.size(), commandsEntries.size());
            if (maxChanges > 0) {
                for (int i = 0; i < maxChanges; i++) {
                    todoEntries.get(i).setCommand(commandsEntries.get(i).getCommand());
                }
                log("- saving changes...");
                todo.saveToFile(editorFile);
                log("- rebase todo file saved");
            } else {
                log("- nothing to change");
            }
        } else {
            log("- nothing to change: commands not provided");
        }
    }

    private static void processCommitEditMsg(File editorFile, File targetDir) throws IOException {
        log("Processing COMMIT_EDITMSG...");
        File inputFile = new File(targetDir, INPUT_FILE_NAME_COMMIT_EDITMSG);
        if (inputFile.exists()) {
            Files.copy(inputFile.toPath(), editorFile.toPath());
            log("- commit message replaced");
        } else {
            log("- default commit message used");
        }
    }

    private static void log(String msg) {
        System.out.println(prepareLogMessage(msg, "INFO"));
    }

    private static String prepareLogMessage(String msg, String level) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " [" + level + "] " + msg;
    }

    private static void logError(String msg) {
        System.err.println(prepareLogMessage(msg, "ERROR"));
    }

    private static void redirecteSystemOut() {
        String targetBasedir = System.getProperty(PROPERTY_KEY_TARGET_BASEDIR);
        if (targetBasedir != null) {
            File target = new File(new File(targetBasedir, RELATIVE_PATH_TO_EDITOR_WORKING_DIR), "editor.log");
            File targetParentDir = target.getParentFile();
            if (!targetParentDir.exists()) {
                targetParentDir.mkdirs();
            }
            try {
                System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(target, true)), true));
                System.setErr(System.out);
            } catch (FileNotFoundException exc) {
                exc.printStackTrace();
            }
        }
    }
}
