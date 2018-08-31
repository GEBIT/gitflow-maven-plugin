//
// Step.java
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
 * Single process step for feature finish.
 *
 * @author Volodymyr Medvid
 */
public class Step<B extends Breakpoint, P extends StepParameters<B>> {

    private StepOperator<P> executor;
    private B breakpoint;

    /**
     * Creates a process step.
     *
     * @param anExecutor
     *            the method to be executed on this step
     */
    public Step(StepOperator<P> anExecutor) {
        this(anExecutor, null);
    }

    /**
     * Creates a process step.
     *
     * @param anExecutor
     *            the method to be executed on this step
     * @param aBreakpoint
     *            the breakpoint type associated with this step. The process
     *            will be restarted from this step if previous run faild on
     *            passed break point.
     */
    public Step(StepOperator<P> anExecutor, B aBreakpoint) {
        executor = anExecutor;
        breakpoint = aBreakpoint;
    }

    /**
     * Execute the process step.
     */
    public P execute(P parameters) throws MojoFailureException, CommandLineException {
        return executor.execute(parameters);
    }

    public B getBreakpoint() {
        return breakpoint;
    }
}