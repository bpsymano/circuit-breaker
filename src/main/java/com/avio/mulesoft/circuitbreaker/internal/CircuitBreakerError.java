package com.avio.mulesoft.circuitbreaker.internal;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public enum CircuitBreakerError implements ErrorTypeDefinition<CircuitBreakerError> {
    CIRCUIT_OPEN,
    CIRCUIT_ERROR
}