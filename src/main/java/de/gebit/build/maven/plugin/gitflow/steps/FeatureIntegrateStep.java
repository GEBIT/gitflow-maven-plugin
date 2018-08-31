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
public class FeatureIntegrateStep extends Step<FeatureIntegrateBreakpoint, FeatureIntegrateStepParameters> {

    public FeatureIntegrateStep(StepOperator<FeatureIntegrateStepParameters> anExecutor,
            FeatureIntegrateBreakpoint aBreakpoint) {
        super(anExecutor, aBreakpoint);
    }

    public FeatureIntegrateStep(StepOperator<FeatureIntegrateStepParameters> anExecutor) {
        super(anExecutor);
    }

}
