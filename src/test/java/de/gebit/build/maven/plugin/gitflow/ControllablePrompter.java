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

/**
 * Controllable prompter allows to simulate the prompter used by maven.
 *
 * @author VMedvid
 */
@Component(role = Prompter.class)
public class ControllablePrompter implements Prompter {

    private Prompter controller;

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
        checkController();
        String answer = controller.prompt(aMessage);
        logPrompt(aMessage, answer);
        return answer;
    }

    @Override
    public String prompt(String aMessage, String aDefaultReply) throws PrompterException {
        checkController();
        String answer = controller.prompt(aMessage, aDefaultReply);
        logPrompt(formatMessageForLog(aMessage, null, aDefaultReply), answer);
        return answer;
    }

    @Override
    public String prompt(String aMessage, List aPossibleValues) throws PrompterException {
        checkController();
        String answer = controller.prompt(aMessage, aPossibleValues);
        logPrompt(formatMessageForLog(aMessage, aPossibleValues, null), answer);
        return answer;
    }

    @Override
    public String prompt(String aMessage, List aPossibleValues, String aDefaultReply) throws PrompterException {
        checkController();
        String answer = controller.prompt(aMessage, aPossibleValues, aDefaultReply);
        logPrompt(formatMessageForLog(aMessage, aPossibleValues, aDefaultReply), answer);
        return answer;
    }

    @Override
    public String promptForPassword(String aMessage) throws PrompterException {
        checkController();
        String answer = controller.promptForPassword(aMessage);
        logPrompt(aMessage, "******");
        return answer;
    }

    @Override
    public void showMessage(String aMessage) throws PrompterException {
        checkController();
        controller.showMessage(aMessage);
        logMessage(aMessage);
    }

    private void checkController() throws PrompterException {
        if (controller == null) {
            throw new PrompterException("Prompter is used but no prompt controller is configured. "
                    + "Pass your promt controller or mock to the method for mojo execution.");
        }
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
            formatted.append(" (");
            for (Iterator it = possibleValues.iterator(); it.hasNext();) {
                String possibleValue = (String) it.next();
                formatted.append(possibleValue);
                if (it.hasNext()) {
                    formatted.append('/');
                }
            }
            formatted.append(')');
        }
        if (defaultReply != null) {
            formatted.append(' ').append(defaultReply).append(": ");
        }
        return formatted.toString();
    }

}
