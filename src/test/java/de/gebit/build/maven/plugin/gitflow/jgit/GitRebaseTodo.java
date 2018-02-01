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
 *
 * @author VMedvid
 */
public class GitRebaseTodo {

    private List<GitRebaseTodoEntry> entries = new ArrayList<GitRebaseTodoEntry>();
    private String sourceStartCommitId;
    private String sourceEndCommitId;
    private String targetCommit;

    /**
     * @param aEntries
     * @param aSourceStartCommitId
     * @param aSourceEndCommitId
     * @param aTargetCommit
     */
    private GitRebaseTodo(List<GitRebaseTodoEntry> aEntries, String aSourceStartCommitId, String aSourceEndCommitId,
            String aTargetCommit) {
        super();
        entries = aEntries;
        sourceStartCommitId = aSourceStartCommitId;
        sourceEndCommitId = aSourceEndCommitId;
        targetCommit = aTargetCommit;
    }


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
     * @return the entries
     */
    public List<GitRebaseTodoEntry> getEntries() {
        return entries;
    }

    /**
     * @return the sourceStartCommitId
     */
    public String getSourceStartCommitId() {
        return sourceStartCommitId;
    }

    /**
     * @return the sourceEndCommitId
     */
    public String getSourceEndCommitId() {
        return sourceEndCommitId;
    }

    /**
     * @return the targetCommit
     */
    public String getTargetCommit() {
        return targetCommit;
    }

    public static class GitRebaseTodoEntry {

        private String command;
        private String commitId;
        private String message;

        /**
         * @param aCommand
         * @param aCommitId
         * @param aMessage
         */
        private GitRebaseTodoEntry(String aCommand, String aCommitId, String aMessage) {
            command = aCommand;
            commitId = aCommitId;
            message = aMessage;
        }

        public String getCommand() {
            return command;
        }

        public String getCommitId() {
            return commitId;
        }

        public String getMessage() {
            return message;
        }

    }

}
