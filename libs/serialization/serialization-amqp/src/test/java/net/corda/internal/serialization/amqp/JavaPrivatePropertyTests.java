package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestDescriptorBasedSerializerRegistry;
import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.v5.base.annotations.CordaSerializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaPrivatePropertyTests {
    @CordaSerializable
    static class C {
        private String a;

        C(String a) { this.a = a; }
    }

    @CordaSerializable
    static class C2 {
        private String a;

        C2(String a) { this.a = a; }

        public String getA() { return a; }
    }

    @CordaSerializable
    static class B {
        private Boolean b;

        B(Boolean b) { this.b = b; }

        @SuppressWarnings("unused")
        public Boolean isB() {
            return this.b;
        }
    }

    @CordaSerializable
    static class B2 {
        private Boolean b;

        @SuppressWarnings("unused")
        public Boolean isB() {
            return this.b;
        }

        public void setB(Boolean b) {
            this.b = b;
        }
    }

    @CordaSerializable
    static class B3 {
        private Boolean b;

        @SuppressWarnings("unused")
        // break the BEAN format explicitly (i.e. it's not isB)
        public Boolean isb() {
            return this.b;
        }

        public void setB(Boolean b) {
            this.b = b;
        }
    }

    @CordaSerializable
    static class C3 {
        private Integer a;

        public Integer getA() {
            return this.a;
        }

        @SuppressWarnings("unused")
        public Boolean isA() {
            return this.a > 0;
        }

        public void setA(Integer a) {
            this.a = a;
        }
    }

    @Test
    public void singlePrivateBooleanWithConstructor() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        B b = new B(true);
        B b2 = des.deserialize(ser.serialize(b, TestSerializationContext.testSerializationContext), B.class, TestSerializationContext.testSerializationContext);
        assertEquals (b.b, b2.b);
    }

    @Test
    public void singlePrivateBooleanWithNoConstructor() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        B2 b = new B2();
        b.setB(false);
        B2 b2 = des.deserialize(ser.serialize(b, TestSerializationContext.testSerializationContext), B2.class, TestSerializationContext.testSerializationContext);
        assertEquals (b.b, b2.b);
    }

    @Test
    public void testCapitilsationOfIs() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        B3 b = new B3();
        b.setB(false);
        B3 b2 = des.deserialize(ser.serialize(b, TestSerializationContext.testSerializationContext), B3.class, TestSerializationContext.testSerializationContext);

        // since we can't find a getter for b (isb != isB) then we won't serialize that parameter
        assertThat(b2.b).isNull();
    }

    @Test
    public void singlePrivateIntWithBoolean() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        C3 c = new C3();
        c.setA(12345);
        C3 c2 = des.deserialize(ser.serialize(c, TestSerializationContext.testSerializationContext), C3.class, TestSerializationContext.testSerializationContext);

        assertEquals (c.a, c2.a);
    }

    @Test
    public void singlePrivateWithConstructor() throws NotSerializableException {
        TestDescriptorBasedSerializerRegistry registry = new TestDescriptorBasedSerializerRegistry();
        SerializerFactory factory = testDefaultFactory(registry);

        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        C c = new C("dripping taps");
        C c2 = des.deserialize(ser.serialize(c, TestSerializationContext.testSerializationContext), C.class, TestSerializationContext.testSerializationContext);

        assertEquals (c.a, c2.a);

        assertEquals(1, registry.getContents().size());
    }

    @Test
    public void singlePrivateWithConstructorAndGetter()
            throws NotSerializableException {
        TestDescriptorBasedSerializerRegistry registry = new TestDescriptorBasedSerializerRegistry();
        SerializerFactory factory = testDefaultFactory(registry);

        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        C2 c = new C2("dripping taps");
        C2 c2 = des.deserialize(ser.serialize(c, TestSerializationContext.testSerializationContext), C2.class, TestSerializationContext.testSerializationContext);

        assertEquals (c.a, c2.a);

        assertEquals(1, registry.getContents().size());
    }
}
