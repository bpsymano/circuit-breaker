
package com.avio.mulesoft.circuitbreaker.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import java.util.Date;
import java.util.concurrent.locks.Lock;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.lock.LockFactory;
import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.exception.ModuleException;

public class CircuitBreakerOperations {

	private static final Log LOG = LogFactory.getLog(CircuitBreakerOperations.class);
	
	@Inject
	LockFactory lockFactory;
	
	@Inject
	private ObjectStoreManager objectStoreManager;

	/*
	 *******************************************************************
	 *
	 * 	Utility methods
	 * 
	 ********************************************************************
	 */	
	
	private Lock acquireLock(String objectStoreReference) {
		return lockFactory.createLock(objectStoreReference);
	}
	
	private ObjectStore<?> getObjectStore(CircuitBreakerConfiguration config) {
		return objectStoreManager.<ObjectStore<Integer>>getObjectStore(config.getObjectStoreReference());
	}
	
	private String getFailureCountKey(CircuitBreakerConfiguration config) {
		return String.format("%s.failureCount", config.getBreakerName());
	}
	
	private String getFailurePointKey(CircuitBreakerConfiguration config) {
		return String.format("%s.failurePoint", config.getBreakerName());
	}

	private boolean timeoutExceeded(Date failureWindow, long tripResetTime, String breakerConfigName) {	
		LOG.debug(breakerConfigName + "::timeoutExceeded::tripResetTime: " + tripResetTime);
		LOG.debug(breakerConfigName + "::timeoutExceeded::breakerTrippedDate: " + failureWindow);		
		return System.currentTimeMillis() - failureWindow.getTime() > tripResetTime;
	}
	
	private boolean openWithTimeoutLapse(ObjectStore<Date> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {			
		Date failurePoint = null;
		String failurePointKey = getFailurePointKey(config);
		if (objectStore.contains(failurePointKey)) {
			failurePoint = objectStore.retrieve(failurePointKey);
		}
		LOG.debug(config.getBreakerName() + "::openWithTimeoutLapse::" + failurePoint);
		return failurePoint != null && timeoutExceeded(failurePoint, config.getTripResetTime(), config.getBreakerName());
	}
	
	private boolean isTripThresholdReached(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		Integer failureCount = 0;
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(getFailureCountKey(config))) {
			failureCount = objectStore.retrieve(failureCountKey);
		} else {
			objectStore.store(failureCountKey, 0);
		}
		LOG.debug(config.getBreakerName() + "::isTripThresholdReached");	
		return failureCount >= config.getTripThreshold();
	}
	
	private boolean isFailuresBelowTrip(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {	
		Integer failureCount = 0;
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(getFailureCountKey(config))) {
			failureCount = objectStore.retrieve(failureCountKey);
		}
		LOG.debug(config.getBreakerName() + "::isFailuresBelowTrip");	
		return failureCount < config.getTripThreshold();
	}

	private void incrementFailureCount(ObjectStore<Integer> objectStore, String failureCountKey, String breakerConfigName) throws ObjectStoreException {
		Integer failureCount = 0;
		if (objectStore.contains(failureCountKey)) {
			failureCount = objectStore.retrieve(failureCountKey);
			objectStore.remove(failureCountKey);
		}
		objectStore.store(failureCountKey, failureCount + 1);
		LOG.debug(breakerConfigName + "::incrementFailureCount");
	}
	
	private void resetFailureCount(ObjectStore<Integer> objectStore, String failureCountKey, String breakerConfigName) throws ObjectStoreException {
		if (objectStore.contains(failureCountKey)) {
			objectStore.remove(failureCountKey);
		}
		objectStore.store(failureCountKey, 0);
		LOG.debug(breakerConfigName + "::resetFailureCount");
	}
	
	private void resetFailurePoint(ObjectStore<Date> objectStore, String failurePointKey, String breakerConfigName) throws ObjectStoreException {
		if (objectStore.contains(failurePointKey)) {
			objectStore.remove(failurePointKey);
		}
		LOG.debug(breakerConfigName + "::resetFailurePoint");
	}
	
	private void initFailurePoint(ObjectStore<Date> objectStore, String failurePointKey, String breakerConfigName) throws ObjectStoreException {
		if (!objectStore.contains(failurePointKey)) {
			objectStore.store(failurePointKey, new Date());
		} else {
			LOG.debug(breakerConfigName + "::initFailurePoint already set - waiting for expiration");
		}
		LOG.debug(breakerConfigName + "::initFailurePoint");
	}
	
	/*
	 *******************************************************************
	 *
	 * 	Processors
	 * 
	 ********************************************************************
	 */	
	
	@Summary("Outputs the cicuit-breaker's configuration settings")	
	@MediaType(value = ANY, strict = false)
	public String dumpConfig(@Config CircuitBreakerConfiguration config) {
    		return config.breakerConfig();
    }
	
	@Summary("Triggers circuit logic utilizing failed attempts and trip reset time. Can tip with or without a specific error type occuring.")	
	@SuppressWarnings("unchecked")
	@MediaType(value = ANY, strict = false)
	@Throws(ExecuteErrorsProvider.class)
	public void trip(@Config CircuitBreakerConfiguration config, 
			@Optional @DisplayName("Error Type") @Summary("The string representaiotn of the ErrorType that you want the breaker to trip on. e.g. HTTP:CONNECTIVIY") String errorType, 
			@Optional @Content Error error) {		
		Lock lock = null;
		ObjectStore<Integer> countObjectStore = null;
		ObjectStore<Date> dateObjectStore = null;
		if (errorType != null) {
			if (errorType.equalsIgnoreCase(error.getErrorType().toString())) {
				LOG.info(config.getBreakerName() + "::trip triggered [" + error.getErrorType().toString() + "] comparing to [" + errorType + "]");
				try {
					lock = acquireLock(config.getObjectStoreReference());
					countObjectStore = (ObjectStore<Integer>) getObjectStore(config);
					dateObjectStore = (ObjectStore<Date>) getObjectStore(config);
					try {
						incrementFailureCount(countObjectStore, getFailureCountKey(config), config.getBreakerName());
						if (isTripThresholdReached(countObjectStore, config)) {
							LOG.debug(config.getBreakerName() + "::failure count matches trip threshold [" + config.getTripThreshold() + "]");
							initFailurePoint(dateObjectStore, getFailurePointKey(config), config.getBreakerName());							
//							dateObjectStore.store(getFailurePointKey(config), new Date());
						}
					} finally {
						lock.unlock();
					}
				} catch (Exception e) {
					throw new ModuleException(CircuitBreakerError.CIRCUIT_ERROR, e);
				}
			}
		} else {
			LOG.info(config.getBreakerName() + "::trip triggered");
			try {
				lock = acquireLock(config.getObjectStoreReference());
				countObjectStore = (ObjectStore<Integer>) getObjectStore(config);
				dateObjectStore = (ObjectStore<Date>) getObjectStore(config);
				try {
					incrementFailureCount(countObjectStore, getFailureCountKey(config), config.getBreakerName());
					if (isTripThresholdReached(countObjectStore, config)) {
						LOG.debug(config.getBreakerName() + "::failure count matches trip threshold [" + config.getTripThreshold() + "]");
						initFailurePoint(dateObjectStore, getFailurePointKey(config), config.getBreakerName());
//						dateObjectStore.store(getFailurePointKey(config), new Date());
					}
				} finally {
					lock.unlock();
				}
			} catch (Exception e) {
				throw new ModuleException(CircuitBreakerError.CIRCUIT_ERROR, e);
			}
		}
	}

	@Summary("Checks whether the circuit has exceeded the number of  failed attempts and, if the circuit is already open, checks to see if the trip reset time has lapsed, re-enabling the circuit.")
	@SuppressWarnings("unchecked")
	@Throws(ExecuteErrorsProvider.class)
	@MediaType(value = ANY, strict = false)
	public void filter(@Config CircuitBreakerConfiguration config) {
		Lock lock = null;
		try {
			lock = acquireLock(config.getObjectStoreReference());
			lock.lock();
			final ObjectStore<Integer> countObjectStore = (ObjectStore<Integer>)getObjectStore(config);
			final ObjectStore<Date> dateObjectStore = (ObjectStore<Date>)getObjectStore(config);			
			try {
				LOG.info(config.getBreakerName() + "::circuit-beaker:filter applied");
				if (isFailuresBelowTrip(countObjectStore, config)) {
					LOG.info(config.getBreakerName() + "::circuit-beaker:filter - failure count below threshold");
					return;
				} 
				if (openWithTimeoutLapse(dateObjectStore, config)) {
					LOG.info(config.getBreakerName() + "::circuit-beaker:filter - trip timeout exceeded, count reset");
					resetFailurePoint(dateObjectStore, getFailurePointKey(config), config.getBreakerName());
					resetFailureCount(countObjectStore, getFailureCountKey(config), config.getBreakerName());
					return;
				} 
				LOG.info(config.getBreakerName() + "::circuit-breaker:filter ACTIVATED");
				throw new CircuitOpenException();
			} finally {
				lock.unlock();
			}
		} catch (Exception e) {
			throw new ModuleException(CircuitBreakerError.CIRCUIT_ERROR, e);
		}
	}

}