package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class SingletonSerializeAsTokenJavaApiTest {

    static class MyService1 implements SingletonSerializeAsToken {
    }

    static class MyService2 implements SingletonSerializeAsToken {

        @NotNull
        @Override
        public String getTokenName() {
            return "Custom singleton name";
        }
    }

    @Test
    public void singletonSerializeAsTokenWithNoOverrides() {
        new MyService1();
    }

    @Test
    public void singletonSerializeAsTokenWithOverriddenTokenName() {
        new MyService2();
    }
}
