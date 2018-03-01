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
import org.apache.maven.plugin.MojoFailureException;
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
     * In case of batch mode an {@link MojoFailureException} will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @return a non-empty value
     * @throws MojoFailureException
     *             in case of batch mode or error while prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, null, null, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no non-empty init value provided.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @return a non-empty value
     * @throws MojoFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue)
            throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no non-empty init value provided.
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
     * @throws MojoFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, null, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty value is entered. If a non-empty init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no non-empty init value provided.
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
     * @throws MojoFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, possibleValues,
                null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param parameterName
     *            the name of the parameter to be used in exception messages
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws MojoFailureException
     *             in case of batch mode or error while prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, StringValidator validator)
            throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, null, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no valid init value provided.
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
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            StringValidator validator) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no valid init value provided.
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
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            StringValidator validator, String missingValueInBatchModeMessage) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, null, null, validator,
                missingValueInBatchModeMessage);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no valid init value provided.
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
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, StringValidator validator) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, null, validator);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no valid init value provided.
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
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues, StringValidator validator) throws MojoFailureException {
        return promptRequiredParameterValue(promptMessage, parameterName, initValue, defaultValue, possibleValues,
                validator, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered. If a valid init value provided it
     * will be returned without prompting.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown if
     * no valid init value provided.
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
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    public String promptRequiredParameterValue(String promptMessage, String parameterName, String initValue,
            String defaultValue, List<String> possibleValues, StringValidator validator,
            String missingValueInBatchModeMessage) throws MojoFailureException {
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
                throw new MojoFailureException("Failed to get " + parameterName, e);
            }
        } else {
            if (StringUtils.isBlank(value)) {
                value = defaultValue;
            }
            if (StringUtils.isBlank(value)) {
                String errorMessage;
                if (!StringUtils.isBlank(missingValueInBatchModeMessage)) {
                    errorMessage = missingValueInBatchModeMessage;
                } else {
                    errorMessage = "No " + parameterName + " set, aborting...";
                }
                throw new MojoFailureException(errorMessage);
            }
            if (!CollectionUtils.isEmpty(possibleValues)) {
                if (!possibleValues.contains(value)) {
                    throw new MojoFailureException("Set " + parameterName + " is not valid, aborting...");
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
                        errorMessage = "Set " + parameterName + " is not valid, aborting...";
                    }
                    throw new MojoFailureException(errorMessage);
                }
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
     * @throws MojoFailureException
     *             in case oferror while prompting
     */
    public boolean promptConfirmation(String promptMessage, Boolean defaultValue, boolean batchModeValue)
            throws MojoFailureException {
        return promptConfirmation(promptMessage, defaultValue, batchModeValue, null);
    }

    /**
     * Prompts for a confirmation with yes/no options. If a non-null
     * batchModeValue provided than this value will be returned in batch mode.
     * Otherwise a {@link MojoFailureException} with batchModeErrorMessage will
     * be thrown.
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
     * @throws MojoFailureException
     *             in case of batchModeValue=<code>null</code> in batch mode or
     *             error while prompting
     */
    public boolean promptConfirmation(String promptMessage, Boolean defaultValue, Boolean batchModeValue,
            String batchModeErrorMessage) throws MojoFailureException {
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
                throw new MojoFailureException("Failed to get user confirmation", e);
            }
        } else {
            if (batchModeValue == null) {
                throw new MojoFailureException(
                        batchModeErrorMessage != null ? batchModeErrorMessage : "Interactive mode is required.");
            }
            return batchModeValue;
        }
    }

    /**
     * Propmpts to select a value from the passed list of possible values by
     * entering a value number. If batch mode is active a
     * {@link MojoFailureException} with batchModeErrorMessage will be thrown.
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
     * @throws MojoFailureException
     *             in case of active batch mode, empty list of possible values
     *             or error while prompting
     */
    public String promptToSelectFromOrderedList(String promptMessagePrefix, String promptMessageSuffix,
            List<String> possibleValues, String batchModeErrorMessage) throws MojoFailureException {
        return promptToSelectFromOrderedList(promptMessagePrefix, promptMessageSuffix,
                possibleValues == null ? null : possibleValues.toArray(new String[possibleValues.size()]),
                batchModeErrorMessage);
    }

    /**
     * Propmpts to select a value from the passed list of possible values by
     * entering a value number. If batch mode is active a
     * {@link MojoFailureException} with batchModeErrorMessage will be thrown.
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
     * @throws MojoFailureException
     *             in case of active batch mode, empty list of possible values
     *             or error while prompting
     */
    public String promptToSelectFromOrderedList(String promptMessagePrefix, String promptMessageSuffix,
            String[] possibleValues, String batchModeErrorMessage) throws MojoFailureException {
        if (interactiveMode) {
            if (possibleValues == null || possibleValues.length == 0) {
                throw new MojoFailureException("Empty list of possible values provided for user selection");
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
                throw new MojoFailureException("Failed to get user selection", e);
            }
        } else {
            throw new MojoFailureException(
                    batchModeErrorMessage != null ? batchModeErrorMessage : "Interactive mode is required.");
        }
    }

}
