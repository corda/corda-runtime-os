package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.SerializedBytesImpl;
import net.corda.internal.serialization.SerializedBytesImplKt;
import net.corda.internal.serialization.amqp.SerializerFactory;
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder;
import net.corda.internal.serialization.amqp.helper.TestSerializationContext;
import net.corda.sandbox.SandboxGroup;
import net.corda.v5.serialization.SerializedBytes;

import java.util.Objects;

public class AMQPTestUtils {

    public static SerializerFactory testDefaultFactory() {
        return SerializerFactoryBuilder.build(
                (SandboxGroup) Objects.requireNonNull(TestSerializationContext.testSerializationContext.getSandboxGroup()));
    }

    public static <T> SerializedBytesImpl<T> unwrapSerializedBytes(SerializedBytes<T> bytes) {
        return SerializedBytesImplKt.unwrap(bytes);
    }
}
