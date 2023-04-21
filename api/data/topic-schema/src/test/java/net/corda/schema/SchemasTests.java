package net.corda.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchemasTests {
    @Test
    void cryptoTopicsShouldLookNiceInJavaApi() {
        assertNotNull(Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC);
    }
}
