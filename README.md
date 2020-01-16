# What?
This is a Custom Connector that implements the Circuit Breaker design pattern (see below). This component acts as a proxy with some state capbilities to manage accessing the downstream resource.
(https://martinfowler.com/bliki/CircuitBreaker.html)

# Why?
The reason for the circuit breaker pattern is to prevent a dependent resource problem for escalating and bringing your application network to a halt. With the layered services approach (or any dependency on a resource outside one’s control), latency and service availability issues could bubble up the transaction stack and render your service(s) unavailable. We want more control over handling issues with these dependencies.

Implementation of this pattern will shield your application from repeatedly trying to access a resource that is likely to fail. Within your application, you are now able to handle issues related to a CIRCUIT_OPEN without wasting compute resources, and potentially further exacerbating a problem with the downstream resource.

The pattern defines three states, CLOSED, OPEN and HALF_OPEN, this connector implements all threes as logical states, in that the state of the circuit is based on meeting the number of failures count, the timeout, and whether a newly CLOSE(d) circuit gets tripped again.

* __CLOSED:__ The request is routed to a resource’s operation. The connector maintains a count of the number of recent failures, and if the call to the operation is unsuccessful the connector  increments this count.
  - If the number of recent failures exceeds a specified threshold, the connector OPENs the circuit. Once OPEN, and with each subsequent request, the connector vetts the request against the resetTime. If the request is within the resetTime window, the connector returns a MuleSoft Error CIRCUIT-BREAKER:CIRCUIT_OPEN, otherwise the connector behaves as if the circuit is CLOSED.
   - The purpose of the resetTime setting is to define the time window for which the downstream resource access issues before the outbound call is attempted again.
* __OPEN:__ The request to the resource fails fast and a MuleSoft Error CIRCUIT-BREAKER:CIRCUIT_OPEN is returned to the calling flow.
* __HALF_OPEN:__ When a newly CLOSE(d) circuit experiences an Error, the circuit is immediately put into an OPEN state.


# Implementation details

### Operations
The Circuit Breaker connector supports the following opertions:
* __circuit-breaker:dump-config__
* __circuit-breaker:filter__
* __circuit-breaker:trip__
  - optional ***ErrorType*** parameter to trip on a specific ErrorType
  - accomodates on-error mule configuration based on ErrorType, as well as supporting forcing a circuit breaker to trip within the calling flow's logic.

### Configuration
* __name:__ The name of the circuit breaker configuration (for a specific resource call, can have multiple configurations)
* __objectStoreReference:__ the name of your preferred objectStore reference
  - _defaultPersistentObjectStore
  - _defaultInMemoryObjectStore
  - Spring configured Objectstore
* __breakerName:__ a name that uniquely identifies this configuration.
  - It is used in debug for the connector to distinguish bewteen multiple configurations.
* __tripThreshold:__ An Integer defining the number of Errors tolerated before opening the circuit.
* __tripResetTime:__ the time in milliseconds that that circuite will remain open.


```xml
<circuit-breaker:config name="Circuit_Breaker_Config"
  objectStoreReference="_defaultInMemoryObjectStore"
  breakerName="myTestBreaker"
  tripThreshold="5"
  tripResetTime="120000"
  doc:name="Circuit Breaker Config" />
```

### Persistance
This connector is built with a dependency on the MuleSoft Objectstore, supporting both persistent and in memory objectStores, as such the client application will need to include Objectstore V2 as a dependency along with the CircuitBreaker if intending to leaverage persistant connector state.

```xml
<dependency>
  <groupId>com.avioconsulting.mulesoft.connector</groupId>
  <artifactId>circuit-breaker</artifactId>
  <version>1.0.0</version>
  <classifier>mule-plugin</classifier>
</dependency>
<dependency>
  <groupId>org.mule.connectors</groupId>
  <artifactId>mule-objectstore-connector</artifactId>
  <version>1.1.3</version>
  <classifier>mule-plugin</classifier>
</dependency>
```
# How?

### MuleSoft application

```xml
<flow name="tripWithErrorType" >
  <http:listener config-ref="in-http-listener-config" path="/error" doc:name="Listener" />

  <logger level="INFO" message="START" />

  <circuit-breaker:dump-config
    target="breakerConfig"
    doc:name="Dump config"
    config-ref="Circuit_Breaker_Config"/>

  <logger level="INFO" message="vars.breakerConfig::#[vars.breakerConfig]" />

  <circuit-breaker:filter doc:name="Filter" config-ref="Circuit_Breaker_Config"/>

  <http:request config-ref="http-request-config" path="connect-error" method="GET" doc:name="HTTP"/>

  <logger level="INFO" message="END" />
  <error-handler >
    <on-error-propagate enableNotifications="false" logException="true" doc:name="On Error Propagate" type="HTTP:CONNECTIVITY">
      <logger level="INFO" message="END-Error" />
      <circuit-breaker:trip config-ref="Circuit_Breaker_Config" errorType="HTTP:CONNECTIVITY"/>
    </on-error-propagate>
  </error-handler>
</flow>
```

```xml
<flow name="tripWithoutErrorType" >
  <http:listener config-ref="in-http-listener-config" path="/no-error" doc:name="Listener"/>
  <logger level="INFO" message="START" />

  <circuit-breaker:dump-config
    target="breakerConfig"
    doc:name="Dump config"
    config-ref="Circuit_Breaker_Config"/>

  <logger level="INFO" message="vars.breakerConfig::#[vars.breakerConfig]" />

  <circuit-breaker:filter doc:name="Filter" config-ref="Circuit_Breaker_Config"/>

  <http:request config-ref="http-request-config" path="connect-error" method="GET" doc:name="HTTP"/>

  <logger level="INFO" message="END" />
  <error-handler >
    <on-error-propagate enableNotifications="false" logException="true" doc:name="On Error Propagate">
      <logger level="INFO" message="END-Error" />
      <circuit-breaker:trip config-ref="Circuit_Breaker_Config"/>
    </on-error-propagate>
  </error-handler>
</flow>
```

### Debug output

To enable DEBUG output on hte circuit breaker connector, add the following configuration to you log4j.xml file:
```xml
<AsyncLogger name="com.avioconsulting.mulesoft.circuitbreaker" level="DEBUG"/>
```

### Using maven dependency
First, clone this repository and run ```mvn clean install``` to install this maven project in your local .m2 repository.


When you install this project into your machine's local .m2 repository, You can include this dependency(see below) in your mule projects. When you included this dependency in your project's pom.xml, AVIO's circuit-breaker connector automatically shows up in mule project's pallete.

```xml
<dependency>
    <groupId>com.avioconsulting.mulesoft.connector</groupId>
    <artifactId>circuit-breaker</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Push to anypoint private exchange
Alternatively, you can push this mule custom component to your anypoint organization's private exchange so that all developers inside that organization can use it. Here are the steps,

* First, Clone this GitHub repository into your local machine.
* Get your Anypoint's organization ID and
	* Place it in pom.xml group id tag. ```<groupId>YOUR_ORG_ID</groupId>```.
	* Place it in url tag under distribution management tag. See below

```xml
<dependency>
    <groupId>YOUR_ORG_ID</groupId>
    <artifactId>circuit-breaker</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

```xml
<distributionManagement>
	<repository>
		<id>[client specific]-anypoint-exchange</id>
		<name>Corporate Repository</name>
		<url>https://maven.anypoint.mulesoft.com/api/v1/organizations/YOUR_ORG_ID/maven</url>
		<layout>default</layout>
	</repository>
</distributionManagement>
```

* Include exchange credentials in your settings.xml under servers section and with the matching server id with the repository id in pom's distribution management tag.
* Run ```mvn clean deploy``` to deploy this custom component into your anypoint exchange.
* Now, click on "search on exchange" in your mule project pallete, login and install component in your project.
