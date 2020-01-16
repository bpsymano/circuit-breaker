
package com.avioconsulting.mulesoft.circuitbreaker.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import java.text.SimpleDateFormat;
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
	
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	private static enum State {
		OPEN,
		CLOSED,
		HALF_OPEN
	}
	
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
		return objectStoreManager.<ObjectStore<?>>getObjectStore(config.getObjectStoreReference());
	}
	
	private String getFailureCountKey(CircuitBreakerConfiguration config) {
		return String.format("%s.failureCount", config.getBreakerName());
	}
	
	private String getFailurePointKey(CircuitBreakerConfiguration config) {
		return String.format("%s.failurePoint", config.getBreakerName());
	}
	
	private String getStateKey(CircuitBreakerConfiguration config) {
		return String.format("%s.state", config.getBreakerName());
	}

	private boolean timeoutExceeded(Date failurePoint, long tripResetTime, String breakerConfigName) {			
		return System.currentTimeMillis() - failurePoint.getTime() > tripResetTime;
	}
	
	private boolean openWithTimeoutLapse(ObjectStore<Date> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {			
		Date failurePoint = null;
		String failurePointKey = getFailurePointKey(config);
		if (objectStore.contains(failurePointKey)) {
			failurePoint = objectStore.retrieve(failurePointKey);
		}
		LOG.debug("[openWithTimeoutLapse]::" + config.getBreakerName() + "::" + dateFormat.format(failurePoint));
		return failurePoint != null && timeoutExceeded(failurePoint, config.getTripResetTime(), config.getBreakerName());
	}
	
	private boolean isTripThresholdReached(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		Integer failureCount = 0;
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(failureCountKey)) {
			failureCount = objectStore.retrieve(failureCountKey);
		} else {
			objectStore.store(failureCountKey, 0);
		}
		LOG.debug("[isTripThresholdReached]::" + config.getBreakerName());	
		return failureCount >= config.getTripThreshold();
	}
	
	private boolean isFailuresBelowTrip(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {	
		Integer failureCount = 0;
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(failureCountKey)) {
			failureCount = objectStore.retrieve(failureCountKey);
		}
		LOG.debug("[isFailuresBelowTrip]::" + config.getBreakerName());	
		return failureCount < config.getTripThreshold();
	}

	private void incrementFailureCount(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		Integer failureCount = 0;
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(failureCountKey)) {
			failureCount = objectStore.retrieve(failureCountKey);
			objectStore.remove(failureCountKey);
		}
		objectStore.store(failureCountKey, failureCount + 1);
		LOG.debug("[incrementFailureCount]::" + config.getBreakerName());
	}
	
	private void resetFailureCount(ObjectStore<Integer> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		String failureCountKey = getFailureCountKey(config);
		if (objectStore.contains(failureCountKey)) {
			objectStore.remove(failureCountKey);
		}
		objectStore.store(failureCountKey, 0);
		LOG.debug("[resetFailureCount]::" + config.getBreakerName());
	}
	
	private void resetFailurePoint(ObjectStore<Date> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		String failurePointKey = getFailurePointKey(config);
		if (objectStore.contains(failurePointKey)) {
			objectStore.remove(failurePointKey);
		}
		LOG.debug("[resetFailurePoint]::" + config.getBreakerName());
	}
	
	private void initFailurePoint(ObjectStore<Date> objectStore, CircuitBreakerConfiguration config) throws ObjectStoreException {
		String failurePointKey = getFailurePointKey(config);
		if (!objectStore.contains(failurePointKey)) {
			objectStore.store(failurePointKey, new Date());
		} else {
			LOG.debug("[initFailurePoint] already set - waiting for expiration::" + config.getBreakerName());
		}
		LOG.debug("[initFailurePoint]::" + config.getBreakerName());		
	}
	
	private State getBreakerState(CircuitBreakerConfiguration config) throws ObjectStoreException {
		String stateKey = getStateKey(config);
		State currentState = State.CLOSED;
		ObjectStore<?> genericObjectStore = (ObjectStore<?>) getObjectStore(config);
		if (genericObjectStore.contains(stateKey)) {
			currentState = (State) genericObjectStore.retrieve(stateKey);
		} 
		LOG.debug("[getBreakerState]::" + config.getBreakerName() + "::state::" + currentState);
		return currentState;
	}
	
	private void setBreakerState(ObjectStore<State> objectStore, State currentState, CircuitBreakerConfiguration config) throws ObjectStoreException {
		String stateKey = getStateKey(config);
		if (objectStore.contains(stateKey)) {
			objectStore.remove(stateKey);
		}
		objectStore.store(stateKey, currentState);
		LOG.debug("[setBreakerState]::"  + config.getBreakerName() + "::setting state to::" +  currentState);
	}
	
	@SuppressWarnings("unchecked")
	private Lock processCircuitLogic(CircuitBreakerConfiguration config) throws ObjectStoreException {
		Lock lock;
		ObjectStore<?> genericObjectStore;
		lock = acquireLock(config.getObjectStoreReference());					
		genericObjectStore = (ObjectStore<?>) getObjectStore(config);
		incrementFailureCount(((ObjectStore<Integer>) genericObjectStore), config);
		// error on circuit HALF_OPEN, (re)OPEN circuit again
		if(getBreakerState(config) == State.HALF_OPEN ) {
			LOG.info("[processCircuitLogic]::" + config.getBreakerName() + "::circuit state change::[HALF_OPEN -> OPEN]");
			initFailurePoint(((ObjectStore<Date>) genericObjectStore), config);;
			setBreakerState(((ObjectStore<State>) genericObjectStore), State.OPEN, config);
		} 
		if (isTripThresholdReached(((ObjectStore<Integer>) genericObjectStore), config)) {
			LOG.info("[processCircuitLogic]::" + config.getBreakerName() + "::failure count matches trip threshold [" + config.getTripThreshold() + "]");
			LOG.info("[processCircuitLogic]::" + config.getBreakerName() + "::circuit state change::[CLOSED -> OPEN]");
			initFailurePoint(((ObjectStore<Date>) genericObjectStore), config);
			setBreakerState(((ObjectStore<State>) genericObjectStore), State.OPEN, config);
		}
		return lock;
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
	
	@Summary("Triggers circuit logic utilizing failed attempts and trip reset time. Can trip with or without a specific error type occuring.")	
	@MediaType(value = ANY, strict = false)
	@Throws(ExecuteErrorsProvider.class)
	public void trip(@Config CircuitBreakerConfiguration config, 
			@Optional @DisplayName("Error Type") @Summary("The string representation of the ErrorType that you want the breaker to trip on. e.g. HTTP:CONNECTIVIY") String errorType, 
			@Optional @Content Error error) {		
		Lock lock = null;
		try {
			if (errorType != null) {
				LOG.info("[trip]::" +config.getBreakerName() + "::TRIP triggered [" + error.getErrorType().toString() + "] comparing to [" + errorType + "]");
				if (errorType.equalsIgnoreCase(error.getErrorType().toString())) {
					lock = processCircuitLogic(config);
				}
			} else {
				LOG.info("[trip]::" + config.getBreakerName() + "::TRIP triggered");
				lock = processCircuitLogic(config);
			}
		} catch (Exception e) {
			throw new ModuleException(CircuitBreakerError.CIRCUIT_ERROR, e);
		} finally {
			lock.unlock();
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
			final ObjectStore<?> genericObjectStore = (ObjectStore<?>)getObjectStore(config);
			try {
				LOG.info("[filter]::" + config.getBreakerName() + "::FILTER applied");
				if(getBreakerState(config) == State.OPEN ) {
					if (openWithTimeoutLapse(((ObjectStore<Date>) genericObjectStore), config)) {
						LOG.info("[filter]::" + config.getBreakerName() + "::circuit STATE::" + getBreakerState(config) + "::TRIP timeout exceeded, count reset");
						resetFailureCount(((ObjectStore<Integer>) genericObjectStore), config);
						resetFailurePoint(((ObjectStore<Date>) genericObjectStore), config);
						setBreakerState(((ObjectStore<State>) genericObjectStore), State.HALF_OPEN, config);
						LOG.debug("[filter]::" + config.getBreakerName() + "::circuit STATE::" + getBreakerState(config));
						return;
					}					
					LOG.debug("[filter]::" + config.getBreakerName() + "::circuit STATE::" + getBreakerState(config));
					throw new CircuitOpenException();					
				}
				if (isFailuresBelowTrip(((ObjectStore<Integer>) genericObjectStore), config)) {
					LOG.info("[filter]::" + config.getBreakerName() + "::failure count below threshold");
					LOG.debug("[filter]::" + config.getBreakerName() + "::circuit STATE::" + getBreakerState(config));
					return;
				}
			} finally {
				lock.unlock();
			}
		} catch (CircuitOpenException circuitOpenException) {
			throw circuitOpenException;
		} catch (Exception e) {
			throw new ModuleException(CircuitBreakerError.CIRCUIT_ERROR, e);
		}
	}

}
