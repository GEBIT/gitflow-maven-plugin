//
// ExtendedPrompter.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

/**
 *
 * @author VMedvid
 */
public class ExtendedPrompter implements Prompter {

    private Prompter prompter;
    private boolean interactiveMode;

    /**
     * @param aPrompter
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
     * @return a non-empty value
     * @throws MojoFailureException
     *             in case of batch mode or error while prompting
     */
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName)
            throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, null, null, null, null);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @return a non-empty value
     * @throws MojoFailureException
     *             in case of empty init value in batch mode or error while
     *             prompting
     */
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue)
            throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, initValue, null, null, null);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
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
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue,
            String defaultValue) throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, initValue, defaultValue, null, null);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
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
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue,
            String defaultValue, List<String> possibleValues) throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, initValue, defaultValue,
                possibleValues, null);
    }

    /**
     * Prompts for a value if interactive mode is enabled. Propmts in a loop
     * until non-empty valid value is entered.<br>
     * In case of batch mode an {@link MojoFailureException} will be thrown.
     *
     * @param promptMessage
     *            the message to be shown in prompt
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws MojoFailureException
     *             in case of batch mode or error while prompting
     */
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName,
            StringValidator validator) throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, null, null, validator);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
     * @param initValue
     *            the init value (can be <code>null</code>)
     * @param validator
     *            the optional validator to validate the non-empty value
     * @return a non-empty valid value
     * @throws MojoFailureException
     *             in case of invalid init value in batch mode or error while
     *             prompting
     */
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue,
            StringValidator validator) throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, initValue, null, validator);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
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
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue,
            String defaultValue, StringValidator validator) throws MojoFailureException {
        return promptRequiredValueIfInteractiveMode(promptMessage, propertyName, initValue, defaultValue, null,
                validator);
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
     * @param propertyName
     *            the name of the property (value) to be used in exception
     *            messages
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
    protected String promptRequiredValueIfInteractiveMode(String promptMessage, String propertyName, String initValue,
            String defaultValue, List<String> possibleValues, StringValidator validator) throws MojoFailureException {
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
                throw new MojoFailureException("Failed to get " + propertyName, e);
            }
        } else {
            if (StringUtils.isBlank(value)) {
                value = defaultValue;
            }
            if (StringUtils.isBlank(value)) {
                throw new MojoFailureException("No " + propertyName + " set, aborting...");
            }
            if (!CollectionUtils.isEmpty(possibleValues)) {
                if (!possibleValues.contains(value)) {
                    throw new MojoFailureException("Set " + propertyName + " is not valid, aborting...");
                }
            }

            if (validator != null && !validator.validate(value).isValid()) {
                throw new MojoFailureException("Set " + propertyName + " is not valid, aborting...");
            }
        }
        return value;
    }

}
