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
 *
 * @author VMedvid
 */
public class GitFlowFailureException extends MojoFailureException {

    public GitFlowFailureException(String problem, String solutionProposal, String... stepsToContinue) {
        this(null, problem, solutionProposal, stepsToContinue);
    }

    public GitFlowFailureException(Throwable cause, String problem, String solutionProposal,
            String... stepsToContinue) {
        super(createGitflowMessage(problem, solutionProposal, stepsToContinue), cause);
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

}
