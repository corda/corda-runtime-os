package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class SingletonSerializeAsTokenJavaTest {

    static class MyService1 implements SingletonSerializeAsToken {
    }

    static class MyService2 implements SingletonSerializeAsToken {

        @NotNull
        @Override
        public String getTokenName() {
            return "Custom singleton name";
        }
    }

    class MyService3 implements SingletonSerializeAsToken {

        @NotNull
        @Override
        public SingletonSerializationToken toToken(@NotNull SerializeAsTokenContext context) {
            return SingletonSerializationToken.singletonSerializationToken("Custom singleton name");
        }
    }

    class MyService4 implements SingletonSerializeAsToken {

        @NotNull
        @Override
        public SingletonSerializationToken toToken(@NotNull SerializeAsTokenContext context) {
            return SingletonSerializationToken.singletonSerializationToken(MyService4.class);
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

    @Test
    public void singletonSerializeAsTokenWithOverriddenSingletonTokenUsingStringName() {
        new MyService3();
    }

    @Test
    public void singletonSerializeAsTokenWithOverriddenSingletonTokenUsingClassName() {
        new MyService4();
    }
}
