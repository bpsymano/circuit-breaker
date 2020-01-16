package com.avioconsulting.mulesoft.circuitbreaker.internal;

import org.mule.runtime.extension.api.exception.ModuleException;

public final class CircuitOpenException extends ModuleException {

	private static final long serialVersionUID = -8879269665593539486L;

	public CircuitOpenException() {
		super("CIRCUIT-BREAKER::CIRCUIT_OPEN", CircuitBreakerError.CIRCUIT_OPEN);
	}
	
	public CircuitOpenException(Exception cause) {
		super(CircuitBreakerError.CIRCUIT_OPEN, cause);
	}
}