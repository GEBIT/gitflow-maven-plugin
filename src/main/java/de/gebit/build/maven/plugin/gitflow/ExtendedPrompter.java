//
// ExtendedPrompter.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

import de.gebit.build.maven.plugin.gitflow.AbstractGitFlowMojo.OutputMode;

/**
 * Wrapper class for a promptem that provides additional usefull methods for
 * prompts.
 *
 * @author VMedvid
 */
public class ExtendedPrompter implements Prompter {

    private static final String LS = System.getProperty("line.separator");

    private Prompter prompter;
    private boolean interactiveMode;
    private AbstractGitFlowMojo mojo;

    /**
     * Creates an instance of {@link ExtendedPrompter}.
     *
     * @param aPrompter
     *            the prompter to be used
     * @param isInteractiveMode
     *            <code>true</code> if interactive mode is active
     */
    public ExtendedPrompter(Prompter aPrompter, boolean isInteractiveMode, AbstractGitFlowMojo aMojo) {
        prompter = aPrompter;
        interactiveMode = isInteractiveMode;
        mojo = aMojo;
    }

    @Override
    public String prompt(String message) throws PrompterException {
        logPrompt(message);
        String answer = prompter.prompt(message);
        logAnswer(answer);
        return answer;
    }

    @Override
    public String prompt(String message, String defaultReply) throws PrompterException {
        logPrompt(formatPromptMessageForLog(message, null, defaultReply));
        String answer = prompter.prompt(message, defaultReply);
        logAnswer(answer);
        return answer;
    }

    @Override
    public String prompt(String message, List possibleValues) throws PrompterException {
        logPrompt(formatPromptMessageForLog(message, possibleValues, null));
        String answer = prompter.prompt(message, possibleValues);
        logAnswer(answer);
        return answer;
    }

    @Override
    public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
        logPrompt(formatPromptMessageForLog(message, possibleValues, defaultReply));
        String answer = prompter.prompt(message, possibleValues, defaultReply);
        logAnswer(answer);
        return answer;
    }

    @Override
    public String promptForPassword(String message) throws PrompterException {
        logPrompt(message);
        String answer = prompter.promptForPassword(message);
        logAnswer("******");
        return answer;
    }

    @Override
    public void showMessage(String message) throws PrompterException {
        logMessage(message);
        prompter.showMessage(message);
    }

    private void logPrompt(String message) {
        mojo.getLog().logCommandOut("PROMPT", message + ":", OutputMode.NONE);
    }

    private void logAnswer(String answer) {
        mojo.getLog().logCommandOut("ANSWER", answer, OutputMode.NONE);
    }

    private void logMessage(String message) {
        mojo.getLog().logCommandOut("PROMPT", message, OutputMode.NONE);
    }

    private String formatPromptMessageForLog(String message, List possibleValues, String defaultReply) {
        StringBuffer formatted = new StringBuffer(message.length() * 2);
        formatted.append(message);
        if (possibleValues != null && !possibleValues.isEmpty()) {
            formatted.append(" ").append(formatPossiblePromptValues(possibleValues));
        }
        if (defaultReply != null) {
            formatted.append(' ').append(defaultReply).append(": ");
        }
        return formatted.toString();
    }

    private String formatPossiblePromptValues(List possibleValues) {
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

    public String promptValue(String promptMessage) throws GitFlowFailureException {
        return promptValue(promptMessage, null, null);
    }

    public String promptValue(String promptMessage, String defaultValue) throws GitFlowFailureException {
        return promptValue(promptMessage, defaultValue, null);
    }

    public String promptValue(String promptMessage, GitFlowFailureInfo missingValueInBatchModeMessage)
            throws GitFlowFailureException {
        return promptValue(promptMessage, null, missingValueInBatchModeMessage);
    }

    public String promptValue(String promptMessage, String defaultValue,
            GitFlowFailureInfo missingValueInBatchModeMessage) throws GitFlowFailureException {
        if (interactiveMode) {
            try {
                String answer = null;
                do {
                    if (StringUtils.isBlank(defaultValue)) {
                        answer = prompt(promptMessage);
                    } else {
                        answer = prompt(promptMessage, defaultValue);
                    }
                    if (StringUtils.isBlank(answer)) {
                        showMessage("Invalid value. A not blank value is required.");
                    }
                } while (StringUtils.isBlank(answer));
                return answer;
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e, createPromptErrorMessage("Failed to get value from user prompt"));
            }
        } else {
            String answer = defaultValue;
            if (StringUtils.isBlank(answer)) {
                if (missingValueInBatchModeMessage != null) {
                    throw new GitFlowFailureException(missingValueInBatchModeMessage);
                } else {
                    throw new GitFlowFailureException(getInteractiveModeRequiredMessage());
                }
            }
            return answer;
        }
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @return a non-empty value
     * @throws GitFlowFailureException
     *             in case of batch mode or error while prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName)
            throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, null, null, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no non-empty init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @return a non-empty value
     * @throws GitFlowFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue)
            throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no non-empty init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param defaultValue
     *            the default value to be used in prompt (can be
     *            <code>null</code>)
     * @return a non-empty value
     * @throws GitFlowFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue) throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no non-empty init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param defaultValue
     *            the default value to be used in prompt (can be
     *            <code>null</code>)
     * @param possibleValues
     *            the possible values (can be <code>null</code>)
     * @return a non-empty value
     * @throws GitFlowFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues) throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, possibleValues,
                null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of batch mode or error while prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, StringValidator validator)
            throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, null, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no valid init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            StringValidator validator) throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-emptyvalue is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no non-empty init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param missingValueInBatchModeMessage
     *            the message to be used in exception if an empty init value
     *            provided in batch mode
     * @return a non-empty value
     * @throws GitFlowFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            GitFlowFailureInfo missingValueInBatchModeMessage) throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, null, null,
                missingValueInBatchModeMessage);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no valid init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @param missingValueInBatchModeMessage
     *            the message to be used in exception if an empty init value
     *            provided in batch mode
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            StringValidator validator, GitFlowFailureInfo missingValueInBatchModeMessage)
            throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, null, validator,
                missingValueInBatchModeMessage);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no valid init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param defaultValue
     *            the default value to be used in prompt (can be
     *            <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, StringValidator validator) throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no valid init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param defaultValue
     *            the default value to be used in prompt (can be
     *            <code>null</code>)
     * @param possibleValues
     *            the possible values (can be <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues, StringValidator validator)
            throws GitFlowFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, possibleValues,
                validator, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link GitFlowFailureException} will be thrown
     * if no valid init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param defaultValue
     *            the default value to be used in prompt (can be
     *            <code>null</code>)
     * @param possibleValues
     *            the possible values (can be <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @param missingValueInBatchModeMessage
     *            the message to be used in exception if an empty init value
     *            provided in batch mode
     * @return a non-empty valid value
     * @throws GitFlowFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues, StringValidator validator,
            GitFlowFailureInfo missingValueInBatchModeMessage) throws GitFlowFailureException {
        String value = initValue;
        if (interactiveMode) {
            try {
                do {
                    if (StringUtils.isBlank(value)) {
                        if (StringUtils.isBlank(defaultValue)) {
                            if (CollectionUtils.isEmpty(possibleValues)) {
                                value = prompt(promptMessage);
                            } else {
                                value = prompt(promptMessage, possibleValues);
                            }
                        } else {
                            if (CollectionUtils.isEmpty(possibleValues)) {
                                value = prompt(promptMessage, defaultValue);
                            } else {
                                value = prompt(promptMessage, possibleValues, defaultValue);
                            }
                        }
                    }
                    if (validator != null) {
                        ValidationResult validationResult = validator.validate(value);
                        if (!validationResult.isValid()) {
                            String invalidMessage = validationResult.getInvalidMessage();
                            if (!StringUtils.isBlank(invalidMessage)) {
                                showMessage(invalidMessage);
                            }
                            value = null;
                        }
                    }
                } while (StringUtils.isBlank(value));
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get '" + parameterName + "' from user prompt"));
            }
        } else {
            if (StringUtils.isBlank(value)) {
                value = defaultValue;
            }
            if (StringUtils.isBlank(value)) {
                if (missingValueInBatchModeMessage != null) {
                    throw new GitFlowFailureException(missingValueInBatchModeMessage);
                } else {
                    throw new GitFlowFailureException(
                            "Parameter '" + parameterName + "' is required in non-interactive mode.",
                            "Specify a value for '" + parameterName + "' or run in interactive mode.");
                }
            }
            if (!CollectionUtils.isEmpty(possibleValues)) {
                if (!possibleValues.contains(value)) {
                    throw new GitFlowFailureException("Parameter '" + parameterName + "' is not valid.",
                            "Specify correct value for parameter '" + parameterName + "' and run again.");
                }
            }

            if (validator != null) {
                ValidationResult validationResult = validator.validate(value);
                if (!validationResult.isValid()) {
                    String errorMessage;
                    String invalidMessage = validationResult.getInvalidMessage();
                    if (!StringUtils.isBlank(invalidMessage)) {
                        errorMessage = invalidMessage;
                    } else {
                        errorMessage = "Parameter '" + parameterName + "' is not valid.";
                    }
                    throw new GitFlowFailureException(errorMessage,
                            "Specify correct value for parameter '" + parameterName + "' and run again.");
                }
            }
        }
        return value;
    }

    private GitFlowFailureInfo createPromptErrorMessage(final String promptError) {
        return new GitFlowFailureInfo(promptError,
                "Either run in non-interactive mode using '-B' parameter or run in an environment where user "
                        + "interaction is possible.");
    }

    /**
     * Prompts for an optional value if interactive mode is enabled. Entered
     * empty value will be accepted. If a non-empty init value provided it will
     * be returned without prompting.<br>
     * In case of batch mode non-empty init value or <code>null</code> will be
     * returned.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @return entered or initial value
     * @throws GitFlowFailureException
     *             in case of error while prompting
     */
    public String promptOptionalParameterValue(String promptMessage, String parameterName, String initValue)
            throws GitFlowFailureException {
        String value = initValue;
        if (interactiveMode) {
            try {
                if (StringUtils.isBlank(value)) {
                    value = prompt(promptMessage);
                }
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get '" + parameterName + "' from user prompt"));
            }
        } else {
            if (StringUtils.isBlank(value)) {
                value = null;
            }
        }
        return value;
    }

    /**
     * Prompts for a confirmation with yes/no options. The batchModeValue will
     * be returned in batch mode.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param defaultValue
     *            the deafult answer for confirmation (no default answer if
     *            <code>null</code>)
     * @param batchModeValue
     *            the answer to be returned if batch mode active
     * @return <code>true</code> in case of positive answer, otherwise
     *         <code>false</code>
     * @throws GitFlowFailureException
     *             in case oferror while prompting
     */
    public boolean promptConfirmation(String promptMessage, Boolean defaultValue, boolean batchModeValue)
            throws GitFlowFailureException {
        return promptConfirmation(promptMessage, defaultValue, batchModeValue, null);
    }

    /**
     * Prompts for a confirmation with yes/no options. A
     * {@link GitFlowFailureException} with batchModeErrorMessage will be thrown
     * in batch mode.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param defaultValue
     *            the deafult answer for confirmation (no default answer if
     *            <code>null</code>)
     * @param batchModeErrorMessage
     *            the error message to be used in exception in batch mode
     * @return <code>true</code> in case of positive answer, otherwise
     *         <code>false</code>
     * @throws GitFlowFailureException
     *             in case of batch mode or error while prompting
     */
    public boolean promptConfirmation(String promptMessage, Boolean defaultValue,
            GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        return promptConfirmation(promptMessage, defaultValue, null, batchModeErrorMessage);
    }

    /**
     * Prompts for a confirmation with yes/no options. If a non-null
     * batchModeValue provided than this value will be returned in batch mode.
     * Otherwise a {@link GitFlowFailureException} with batchModeErrorMessage
     * will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param defaultValue
     *            the deafult answer for confirmation (no default answer if
     *            <code>null</code>)
     * @param batchModeValue
     *            the answer to be returned if batch mode active (exception will
     *            be thrown if <code>null</code> in batch mode)
     * @param batchModeErrorMessage
     *            the error message to be used in exception in batch mode
     * @return <code>true</code> in case of positive answer, otherwise
     *         <code>false</code>
     * @throws GitFlowFailureException
     *             in case of batchModeValue=<code>null</code> in batch mode or
     *             error while prompting
     */
    public boolean promptConfirmation(String promptMessage, Boolean defaultValue, Boolean batchModeValue,
            GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        if (interactiveMode) {
            try {
                String answer;
                if (defaultValue != null) {
                    answer = prompt(promptMessage, Arrays.asList("y", "n"), defaultValue ? "y" : "n");
                } else {
                    answer = prompt(promptMessage, Arrays.asList("y", "n"));
                }
                return "y".equalsIgnoreCase(answer);
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get confirmation from user prompt."));
            }
        } else {
            if (batchModeValue == null) {
                throw new GitFlowFailureException(
                        batchModeErrorMessage != null ? batchModeErrorMessage : getInteractiveModeRequiredMessage());
            }
            return batchModeValue;
        }
    }

    private GitFlowFailureInfo getInteractiveModeRequiredMessage() {
        return new GitFlowFailureInfo("Interactive mode is required to execute the goal.",
                "Please run again in interactive mode or report the problem in the GBLD JIRA if the goal should be "
                        + "executable also in non-interactive mode.");
    }

    /**
     * Propmpts to select a value from the passed list of possible values by
     * entering a value number. If batch mode is active a
     * {@link GitFlowFailureException} will be thrown.
     *
     * @param promptMessagePrefix
     *            the message prefix that will be shown in prompt before the
     *            list of possible values (can be <code>null</code>)
     * @param promptMessageSuffix
     *            the message suffix that will be shown in prompt after the list
     *            of possible values (can be <code>null</code>)
     * @param possibleValues
     *            the list of possible values (should be not not empty)
     * @return the selected value
     * @throws GitFlowFailureException
     *             in case of active batch mode, empty list of possible values
     *             or error while prompting
     */
    public String promptToSelectFromOrderedList(String promptMessagePrefix, String promptMessageSuffix,
            List<String> possibleValues) throws GitFlowFailureException {
        return promptToSelectFromOrderedList(promptMessagePrefix, promptMessageSuffix, possibleValues, null);
    }

    /**
     * Propmpts to select a value from the passed list of possible values by
     * entering a value number. If batch mode is active a
     * {@link GitFlowFailureException} with batchModeErrorMessage will be
     * thrown.
     *
     * @param promptMessagePrefix
     *            the message prefix that will be shown in prompt before the
     *            list of possible values (can be <code>null</code>)
     * @param promptMessageSuffix
     *            the message suffix that will be shown in prompt after the list
     *            of possible values (can be <code>null</code>)
     * @param possibleValues
     *            the list of possible values (should be not not empty)
     * @param batchModeErrorMessage
     *            the error message to be used in exception in batch mode
     * @return the selected value
     * @throws GitFlowFailureException
     *             in case of active batch mode, empty list of possible values
     *             or error while prompting
     */
    public String promptToSelectFromOrderedList(String promptMessagePrefix, String promptMessageSuffix,
            List<String> possibleValues, GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        return promptToSelectFromOrderedList(promptMessagePrefix, promptMessageSuffix,
                possibleValues == null ? null : possibleValues.toArray(new String[possibleValues.size()]),
                batchModeErrorMessage);
    }

    /**
     * Propmpts to select a value from the passed list of possible values by
     * entering a value number. If batch mode is active a
     * {@link GitFlowFailureException} with batchModeErrorMessage will be
     * thrown.
     *
     * @param promptMessagePrefix
     *            the message prefix that will be shown in prompt before the
     *            list of possible values (can be <code>null</code>)
     * @param promptMessageSuffix
     *            the message suffix that will be shown in prompt after the list
     *            of possible values (can be <code>null</code>)
     * @param possibleValues
     *            the list of possible values (should be not not empty)
     * @param batchModeErrorMessage
     *            the error message to be used in exception in batch mode
     * @return the selected value
     * @throws GitFlowFailureException
     *             in case of active batch mode, empty list of possible values
     *             or error while prompting
     */
    public String promptToSelectFromOrderedList(String promptMessagePrefix, String promptMessageSuffix,
            String[] possibleValues, GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        if (possibleValues == null || possibleValues.length == 0) {
            throw new GitFlowFailureException("Empty list of possible values provided for user selection",
                    "Please report the error in the GBLD JIRA.");
        }
        List<SelectOption> options = new ArrayList<>(possibleValues.length);
        for (int i = 0; i < possibleValues.length; i++) {
            options.add(new SelectOption(String.valueOf(i + 1), possibleValues[i]));
        }
        SelectOption option = promptToSelectOption(promptMessagePrefix, promptMessageSuffix, options,
                batchModeErrorMessage);
        return option.getValue();
    }

    /**
     *
     *
     * @param promptMessage
     * @param possibleValues
     * @param batchModeErrorMessage
     * @return
     * @throws GitFlowFailureException
     */
    public String promptSelection(String promptMessage, String[] possibleValues,
            GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        return promptSelection(promptMessage, possibleValues, null, batchModeErrorMessage);
    }

    /**
     *
     *
     * @param promptMessage
     * @param possibleValues
     * @param defaultValue
     * @return
     * @throws GitFlowFailureException
     */
    public String promptSelection(String promptMessage, String[] possibleValues, String defaultValue)
            throws GitFlowFailureException {
        return promptSelection(promptMessage, possibleValues, defaultValue, null);
    }

    /**
     *
     *
     * @param promptMessage
     * @param possibleValues
     * @param defaultValue
     * @param batchModeErrorMessage
     * @return
     * @throws GitFlowFailureException
     */
    public String promptSelection(String promptMessage, String[] possibleValues, String defaultValue,
            GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        if (interactiveMode) {
            if (possibleValues == null || possibleValues.length == 0) {
                throw new GitFlowFailureException(getEmptyListOfPossibleValuesMessage());
            }
            try {
                if (defaultValue != null) {
                    return prompt(promptMessage.toString(), Arrays.asList(possibleValues), defaultValue);
                } else {
                    return prompt(promptMessage.toString(), Arrays.asList(possibleValues));
                }
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get user selection from user prompt"));
            }
        } else {
            if (batchModeErrorMessage != null) {
                throw new GitFlowFailureException(batchModeErrorMessage);
            } else if (isPossibleValue(defaultValue, possibleValues)) {
                return defaultValue;
            } else {
                throw new GitFlowFailureException(getInteractiveModeRequiredMessage());
            }

        }
    }

    private boolean isPossibleValue(String value, String[] possibleValues) throws GitFlowFailureException {
        if (possibleValues == null || possibleValues.length == 0) {
            throw new GitFlowFailureException(getEmptyListOfPossibleValuesMessage());
        }
        if (StringUtils.isNotBlank(value)) {
            for (String possibleValue : possibleValues) {
                if (value.equalsIgnoreCase(possibleValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private GitFlowFailureInfo getEmptyListOfPossibleValuesMessage() {
        return new GitFlowFailureInfo("Empty list of possible values provided for user selection",
                "Please report the error in the GBLD JIRA.");
    }

    public SelectOption promptToSelectOption(String promptMessagePrefix, String promptMessageSuffix,
            List<SelectOption> options, GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        if (interactiveMode) {
            if (options == null || options.size() == 0) {
                throw new GitFlowFailureException("Empty list of possible options provided for user selection",
                        "Please report the error in the GBLD JIRA.");
            }
            StringBuilder promptMessage = new StringBuilder();
            if (!StringUtils.isBlank(promptMessagePrefix)) {
                promptMessage.append(promptMessagePrefix);
                promptMessage.append(LS);
            }
            List<String> optionKeys = new ArrayList<String>();
            Map<String, SelectOption> optionsByKey = new HashMap<>();
            for (SelectOption option : options) {
                promptMessage.append(option.getKey() + ". " + option.getText() + LS);
                optionKeys.add(option.getKey());
                optionsByKey.put(option.getKey(), option);
            }
            if (!StringUtils.isBlank(promptMessageSuffix)) {
                promptMessage.append(promptMessageSuffix);
            }
            try {
                String answer = prompt(promptMessage.toString(), optionKeys);
                return optionsByKey.get(answer);
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get user selection from user prompt"));
            }
        } else {
            throw new GitFlowFailureException(
                    batchModeErrorMessage != null ? batchModeErrorMessage : getInteractiveModeRequiredMessage());
        }
    }

    public SelectOption promptToSelectFromOrderedListAndOptions(String promptMessagePrefix, String promptMessageSuffix,
            List<String> possibleValues, List<SelectOption> preOptions, List<SelectOption> postOptions,
            GitFlowFailureInfo batchModeErrorMessage) throws GitFlowFailureException {
        if ((possibleValues == null || possibleValues.size() == 0) && (preOptions == null || preOptions.size() == 0)
                && (postOptions == null || postOptions.size() == 0)) {
            throw new GitFlowFailureException("Empty list of possible values provided for user selection",
                    "Please report the error in the GBLD JIRA.");
        }
        List<SelectOption> options = new ArrayList<>();
        if (preOptions != null && preOptions.size() > 0) {
            for (SelectOption option : preOptions) {
                options.add(option);
            }
        }
        if (possibleValues != null && possibleValues.size() > 0) {
            for (int i = 0, size = possibleValues.size(); i < size; i++) {
                options.add(new SelectOption(String.valueOf(i + 1), possibleValues.get(i)));
            }
        }
        if (postOptions != null && postOptions.size() > 0) {
            for (SelectOption option : postOptions) {
                options.add(option);
            }
        }
        return promptToSelectOption(promptMessagePrefix, promptMessageSuffix, options, batchModeErrorMessage);
    }

    public static class SelectOption {
        private String key;
        private String value;
        private String text;

        public SelectOption(String aKey, String aValue, String aText) {
            key = aKey;
            value = aValue;
            text = aText;
        }

        public SelectOption(String aKey, String aValue) {
            this(aKey, aValue, aValue);
        }

        /**
         * @return the key
         */
        public String getKey() {
            return key;
        }

        /**
         * @return the value
         */
        public String getValue() {
            return value;
        }

        /**
         * @return the text
         */
        public String getText() {
            return text;
        }
    }

}
