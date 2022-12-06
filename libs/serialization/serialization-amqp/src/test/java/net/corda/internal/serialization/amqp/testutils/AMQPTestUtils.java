package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.amqp.SerializerFactory;
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder;
import net.corda.internal.serialization.amqp.helper.TestSerializationContext;
import net.corda.sandbox.SandboxGroup;

import java.util.Objects;

public class AMQPTestUtils {

    public static SerializerFactory testDefaultFactory() {
        return SerializerFactoryBuilder.build(
                (SandboxGroup) Objects.requireNonNull(TestSerializationContext.testSerializationContext.getSandboxGroup())
        );
    }
}
