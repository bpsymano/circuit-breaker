package com.avio.mulesoft.circuitbreaker.internal;

import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import com.google.gson.JsonObject;

import org.mule.runtime.extension.api.annotation.param.Optional;

/**
 * This class represents an extension configuration, values set in this class
 * are commonly used across multiple operations since they represent something
 * core from the extension.
 */
@Operations(CircuitBreakerOperations.class)
public class CircuitBreakerConfiguration {

	@Parameter
	@Summary("The ObjectStore reference name")
	@Optional(defaultValue = "_defaultInMemoryObjectStore")
	private String objectStoreReference;
	
	@Parameter
	@Summary("The amount of failures (errors thrown/caught) until the circuit breaker is tripped/open")
	@Optional(defaultValue = "3")
	private Integer tripThreshold;

	@Parameter
	@Summary("How long to wait (in milliseconds) until the breaker's failure count is reset")
	@Optional(defaultValue = "60000")
	private Long tripResetTime;

	/**
	 * The breaker configuration is specific to an integration with the expectation
	 * that there may be multiple breaker configs. The name is used to identified
	 * specific configurations, as well as used logging for clarity.
	 */
	@Parameter
	@Summary("The name of the breaker")
	private String breakerName;

	public String getObjectStoreReference() {
		return objectStoreReference;
	}

	public void setObjectStoreReference(String objectStoreReference) {
		this.objectStoreReference = objectStoreReference;
	}

	public void setTripThreshold(Integer tripThreshold) {
		this.tripThreshold = tripThreshold;
	}

	public Integer getTripThreshold() {
		return tripThreshold;
	}

	public void setTripResetTime(Long tripTimeout) {
		this.tripResetTime = tripTimeout;
	}

	public Long getTripResetTime() {
		return tripResetTime;
	}

	public void setBreakerName(String breakerName) {
		this.breakerName = breakerName;
	}

	public String getBreakerName() {
		return breakerName;
	}

	public String breakerConfig() {
		JsonObject breakerConfig = new JsonObject();
		breakerConfig.addProperty("breakerName", breakerName);
		breakerConfig.addProperty("objectStoreReference", objectStoreReference);
		breakerConfig.addProperty("tripThreshold", tripThreshold);
		breakerConfig.addProperty("tripResetTime", tripResetTime);
		JsonObject breaker = new JsonObject();
		breaker.add("circuitBreaker", breakerConfig);
		return breaker.toString();
	}

}
