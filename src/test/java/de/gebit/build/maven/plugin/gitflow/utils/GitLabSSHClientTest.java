//
// GitLabSSHClientTest.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.utils;

import static de.gebit.build.maven.plugin.gitflow.ExceptionAsserts.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import de.gebit.build.maven.plugin.gitflow.utils.GitLabSSHClient.ClosableSession;

/**
 * 
 * @author Volodja
 */
public class GitLabSSHClientTest {

    private static final String DEFAULT_USER = "git";
    private static final int DEFAULT_PORT = 22;
    private static final String KNOWN_HOSTS_RELATIVE_PATH = ".ssh" + File.separator + "known_hosts";
    private static final String HOST = "dev-bln-gitlab.local.gebit.de";
    private static final String PERSONAL_TOKEN_RESPONSE = "Token:   aAY1G3YPeemECgUvxuXY\nScopes:  read_api\n"
            + "Expires: 2020-08-07";
    private static final String PERSONAL_TOKEN = "aAY1G3YPeemECgUvxuXY";
    private static final String PERSONAL_TOKEN_COMMAND = "personal_access_token gebit_flow_mr api 1";

    @Rule
    public MockitoRule mockitoRuel = MockitoJUnit.rule().silent();
    @Mock
    private JSch jschMock;
    @Mock
    private Session sessionMock;
    
    private GitLabSSHClient clientSpy;
    
    @Before
    public void setUp() throws Exception {
        clientSpy = spy(new GitLabSSHClient(jschMock));
        doReturn(mock(IdentityRepository.class)).when(clientSpy).getIdentityRepository();
    }

    @Test
    public void testConnect() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        // test
        clientSpy.connect(HOST);
        // verify
        verify(jschMock).setIdentityRepository(any());
        verify(jschMock).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verifyNoMoreInteractions(sessionMock);
    }

    @Test
    public void testConnectWithTryBlock() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        // test
        try (ClosableSession session = clientSpy.connect(HOST)) {
            // NOP
        }
        // verify
        verify(jschMock).setIdentityRepository(any());
        verify(jschMock).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verify(sessionMock).disconnect();
        verifyNoMoreInteractions(sessionMock);
    }

    @Test
    public void testConnectTwice() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        when(sessionMock.isConnected()).thenReturn(true);
        // test
        clientSpy.connect(HOST);
        assertThrows(JSchException.class, "A connection to ssh host is already established.",
                () -> clientSpy.connect(HOST));
        // verify
        verify(jschMock).setIdentityRepository(any());
        verify(jschMock).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verify(sessionMock).isConnected();
        verifyNoMoreInteractions(sessionMock);
    }

    @Test
    public void testConnectTwiceWithTryBlock() throws Exception {
        // set up
        Session sessionMock2 = mock(Session.class);
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock, sessionMock2);
        when(sessionMock.isConnected()).thenReturn(false);
        // test
        clientSpy.connect(HOST);
        clientSpy.connect(HOST);
        // verify
        verify(jschMock).setIdentityRepository(any());
        verify(jschMock).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock, times(2)).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verify(sessionMock).isConnected();
        verifyNoMoreInteractions(sessionMock);
        verify(sessionMock2).connect();
        verifyNoMoreInteractions(sessionMock2);
    }

    @Test
    public void testConnectTwiceAfterDisconnected() throws Exception {
        // set up
        Session sessionMock2 = mock(Session.class);
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock, sessionMock2);
        // test
        try (ClosableSession session = clientSpy.connect(HOST)) {
            // NOP
        }
        try (ClosableSession session = clientSpy.connect(HOST)) {
            // NOP
        }
        // verify
        verify(jschMock, times(2)).setIdentityRepository(any());
        verify(jschMock, times(2)).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock, times(2)).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verify(sessionMock).disconnect();
        verifyNoMoreInteractions(sessionMock);
        verify(sessionMock2).connect();
        verify(sessionMock2).disconnect();
        verifyNoMoreInteractions(sessionMock2);
    }

    @Test
    public void testDisconnectTwice() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        // test
        clientSpy.connect(HOST);
        clientSpy.disconnect();
        clientSpy.disconnect();
        // verify
        verify(jschMock).setIdentityRepository(any());
        verify(jschMock).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verify(sessionMock).disconnect();
        verifyNoMoreInteractions(sessionMock);
    }

    @Test
    public void testConnectWithException() throws Exception {
        // set up
        JSchException expectedException = new JSchException();
        Session sessionMock2 = mock(Session.class);
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock, sessionMock2);
        doThrow(expectedException).when(sessionMock).connect();
        // test
        assertThrows(expectedException, () -> clientSpy.connect(HOST));
        clientSpy.connect(HOST);
        // verify
        verify(jschMock, times(2)).setIdentityRepository(any());
        verify(jschMock, times(2)).setKnownHosts(endsWith(KNOWN_HOSTS_RELATIVE_PATH));
        verify(jschMock, times(2)).getSession(DEFAULT_USER, HOST, DEFAULT_PORT);
        verify(sessionMock).connect();
        verifyNoMoreInteractions(sessionMock);
        verify(sessionMock2).connect();
        verifyNoMoreInteractions(sessionMock2);
    }

    @Test
    public void testCreatePersonalToken() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        when(sessionMock.isConnected()).thenReturn(true);
        doReturn(PERSONAL_TOKEN_RESPONSE).when(clientSpy).executeCommand(PERSONAL_TOKEN_COMMAND);
        // test
        clientSpy.connect(HOST);
        String token = clientSpy.createPersonalToken();
        // verify
        verify(sessionMock).connect();
        verifyNoMoreInteractions(sessionMock);
        verify(clientSpy).executeCommand(PERSONAL_TOKEN_COMMAND);
        assertEquals(PERSONAL_TOKEN, token);
    }

    @Test
    public void testParsePersonalToken() throws Exception {
        // test
        String token = clientSpy.parsePersonalToken(PERSONAL_TOKEN_RESPONSE);
        // verify
        assertEquals(PERSONAL_TOKEN, token);
    }

    @Test
    public void testParsePersonalTokenFromInvalidRespons() throws Exception {
        // test
        assertThrows(JSchException.class, "Token couldn't be parsed. Response:\ndummy response",
                () -> clientSpy.parsePersonalToken("dummy response"));
    }

    @Test
    public void testExecuteCommand() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        when(sessionMock.isConnected()).thenReturn(true);
        ChannelExec channekMock = mock(ChannelExec.class);
        when(sessionMock.openChannel("exec")).thenReturn(channekMock);
        // test
        clientSpy.connect(HOST);
        clientSpy.executeCommand(PERSONAL_TOKEN_COMMAND);
        // verify
        verify(sessionMock).connect();
        verify(sessionMock).isConnected();
        verify(sessionMock).openChannel("exec");
        verifyNoMoreInteractions(sessionMock);
        verify(channekMock).setCommand(PERSONAL_TOKEN_COMMAND);
        verify(channekMock).setOutputStream(any());
        verify(channekMock).connect();
        verify(channekMock).isConnected();
    }

    @Test
    public void testExecuteCommandWhenNotConnected() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        // test
        assertThrows(JSchException.class, "A connection to ssh host is not yet established.",
                () -> clientSpy.executeCommand(PERSONAL_TOKEN_COMMAND));
        // verify
        verifyZeroInteractions(sessionMock);
    }

    @Test
    public void testExecuteCommandWhenDisconnected() throws Exception {
        // set up
        when(jschMock.getSession(DEFAULT_USER, HOST, DEFAULT_PORT)).thenReturn(sessionMock);
        when(sessionMock.isConnected()).thenReturn(false);
        // test
        clientSpy.connect(HOST);
        assertThrows(JSchException.class, "A connection to ssh host is not yet established.",
                () -> clientSpy.executeCommand(PERSONAL_TOKEN_COMMAND));
        // verify
        verify(sessionMock).connect();
        verify(sessionMock).isConnected();
        verifyNoMoreInteractions(sessionMock);
    }

}
