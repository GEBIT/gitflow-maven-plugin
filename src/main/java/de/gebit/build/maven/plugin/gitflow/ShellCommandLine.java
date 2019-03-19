//
// ShellCommandLine.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Arrays;

import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.shell.CmdShell;
import org.codehaus.plexus.util.cli.shell.Shell;

/**
 * Extension of Commandline to fix the problem that shell (e.g. cmd.exe) is not
 * used in execute() method and the problem that qoutes in arguments are not
 * escaped in cmd shell.
 *
 * @author VMedvid
 */
public class ShellCommandLine extends Commandline {

    /**
     * Create a new command line object.
     */
    public ShellCommandLine() {
        super();
        Shell shell = getShell();
        if (shell instanceof CmdShell) {
            setShell(new MyCmdShell());
        }
    }

    @Override
    public String[] getCommandline() {
        return getShellCommandline();
    }

    private class MyCmdShell extends CmdShell {

        private char[] quotingTriggerChars;

        public MyCmdShell() {
            super();
            setDoubleQuotedExecutableEscaped(true);
        }

        @Override
        protected char[] getQuotingTriggerChars() {
            if (quotingTriggerChars == null) {
                quotingTriggerChars = super.getQuotingTriggerChars();
                quotingTriggerChars  = Arrays.copyOf(quotingTriggerChars, quotingTriggerChars.length + 1);
                quotingTriggerChars[quotingTriggerChars.length - 1] = '^';
            }
            return quotingTriggerChars;
        }
    }
}
