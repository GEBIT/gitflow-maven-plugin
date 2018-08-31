//
// StepParametersInitializer.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 *
 * @author Volodymyr Medvid
 */
@FunctionalInterface
public interface StepParametersInitializer<B extends Breakpoint, P extends StepParameters<B>> {

    P init(B breakpoint) throws MojoFailureException, CommandLineException;

}
