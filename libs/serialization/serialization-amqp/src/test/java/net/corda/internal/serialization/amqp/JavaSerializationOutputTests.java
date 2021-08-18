package net.corda.internal.serialization.amqp;

import net.corda.v5.serialization.SerializedBytes;
import net.corda.v5.serialization.annotations.ConstructorForDeserialization;
import net.corda.internal.serialization.amqp.Choice;
import net.corda.internal.serialization.amqp.CompositeType;
import net.corda.internal.serialization.amqp.Descriptor;
import net.corda.internal.serialization.amqp.DeserializationInput;
import net.corda.internal.serialization.amqp.Envelope;
import net.corda.internal.serialization.amqp.Field;
import net.corda.internal.serialization.amqp.Metadata;
import net.corda.internal.serialization.amqp.RestrictedType;
import net.corda.internal.serialization.amqp.Schema;
import net.corda.internal.serialization.amqp.SerializationOutput;
import net.corda.internal.serialization.amqp.SerializerFactory;
import net.corda.internal.serialization.amqp.Transform;
import net.corda.internal.serialization.amqp.TransformsSchema;
import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nonnull;
import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaSerializationOutputTests {

    static class Foo {
        private final String bob;
        private final int count;

        public Foo(String msg, long count) {
            this.bob = msg;
            this.count = (int) count;
        }

        @ConstructorForDeserialization
        private Foo(String fred, int count) {
            this.bob = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return bob;
        }

        @SuppressWarnings("unused")
        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Foo foo = (Foo) o;

            if (count != foo.count) return false;
            return bob != null ? bob.equals(foo.bob) : foo.bob == null;
        }

        @Override
        public int hashCode() {
            int result = bob != null ? bob.hashCode() : 0;
            result = 31 * result + count;
            return result;
        }
    }

    static class UnAnnotatedFoo {
        private final String bob;
        private final int count;

        private UnAnnotatedFoo(String fred, int count) {
            this.bob = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return bob;
        }

        @SuppressWarnings("unused")
        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UnAnnotatedFoo foo = (UnAnnotatedFoo) o;

            if (count != foo.count) return false;
            return bob != null ? bob.equals(foo.bob) : foo.bob == null;
        }

        @Override
        public int hashCode() {
            int result = bob != null ? bob.hashCode() : 0;
            result = 31 * result + count;
            return result;
        }
    }

    static class BoxedFoo {
        private final String fred;
        private final Integer count;

        private BoxedFoo(String fred, Integer count) {
            this.fred = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return fred;
        }

        @SuppressWarnings("unused")
        public Integer getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BoxedFoo boxedFoo = (BoxedFoo) o;

            if (fred != null ? !fred.equals(boxedFoo.fred) : boxedFoo.fred != null) return false;
            return count != null ? count.equals(boxedFoo.count) : boxedFoo.count == null;
        }

        @Override
        public int hashCode() {
            int result = fred != null ? fred.hashCode() : 0;
            result = 31 * result + (count != null ? count.hashCode() : 0);
            return result;
        }
    }


    static class BoxedFooNotNull {
        private final String fred;
        private final Integer count;

        private BoxedFooNotNull(String fred, Integer count) {
            this.fred = fred;
            this.count = count;
        }

        public String getFred() {
            return fred;
        }

        @Nonnull
        public Integer getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BoxedFooNotNull boxedFoo = (BoxedFooNotNull) o;

            if (fred != null ? !fred.equals(boxedFoo.fred) : boxedFoo.fred != null) return false;
            return count != null ? count.equals(boxedFoo.count) : boxedFoo.count == null;
        }

        @Override
        public int hashCode() {
            int result = fred != null ? fred.hashCode() : 0;
            result = 31 * result + (count != null ? count.hashCode() : 0);
            return result;
        }
    }

    private Object serdes(Object obj) throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();
        SerializerFactory factory2 = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory1);
        SerializedBytes<Object> bytes = ser.serialize(obj, TestSerializationContext.testSerializationContext);

        DecoderImpl decoder = new DecoderImpl();

        decoder.register(Envelope.DESCRIPTOR, Envelope.Companion);
        decoder.register(Schema.DESCRIPTOR, Schema.Companion);
        decoder.register(Descriptor.DESCRIPTOR, Descriptor.Companion);
        decoder.register(Field.DESCRIPTOR, Field.Companion);
        decoder.register(CompositeType.DESCRIPTOR, CompositeType.Companion);
        decoder.register(Choice.DESCRIPTOR, Choice.Companion);
        decoder.register(RestrictedType.DESCRIPTOR, RestrictedType.Companion);
        decoder.register(Transform.DESCRIPTOR, Transform.Companion);
        decoder.register(TransformsSchema.DESCRIPTOR, TransformsSchema.Companion);
        decoder.register(Metadata.DESCRIPTOR, Metadata.Companion);

        new EncoderImpl(decoder);
        decoder.setByteBuffer(ByteBuffer.wrap(bytes.getBytes(), 8, bytes.getSize() - 8));
        Envelope result = (Envelope) decoder.readObject();
        assertNotNull(result);

        DeserializationInput des = new DeserializationInput(factory2);
        Object desObj = des.deserialize(bytes, Object.class, TestSerializationContext.testSerializationContext);
        assertTrue(Objects.deepEquals(obj, desObj));

        // Now repeat with a re-used factory
        SerializationOutput ser2 = new SerializationOutput(factory1);
        DeserializationInput des2 = new DeserializationInput(factory1);
        Object desObj2 = des2.deserialize(ser2.serialize(obj, TestSerializationContext.testSerializationContext),
                Object.class, TestSerializationContext.testSerializationContext);

        assertTrue(Objects.deepEquals(obj, desObj2));
        // TODO: check schema is as expected
        return desObj2;
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Foo obj = new Foo("Hello World!", 123);
        serdes(obj);
    }

    @Test
    public void testJavaConstructorWithoutAnnotations() throws NotSerializableException {
        UnAnnotatedFoo obj = new UnAnnotatedFoo("Hello World!", 123);
        serdes(obj);
    }


    @Test
    public void testBoxedTypes() throws NotSerializableException {
        BoxedFoo obj = new BoxedFoo("Hello World!", 123);
        serdes(obj);
    }

    @Test
    public void testBoxedTypesNotNull() throws NotSerializableException {
        BoxedFooNotNull obj = new BoxedFooNotNull("Hello World!", 123);
        serdes(obj);
    }
}
