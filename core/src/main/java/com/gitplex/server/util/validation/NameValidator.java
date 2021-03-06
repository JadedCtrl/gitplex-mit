package com.gitplex.server.util.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.gitplex.server.util.StringUtils;
import com.gitplex.server.util.validation.annotation.Name;

public class NameValidator implements ConstraintValidator<Name, String> {
	
	// insert spaces in invalid chars in order to get a pretty display for 
	// name validation error message. Refer to @Name annotation for more detail.
	public static final String invalidChars = ", / \\ : * ? \" < > | [ ]";
	
	public void initialize(Name constaintAnnotation) {
	}

	public boolean isValid(String value, ConstraintValidatorContext constraintContext) {
		if (value == null)
			return true;

		return StringUtils.containsNone(value, StringUtils.deleteWhitespace(invalidChars));
	}
}
