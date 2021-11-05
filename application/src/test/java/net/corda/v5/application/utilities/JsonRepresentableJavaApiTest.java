package net.corda.v5.application.utilities;

import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class JsonRepresentableJavaApiTest {

    private final TestClass jsonRepresentable = new TestClass();

    @Test
    public void toJsonString() {
        String name = jsonRepresentable.toJsonString();

        Assertions.assertThat(name).isNotNull();
        Assertions.assertThat(name).isEqualTo("test");
    }

    static class TestClass implements JsonRepresentable {

        @NotNull
        @Override
        public String toJsonString() {
            return "test";
        }
    }
}
