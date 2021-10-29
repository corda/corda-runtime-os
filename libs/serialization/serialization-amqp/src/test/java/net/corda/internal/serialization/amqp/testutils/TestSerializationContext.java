package net.corda.internal.serialization.amqp.testutils;

import net.corda.internal.serialization.SerializationContext;
import net.corda.internal.serialization.AllWhitelist;
import net.corda.internal.serialization.SerializationContextImpl;
import net.corda.v5.base.types.ByteSequence;

import java.util.HashMap;
import java.util.Map;

public class TestSerializationContext {

    private static final Map<Object, Object> serializationProperties = new HashMap<>();

    public static SerializationContext testSerializationContext = new SerializationContextImpl(
        ByteSequence.of(new byte[] { 'c', 'o', 'r', 'd', 'a', (byte)0, (byte)0, (byte)1}),
        AllWhitelist.INSTANCE,
        serializationProperties,
        false,
        SerializationContext.UseCase.Testing,
        null);
}
