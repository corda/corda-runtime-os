package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingletonSerializeAsTokenJavaApiTest {
    private static final String CUSTOM_NAME = "Custom singleton name";

    static class MyService1 implements SingletonSerializeAsToken {
    }

    static class MyService2 implements SingletonSerializeAsToken {
        @NotNull
        @Override
        public String getTokenName() {
            return CUSTOM_NAME;
        }
    }

    @Test
    void singletonSerializeAsTokenWithNoOverrides() {
        SingletonSerializeAsToken service = new MyService1();
        assertEquals(MyService1.class.getName(), service.getTokenName());
    }

    @Test
    void singletonSerializeAsTokenWithOverriddenTokenName() {
        SingletonSerializeAsToken service = new MyService2();
        assertEquals(CUSTOM_NAME, service.getTokenName());
    }
}
