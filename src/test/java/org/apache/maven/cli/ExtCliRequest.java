//
// ExtCliRequest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package org.apache.maven.cli;

import org.apache.commons.cli.ParseException;
import org.codehaus.plexus.classworlds.ClassWorld;

/**
 * Extends {@link CliRequest} to make it possible to instatiate the class.
 *
 * @author VMedvid
 */
public class ExtCliRequest extends CliRequest {

    /**
     * Creates an instance with passes arguments and parses the arguments to a
     * command line. The instance can be used together with
     * {@link org.apache.maven.cli.configuration.ConfigurationProcessor} to
     * create a maven request.
     *
     * @param aArgs
     *            the argumetns for the maven request
     * @throws ParseException
     *             if maven arguments couldn't be parsed
     */
    public ExtCliRequest(String[] aArgs) throws ParseException {
        super(aArgs, null);
        this.commandLine = new CLIManager().parse(aArgs);
    }

    /**
     * Creates an instance with passes arguments and class world. The instance
     * can be used in {@link MavenCli}.
     *
     * @param aArgs
     *            the argumetns for the maven request
     * @param aClassWorld
     *            the class world for the plexus container
     */
    public ExtCliRequest(String[] aArgs, ClassWorld aClassWorld) {
        super(aArgs, aClassWorld);
    }

    /**
     * Sets the working directory to the request.
     *
     * @param aWorkingDirectory
     *            the working directory to be set
     */
    public void setWorkingDirector(String aWorkingDirectory) {
        this.workingDirectory = aWorkingDirectory;
    }
}
