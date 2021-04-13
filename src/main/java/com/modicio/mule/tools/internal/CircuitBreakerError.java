package com.modicio.mule.tools.internal;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public enum CircuitBreakerError implements ErrorTypeDefinition<CircuitBreakerError> {
    CIRCUIT_OPEN,
    CIRCUIT_ERROR
}