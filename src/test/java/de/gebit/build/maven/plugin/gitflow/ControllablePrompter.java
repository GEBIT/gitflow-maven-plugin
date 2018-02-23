//
// ControllablePrompter.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Iterator;
import java.util.List;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

import com.google.common.base.Objects;

/**
 * Controllable prompter allows to simulate the prompter used by maven.
 *
 * @author VMedvid
 */
@Component(role = Prompter.class)
public class ControllablePrompter implements Prompter {

    private static final int MAX_REPEATS_WITH_SAME_MESSAGE = 10;

    private Prompter controller;

    private String lastMessage;

    private int repeatsWithSameMessage = 0;

    /**
     * Sets a controller that should response to the prompt.
     *
     * @param aController
     *            the controller to set
     */
    public void setController(Prompter aController) {
        controller = aController;
    }

    @Override
    public String prompt(String aMessage) throws PrompterException {
        checkControllerAndMessage(aMessage);
        String answer = controller.prompt(aMessage);
        logPrompt(aMessage, answer);
        return answer;
    }

    @Override
    public String prompt(String aMessage, String aDefaultReply) throws PrompterException {
        checkControllerAndMessage(aMessage);
        String answer = controller.prompt(aMessage, aDefaultReply);
        if (StringUtils.isEmpty(answer)) {
            answer = aDefaultReply;
        }
        logPrompt(formatMessageForLog(aMessage, null, aDefaultReply), answer);
        return answer;
    }

    @Override
    public String prompt(String aMessage, List aPossibleValues) throws PrompterException {
        checkControllerAndMessage(aMessage);
        String answer = controller.prompt(aMessage, aPossibleValues);
        logPrompt(formatMessageForLog(aMessage, aPossibleValues, null), answer);
        checkAnwer(answer, aPossibleValues);
        return answer;
    }

    private void checkAnwer(String answer, List possibleValues) {
        if (possibleValues == null || possibleValues.size() == 0) {
            throw new IllegalStateException("TEST ERROR: list of possible values is empty");
        }
        if (answer == null) {
            throw new IllegalStateException("TEST ERROR: answer can't be null");
        }
        if (!possibleValues.contains(answer)) {
            throw new IllegalStateException("TEST ERROR: prompt answer [" + answer + "] doesn't match possible values "
                    + formatPossibleValues(possibleValues));
        }
    }

    @Override
    public String prompt(String aMessage, List aPossibleValues, String aDefaultReply) throws PrompterException {
        checkControllerAndMessage(aMessage);
        String answer = controller.prompt(aMessage, aPossibleValues, aDefaultReply);
        if (StringUtils.isEmpty(answer)) {
            answer = aDefaultReply;
        }
        logPrompt(formatMessageForLog(aMessage, aPossibleValues, aDefaultReply), answer);
        checkAnwer(answer, aPossibleValues);
        return answer;
    }

    @Override
    public String promptForPassword(String aMessage) throws PrompterException {
        checkControllerAndMessage(aMessage);
        String answer = controller.promptForPassword(aMessage);
        logPrompt(aMessage, "******");
        return answer;
    }

    @Override
    public void showMessage(String aMessage) throws PrompterException {
        checkControllerAndMessage(aMessage);
        controller.showMessage(aMessage);
        logMessage(aMessage);
    }

    private void checkControllerAndMessage(String aMessage) throws PrompterException {
        if (controller == null) {
            throw new IllegalStateException("TEST ERROR: Prompter is used but no prompt controller is configured. "
                    + "Pass your promt controller or mock to the method for mojo execution.");
        }
        if (Objects.equal(lastMessage, aMessage)) {
            repeatsWithSameMessage++;
            if (repeatsWithSameMessage > MAX_REPEATS_WITH_SAME_MESSAGE) {
                int tries = repeatsWithSameMessage;
                repeatsWithSameMessage = 0;
                lastMessage = null;
                throw new IllegalStateException(
                        "TEST ERROR: Too many prompt tries (" + tries + ") with same mesage [" + aMessage + "]");
            }
        } else {
            repeatsWithSameMessage = 0;
        }
        lastMessage = aMessage;
    }

    private void logMessage(String aMessage) {
        System.out.println("PROMPT MESSAGE: " + aMessage);
    }

    private void logPrompt(String aMessage, String aAnswer) {
        System.out.println("PROMPT: " + aMessage + ":");
        System.out.println("ANSWER: " + aAnswer);
    }

    private String formatMessageForLog(String message, List possibleValues, String defaultReply) {
        StringBuffer formatted = new StringBuffer(message.length() * 2);
        formatted.append(message);
        if (possibleValues != null && !possibleValues.isEmpty()) {
            formatted.append(" ").append(formatPossibleValues(possibleValues));
        }
        if (defaultReply != null) {
            formatted.append(' ').append(defaultReply).append(": ");
        }
        return formatted.toString();
    }

    private String formatPossibleValues(List possibleValues) {
        StringBuffer formatted = new StringBuffer();
        formatted.append("(");
        for (Iterator it = possibleValues.iterator(); it.hasNext();) {
            String possibleValue = (String) it.next();
            formatted.append(possibleValue);
            if (it.hasNext()) {
                formatted.append('/');
            }
        }
        formatted.append(')');
        return formatted.toString();
    }

}
