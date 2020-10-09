//
// GitLabSSHClient.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;

/**
 * 
 * @author Volodja
 */
public class GitLabSSHClient {

    private static final int TIMEOUT_IN_MILLIS = 15000;

    private static final int GITLAB_SSH_PORT = 22;

    private static final String GITLAB_USER = "git";

    private JSch jsch;

    private Session session;

    public GitLabSSHClient() {
        this(new JSch());
    }

    public GitLabSSHClient(JSch aJSch) {
        jsch = aJSch;
        JSch.setConfig("PreferredAuthentications", "publickey");
        JSch.setConfig("StrictHostKeyChecking", "yes");
        JSch.setConfig("ConnectTimeout", String.valueOf(TIMEOUT_IN_MILLIS));
    }

    public ClosableSession connect(String host) throws JSchException, AgentProxyException {
        if (session != null) {
            if (session.isConnected()) {
                throw new JSchException("A connection to ssh host is already established.");
            } else {
                session = null;
            }
        } else {
            configureJSch();
        }
        session = jsch.getSession(GITLAB_USER, host, GITLAB_SSH_PORT);
        try {
            session.connect();
        } catch (Exception exc) {
            session = null;
            throw exc;
        }
        return new ClosableSession(this);
    }

    private void configureJSch() throws AgentProxyException, JSchException {
        Connector connector = ConnectorFactory.getDefault().createConnector();
        RemoteIdentityRepository identityRepository = new RemoteIdentityRepository(connector);
        jsch.setIdentityRepository(identityRepository);
        Path knownHostsPath = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");
        jsch.setKnownHosts(knownHostsPath.toAbsolutePath().toString());
    }

    public void disconnect() {
        if (session != null) {
            session.disconnect();
            session = null;
        }
    }

    /**
     * @throws JSchException
     * @return
     */
    public String createPersonalToken() throws JSchException {
        return parsePersonalToken(executeCommand("personal_access_token gebit_flow_mr api 1"));
    }

    public String executeCommand(String command) throws JSchException {
        checkIfConnected();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channel.setOutputStream(responseStream);
        channel.connect();
        while (channel.isConnected()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
        return new String(responseStream.toByteArray());
    }

    String parsePersonalToken(String response) throws JSchException {
        Matcher matcher = Pattern.compile("^Token:\\s*([\\w|-]+)").matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new JSchException("Token couldn't be parsed. Response:\n" + response);
    }

    private void checkIfConnected() throws JSchException {
        if (session == null || !session.isConnected()) {
            throw new JSchException("A connection to ssh host is not yet established.");
        }
    }

    public static class ClosableSession implements Closeable {

        private GitLabSSHClient client;

        private ClosableSession(GitLabSSHClient aClient) {
            client = aClient;
        }

        @Override
        public void close() throws IOException {
            client.disconnect();
        }

    }

}
