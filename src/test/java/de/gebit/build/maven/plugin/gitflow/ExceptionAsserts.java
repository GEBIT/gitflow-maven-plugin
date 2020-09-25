//
// ExceptionAsserts.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MojoExecutionException;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * 
 * @author Volodja
 */
public class ExceptionAsserts {

    private static final String NOT_EXISTING_DIR = AbstractGitFlowMojoTestCase.NOT_EXISTING_DIR;

    private static final String COMMAND_LINE_EXCEPTION_MESSAGE_PATTERN = "Working directory \"{0}\" does not exist!";

    private static final String GITFLOW_FAULURE_EXCEPTION_HEADER = "\n\n############################ Gitflow problem ###########################\n";

    private static final String GITFLOW_FAULURE_EXCEPTION_FOOTER = "\n########################################################################\n";

    @FunctionalInterface
    public interface ThrowableRunnable {
        void run() throws Throwable;
    }

    /**
     * Asserts that the passed maven execution result consists of exception of
     * passed class and with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedExceptionClass
     *            the class of expected exception
     * @param expectedExceptionMessage
     *            the message of expected exception or <code>null</code> if
     *            exception message shouldn't be checked
     * @param regex
     *            <code>true</code> if <code>expectedExceptionMessage</code> is
     *            a regular exprssion that should be matched
     */
    public static void assertExceptionOnMavenExecution(MavenExecutionResult mavenExecutionResult,
            Class<? extends Throwable> expectedExceptionClass, String expectedExceptionMessage, boolean regex) {
        List<Throwable> exceptions = mavenExecutionResult.getExceptions();
        assertEquals("number of maven execution exceptions is different from expected", 1, exceptions.size());
        Throwable exception = exceptions.get(0);
        if (exception instanceof LifecycleExecutionException) {
            exception = exception.getCause();
        }
        assertException(exception, expectedExceptionClass, expectedExceptionMessage, regex);
    }

    public static void assertException(Throwable exception, Class<? extends Throwable> expectedExceptionClass,
            String expectedExceptionMessage, boolean regex) {
        if (!exception.getClass().equals(expectedExceptionClass)) {
            assertEquals("unexpected exception",
                    expectedExceptionClass.getName() + "(" + expectedExceptionMessage + ")",
                    exception.getClass().getName() + "(" + exception.getMessage() + ")");
        }
        assertExceptionMessage(exception, expectedExceptionMessage, regex);
    }

    public static void assertExceptionMessage(Throwable exception, String expectedExceptionMessage, boolean regex) {
        if (expectedExceptionMessage != null) {
            String exceptionMessage = trimGitFlowFailureExceptionMessage(exception.getMessage());
            if (regex) {
                assertTrue(
                        "exception message doesn't matches expected pattern.\nPattern: " + expectedExceptionMessage
                                + "\nMessage: " + exceptionMessage,
                        Pattern.compile(expectedExceptionMessage, Pattern.MULTILINE | Pattern.DOTALL)
                                .matcher(exceptionMessage).matches());
            } else {
                assertEquals("unexpected exception message", expectedExceptionMessage, exceptionMessage);
            }
        }
    }

    public static String trimGitFlowFailureExceptionMessage(String aMessage) {
        String message = aMessage;
        if (message != null) {
            if (message.startsWith(GITFLOW_FAULURE_EXCEPTION_HEADER)) {
                message = message.substring(GITFLOW_FAULURE_EXCEPTION_HEADER.length());
            }
            if (message.endsWith(GITFLOW_FAULURE_EXCEPTION_FOOTER)) {
                int pos = message.lastIndexOf("\n", message.length() - GITFLOW_FAULURE_EXCEPTION_FOOTER.length() - 1)
                        - 1;
                message = message.substring(0, pos);
            }
        }
        return message;
    }

    /**
     * Asserts that the passed maven execution result consists of failure
     * exception with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessage
     *            the expected message of failure exception or <code>null</code>
     *            if exception message shouldn't be checked
     */
    public static void assertMavenExecutionException(MavenExecutionResult mavenExecutionResult,
            String expectedMessage) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoExecutionException.class, expectedMessage, false);
    }

    public static void assertGitFlowFailureException(MavenExecutionResult mavenExecutionResult,
            GitFlowFailureInfo expectedFailureInfo) {
        assertGitFlowFailureException(mavenExecutionResult, expectedFailureInfo.getProblem(),
                expectedFailureInfo.getSolutionProposal(), expectedFailureInfo.getStepsToContinue());
    }

    public static void assertGitFlowFailureException(MavenExecutionResult mavenExecutionResult, String expectedProblem,
            String expectedSolutionProposal, String... expectedSteps) {
        String expectedMessage = createGitFlowMessage(expectedProblem, expectedSolutionProposal, expectedSteps);
        assertExceptionOnMavenExecution(mavenExecutionResult, GitFlowFailureException.class, expectedMessage, false);
    }

    public static String createGitFlowMessage(String problem, String solutionProposal, String... stepsToContinue) {
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

    public static String createGitFlowMessage(GitFlowFailureInfo expectedFailureInfoPattern) {
        return createGitFlowMessage(expectedFailureInfoPattern.getProblem(),
                expectedFailureInfoPattern.getSolutionProposal(), expectedFailureInfoPattern.getStepsToContinue());
    }

    public static void assertGitFlowFailureExceptionRegEx(MavenExecutionResult mavenExecutionResult,
            GitFlowFailureInfo expectedFailureInfoPattern) {
        String expectedMessage = createGitFlowMessage(expectedFailureInfoPattern);
        assertExceptionOnMavenExecution(mavenExecutionResult, GitFlowFailureException.class, expectedMessage, true);
    }

    public static void assertGitflowFailureOnCommandLineException(RepositorySet repositorySet,
            MavenExecutionResult mavenExecutionResult) {
        String expectedProblem = "External command execution failed with error:\n"
                + MessageFormat.format(COMMAND_LINE_EXCEPTION_MESSAGE_PATTERN,
                        new File(repositorySet.getWorkingDirectory(), NOT_EXISTING_DIR).getAbsolutePath());
        String expectedSolutionProposal = "Please report the error in the GBLD JIRA.";
        String expectedMessage = createGitFlowMessage(expectedProblem, expectedSolutionProposal);
        assertMavenExecutionException(mavenExecutionResult, expectedMessage);
    }

    public static void assertGitFlowFailureException(Throwable exception, String expectedProblem,
            String expectedSolutionProposal, String... expectedSteps) {
        String expectedMessage = createGitFlowMessage(expectedProblem, expectedSolutionProposal, expectedSteps);
        assertException(exception, GitFlowFailureException.class, expectedMessage, false);
    }

    public static void assertGitFlowFailureExceptionRegEx(Throwable exception,
            GitFlowFailureInfo expectedFailureInfoPattern) {
        String expectedMessage = createGitFlowMessage(expectedFailureInfoPattern);
        assertException(exception, GitFlowFailureException.class, expectedMessage, true);
    }

    public static Throwable assertThrows(ThrowableRunnable call) {
        try {
            call.run();
        } catch (Throwable exc) {
            return exc;
        }
        fail("Exception was expected");
        return null;
    }
}
