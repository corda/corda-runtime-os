package net.corda.v5.application.marshalling.json;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class JsonDeserializerJavaApiTest {

    private static class TestDeserializer implements JsonDeserializer<String> {
        @NotNull
        @Override
        public String deserialize(@NotNull JsonNodeReader jsonRoot) {
            jsonRoot.hasField("");
            return "";
        }
    }

    @Test
    void instantiateSubClassAndCall() {
        TestDeserializer td = new TestDeserializer();
        JsonNodeReader jnr = mock(JsonNodeReader.class);
        td.deserialize(jnr);
    }
}
