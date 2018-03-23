//
// GitFlowFailureInfo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 *
 * @author VMedvid
 */
public class GitFlowFailureInfo {

    private String problem;
    private String solutionProposal;
    private String[] stepsToContinue;

    /**
     * Creates an instance of {@link GitFlowFailureInfo}.
     *
     * @param aProblem
     *            the problem message
     * @param aSolutionProposal
     *            the solution proposal message
     * @param aStepsToContinue
     *            the messages for steps to continue
     */
    public GitFlowFailureInfo(String aProblem, String aSolutionProposal, String... aStepsToContinue) {
        problem = aProblem;
        solutionProposal = aSolutionProposal;
        stepsToContinue = aStepsToContinue;
    }

    /**
     * @return the problem message
     */
    public String getProblem() {
        return problem;
    }

    /**
     * @return the solution proposal message
     */
    public String getSolutionProposal() {
        return solutionProposal;
    }

    /**
     * @return the messages for steps to continue
     */
    public String[] getStepsToContinue() {
        return stepsToContinue;
    }

}
