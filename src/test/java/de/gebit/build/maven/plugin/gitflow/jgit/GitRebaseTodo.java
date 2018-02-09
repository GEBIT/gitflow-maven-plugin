//
// GitRebaseTodo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Container for information from a git-rebase-todo file created by git for an interactive rebase.
 *
 * @author VMedvid
 */
public class GitRebaseTodo {

    private List<GitRebaseTodoEntry> entries = new ArrayList<GitRebaseTodoEntry>();
    private String sourceStartCommitId;
    private String sourceEndCommitId;
    private String targetCommitId;

    /**
     * Creates the container and fills it with passed information.
     *
     * @param aEntries the list of commits listed in the git-rebase-todo file
     * @param aSourceStartCommitId the commitId of the rebase source start
     * @param aSourceEndCommitId the commitId of the rebase source end
     * @param aTargetCommitId the commitId of the rebase target
     */
    private GitRebaseTodo(List<GitRebaseTodoEntry> aEntries, String aSourceStartCommitId, String aSourceEndCommitId,
            String aTargetCommitId) {
        entries = aEntries;
        sourceStartCommitId = aSourceStartCommitId;
        sourceEndCommitId = aSourceEndCommitId;
        targetCommitId = aTargetCommitId;
    }


    /**
     * Loads and parses passed git-rebase-todo file.
     *
     * @param gitRebaseTodoFile the path to the git-rebase-todo file
     * @return the container with information from the git-rebase-todo file
     * @throws FileNotFoundException if git-rebase-todo file can't be found
     * @throws IOException if error occurs on reading the file
     */
    public static GitRebaseTodo load(File gitRebaseTodoFile) throws FileNotFoundException, IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(gitRebaseTodoFile))) {
            List<GitRebaseTodoEntry> entries = new ArrayList<GitRebaseTodoEntry>();
            String sourceStartCommitId = null;
            String sourceEndCommitId = null;
            String targetCommit = null;
            String line;
            while ((line = br.readLine()) != null) {
               if (line.startsWith("# Rebase ")) {
                   line = StringUtils.substringAfter(line, "# Rebase ");
                   sourceStartCommitId = StringUtils.substringBefore(line, "..");
                   line = StringUtils.substringAfter(line, "..");
                   sourceEndCommitId = StringUtils.substringBefore(line, " onto ");
                   line = StringUtils.substringAfter(line, " onto ");
                   targetCommit = StringUtils.substringBefore(line, " ");
                   break;
               } else if (!line.trim().isEmpty()) {
                   String command = null;
                   String commitId = null;
                   String message = null;
                   String[] parts = StringUtils.split(line, " ", 3);
                   command = parts[0];
                   if (parts.length > 1) {
                       commitId = parts[1];
                   }
                   if (parts.length > 2) {
                       message = parts[2];
                   }
                   entries.add(new GitRebaseTodoEntry(command, commitId, message));
               }
            }
            return new GitRebaseTodo(entries, sourceStartCommitId, sourceEndCommitId, targetCommit);
        }

    }

    /**
     * @return the list of commits listed in the git-rebase-todo file
     */
    public List<GitRebaseTodoEntry> getEntries() {
        return entries;
    }

    /**
     * @return the commitId of the rebase source start
     */
    public String getSourceStartCommitId() {
        return sourceStartCommitId;
    }

    /**
     * @return the commitId of the rebase source end
     */
    public String getSourceEndCommitId() {
        return sourceEndCommitId;
    }

    /**
     * @return the commitId of the rebase target
     */
    public String getTargetCommitId() {
        return targetCommitId;
    }

    /**
     * Container with information for a commit from a git-rebase-todo file.
     *
     * @author VMedvid
     */
    public static class GitRebaseTodoEntry {

        private String command;
        private String commitId;
        private String message;

        /**
         * Creates the container and fills it with passed information.
         *
         * @param aCommand the command for the commit (pick, reword, squash etc.)
         * @param aCommitId the short commitId
         * @param aMessage the commit message
         */
        private GitRebaseTodoEntry(String aCommand, String aCommitId, String aMessage) {
            command = aCommand;
            commitId = aCommitId;
            message = aMessage;
        }

        /**
         * @return the command for the commit (pick, reword, squash etc.)
         */
        public String getCommand() {
            return command;
        }

        /**
         * @return the short commitId
         */
        public String getCommitId() {
            return commitId;
        }

        /**
         * @return the commit message
         */
        public String getMessage() {
            return message;
        }

    }

}
