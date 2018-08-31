//
// FeatureFinishStep.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

/**
 *
 * @author Volodymyr Medvid
 */
public class FeatureFinishStep extends Step<FeatureFinishBreakpoint, FeatureFinishStepParameters> {

    public FeatureFinishStep(StepOperator<FeatureFinishStepParameters> anExecutor,
            FeatureFinishBreakpoint aBreakpoint) {
        super(anExecutor, aBreakpoint);
    }

    public FeatureFinishStep(StepOperator<FeatureFinishStepParameters> anExecutor) {
        super(anExecutor);
    }

}
