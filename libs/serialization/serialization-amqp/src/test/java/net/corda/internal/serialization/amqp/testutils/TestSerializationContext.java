package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.AllWhitelist;
import net.corda.internal.serialization.SerializationContextImpl;
import net.corda.serialization.SerializationContext;

import java.util.HashMap;
import java.util.Map;

import static net.corda.internal.serialization.amqp.SchemaKt.amqpMagic;

public class TestSerializationContext {

    private static final Map<Object, Object> serializationProperties = new HashMap<>();

    public static SerializationContext testSerializationContext = new SerializationContextImpl(
        amqpMagic,
        AllWhitelist.INSTANCE,
        serializationProperties,
        false,
        SerializationContext.UseCase.Testing,
        null);
}
