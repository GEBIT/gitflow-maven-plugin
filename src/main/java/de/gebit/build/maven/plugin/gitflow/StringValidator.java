//
// StringValidator.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Interface for validators of string values.
 *
 * @author VMedvid
 */
public interface StringValidator {

    /**
     * Validates passed string value and returns a validation result. Validation
     * result should always be not <code>null</code>.
     *
     * @param aValue the value to be validated
     * @return the validation result
     */
    ValidationResult validate(String aValue);

}
