//
// StepOperator.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;

@FunctionalInterface
public interface StepOperator<P extends StepParameters<?>> {

    P execute(P parameters) throws MojoFailureException, CommandLineException;
}