//
// StepsUtil.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 *
 * @author Volodymyr Medvid
 */
public class StepsUtil {

    /**
     * Get steps to be executed.<br>
     * If breakpoint parameter is <code>null</code> then returns all steps.<br>
     * If breakpoint parameter is not <code>null</code> then returns all steps
     * beginning from the breakpoint.
     *
     * @param breakpoint
     *            the breakpoint which to start from or <code>null</code> to get
     *            all steps
     * @param allProcessSteps all processable steps
     * @return the steps to be executed
     */
    public static <B extends Breakpoint, P extends StepParameters<B>> List<Step<B, P>> getStepsToExecute(B breakpoint,
            List<Step<B, P>> allProcessSteps) {
        List<Step<B, P>> steps = new ArrayList<>();
        for (int i = allProcessSteps.size() - 1; i >= 0; i--) {
            Step<B, P> step = allProcessSteps.get(i);
            steps.add(step);
            if (breakpoint != null && breakpoint == step.getBreakpoint()) {
                break;
            }
        }
        Collections.reverse(steps);
        return steps;
    }

    public static <B extends Breakpoint, P extends StepParameters<B>> void processSteps(List<Step<B, P>> allProcessSteps,
            BreakpointSupplier<B> breakpointSupplier, StepParametersInitializer<B, P> stepParametersInitializer)
            throws MojoFailureException, CommandLineException {
        B breakpoint = breakpointSupplier.getBreakpoint();
        List<Step<B, P>> steps = getStepsToExecute(breakpoint, allProcessSteps);
        P stepParameters = stepParametersInitializer.init(breakpoint);
        for (Step<B, P> step : steps) {
            stepParameters = step.execute(stepParameters);
        }
    }
}
