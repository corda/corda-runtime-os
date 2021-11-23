package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.AllWhitelist;
import net.corda.internal.serialization.amqp.SerializerFactory;
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder;
import net.corda.sandbox.SandboxGroup;

public class AMQPTestUtils {

    public static SerializerFactory testDefaultFactory() {
        return SerializerFactoryBuilder.build(AllWhitelist.INSTANCE,
                (SandboxGroup) TestSerializationContext.getTestSerializationContext().getSandboxGroup());
    }
}
