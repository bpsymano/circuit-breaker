package com.modicio.mule.tools.internal;

import java.util.HashSet;
import java.util.Set;

import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public class ExecuteErrorsProvider implements ErrorTypeProvider {
    @SuppressWarnings("rawtypes")
	@Override
    public Set<ErrorTypeDefinition> getErrorTypes() {
        HashSet<ErrorTypeDefinition> errors = new HashSet<>();
        errors.add(CircuitBreakerError.CIRCUIT_OPEN);
        errors.add(CircuitBreakerError.CIRCUIT_ERROR);
        return errors;
    }
}