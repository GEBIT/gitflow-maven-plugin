//
// ValidationResult.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Validation result with information about validity and a message in case of invalid result.
 *
 * @author VMedvid
 */
public class ValidationResult {

    public static final ValidationResult VALID = new ValidationResult();

    private boolean valid = true;
    private String invalidMessage;

    /**
     * Creates an invalid validation result with passed message.
     *
     * @param aInvalidMessage the invalid message
     */
    public ValidationResult(String aInvalidMessage) {
        invalidMessage = aInvalidMessage;
        valid = false;
    }

    /**
     * Creates a valid result.
     */
    public ValidationResult() {
        valid = true;
    }

    /**
     * Creates a valid result or an invalid result without message depending on passed flag.
     *
     * @param isValid <code>true</code> if a valid result should be created.
     */
    public ValidationResult(boolean isValid) {
        valid = isValid;
    }

    /**
     * Checks if result is valid or not.
     *
     * @return <code>true</code> if result is valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the invalid message (can be <code>null</code>).
     *
     * @return the invalid message (can be <code>null</code>)
     */
    public String getInvalidMessage() {
        return invalidMessage;
    }

    @Override
    public String toString() {
        return "ValidationResult [valid=" + valid + ", invalidMessage=" + invalidMessage + "]";
    }
}
