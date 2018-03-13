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
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

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

    /**
     * Creates an instance of {@link ExtendedPrompter}.
     *
     * @param aPrompter
     *            the prompter to be used
     * @param isInteractiveMode
     *            <code>true</code> if interactive mode is active
     */
    public ExtendedPrompter(Prompter aPrompter, boolean isInteractiveMode) {
        prompter = aPrompter;
        interactiveMode = isInteractiveMode;
    }

    @Override
    public String prompt(String message) throws PrompterException {
        return prompter.prompt(message);
    }

    @Override
    public String prompt(String message, String defaultReply) throws PrompterException {
        return prompter.prompt(message, defaultReply);
    }

    @Override
    public String prompt(String message, List possibleValues) throws PrompterException {
        return prompter.prompt(message, possibleValues);
    }

    @Override
    public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
        return prompter.prompt(message, possibleValues, defaultReply);
    }

    @Override
    public String promptForPassword(String message) throws PrompterException {
        return prompter.promptForPassword(message);
    }

    @Override
    public void showMessage(String message) throws PrompterException {
        prompter.showMessage(message);
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
        if (interactiveMode) {
            if (possibleValues == null || possibleValues.length == 0) {
                throw new GitFlowFailureException("Empty list of possible values provided for user selection",
                        "Please report the error in the GBLD JIRA.");
            }
            StringBuilder promptMessage = new StringBuilder();
            if (!StringUtils.isBlank(promptMessagePrefix)) {
                promptMessage.append(promptMessagePrefix);
                promptMessage.append(LS);
            }
            List<String> numberedList = new ArrayList<String>();
            int index = 0;
            for (String possibleValue : possibleValues) {
                String pos = String.valueOf(++index);
                promptMessage.append(pos + ". " + possibleValue + LS);
                numberedList.add(pos);
            }
            if (!StringUtils.isBlank(promptMessageSuffix)) {
                promptMessage.append(promptMessageSuffix);
            }
            try {
                String answer = prompt(promptMessage.toString(), numberedList);
                int pos = Integer.parseInt(answer);
                return possibleValues[pos - 1];
            } catch (PrompterException e) {
                throw new GitFlowFailureException(e,
                        createPromptErrorMessage("Failed to get user selection from user prompt"));
            }
        } else {
            throw new GitFlowFailureException(
                    batchModeErrorMessage != null ? batchModeErrorMessage : getInteractiveModeRequiredMessage());
        }
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

}
