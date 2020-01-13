package com.avio.mulesoft.circuitbreaker.internal;

import org.mule.runtime.extension.api.exception.ModuleException;

public final class CircuitOpenException extends ModuleException {

	private static final long serialVersionUID = -8879269665593539486L;

	public CircuitOpenException() {
		super("circuit-breaker::open", CircuitBreakerError.CIRCUIT_OPEN);
	}
	
	public CircuitOpenException(Exception cause) {
		super(CircuitBreakerError.CIRCUIT_OPEN, cause);
	}
}