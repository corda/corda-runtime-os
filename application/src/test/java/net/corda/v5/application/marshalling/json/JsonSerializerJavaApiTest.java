package net.corda.v5.application.marshalling.json;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.mockito.Mockito.mock;

public class JsonSerializerJavaApiTest {

    private static class TestSerializer implements JsonSerializer<String> {
        @Override
        public void serialize(String item, @NotNull JsonWriter jsonWriter) {
            try {
                jsonWriter.writeRaw("test");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void instantiateSubclassAndCall() {
        TestSerializer ts = new TestSerializer();
        JsonWriter jw = mock(JsonWriter.class);
        ts.serialize("test", jw);
    }
}
