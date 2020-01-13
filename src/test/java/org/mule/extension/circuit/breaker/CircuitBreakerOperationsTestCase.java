package org.mule.extension.circuit.breaker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.junit.Ignore;
import org.junit.Test;

public class CircuitBreakerOperationsTestCase extends MuleArtifactFunctionalTestCase {

  /**
   * Specifies the mule config xml with the flows that are going to be executed in the tests, this file lives in the test resources.
   */
  @Override
  protected String getConfigFile() {
    return "test-mule-config.xml";
  }

  @Test
  public void executeDumpConfigOperation() throws Exception {
    String payloadValue = ((String) flowRunner("dumpConfig").run()
                                      .getMessage()
                                      .getPayload()
                                      .getValue());
    assertThat(payloadValue, is("Circuit Breaker Config [breakerName=test-circuit-breaker-config, objectStore=_defaultInMemoryObjectStore, tripThreshold=5, tripResetTime=30000]"));
  }

  @Test
  @Ignore
  public void executeFilterOperation() throws Exception {
    String payloadValue = ((String) flowRunner("filter")
                                      .run()
                                      .getMessage()
                                      .getPayload()
                                      .getValue());
    assertThat(payloadValue, is("test::TRY_BLOCK::ErrorHandling::filter exception"));
  }
}
