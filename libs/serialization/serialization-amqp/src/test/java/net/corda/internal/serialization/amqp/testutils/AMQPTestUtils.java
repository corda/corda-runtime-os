package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.AllWhitelist;
import net.corda.internal.serialization.amqp.SerializerFactory;
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder;

public class AMQPTestUtils {

    public static SerializerFactory testDefaultFactory() {
        return SerializerFactoryBuilder.build(AllWhitelist.INSTANCE);
    }
}
