package net.corda.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchemasTests {
    @Test
    void cryptoTopicsShouldLookNiceInJavaApi() {
        assertNotNull(Schemas.Crypto.EVENT_TOPIC);
        assertNotNull(Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.HSM_CONFIG_LABEL_TOPIC);
        assertNotNull(Schemas.Crypto.HSM_CONFIG_TOPIC);
        assertNotNull(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.KEY_REGISTRATION_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.MEMBER_CONFIG_TOPIC);
        assertNotNull(Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC);
        assertNotNull(Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC);
        assertNotNull(Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC);
    }
}
