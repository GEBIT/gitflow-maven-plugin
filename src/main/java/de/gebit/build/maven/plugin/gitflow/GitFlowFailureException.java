//
// GitflowFailureException.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoFailureException;

/**
 * Extension of {@link MojoFailureException} that provides structured message.
 *
 * @author Volodymyr Medvid
 */
public class GitFlowFailureException extends MojoFailureException {

    private String problem;
    private String solutionProposal;
    private String[] stepsToContinue;
    private boolean resetCause = false;

    public GitFlowFailureException(String aProblem, String aSolutionProposal, String... aStepsToContinue) {
        this(null, aProblem, aSolutionProposal, aStepsToContinue);
    }

    public GitFlowFailureException(Throwable cause, String aProblem, String aSolutionProposal,
            String... aStepsToContinue) {
        super(createGitflowMessage(aProblem, aSolutionProposal, aStepsToContinue), cause);
        this.problem = aProblem;
        this.solutionProposal = aSolutionProposal;
        this.stepsToContinue = aStepsToContinue;
    }

    public GitFlowFailureException(GitFlowFailureInfo aGitFlowFailureInfo) {
        this(null, aGitFlowFailureInfo);
    }

    public GitFlowFailureException(Throwable cause, GitFlowFailureInfo aGitFlowFailureInfo) {
        this(cause, aGitFlowFailureInfo.getProblem(), aGitFlowFailureInfo.getSolutionProposal(),
                aGitFlowFailureInfo.getStepsToContinue());
    }

    private static String createGitflowMessage(String problem, String solutionProposal, String[] stepsToContinue) {
        StringBuilder message = new StringBuilder();
        if (problem != null) {
            message.append(problem);
        }
        if (solutionProposal != null) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(solutionProposal);
        }
        if (stepsToContinue != null && stepsToContinue.length > 0) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append("How to continue:");
            message.append("\n");
            for (int i = 0; i < stepsToContinue.length; i++) {
                if (i > 0) {
                    message.append("\n");
                }
                message.append(stepsToContinue[i]);
            }
        }
        return message.toString();
    }

    public String getProblem() {
        return problem;
    }

    public String getSolutionProposal() {
        return solutionProposal;
    }

    public String[] getStepsToContinue() {
        return stepsToContinue;
    }

    public void resetCause() {
        resetCause = true;
    }

    @Override
    public synchronized Throwable getCause() {
        if (resetCause) {
            return null;
        }
        return super.getCause();
    }

}
