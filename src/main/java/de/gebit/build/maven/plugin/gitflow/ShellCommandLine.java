//
// ShellCommandLine.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.codehaus.plexus.util.cli.Commandline;

/**
 * Extension of Commandline to fix the problem that shell (e.g. cmd.exe) is not used in execute() method.
 *
 * @author VMedvid
 */
public class ShellCommandLine extends Commandline {

	@Override
	public String[] getCommandline() {
		return getShellCommandline();
	}
}
