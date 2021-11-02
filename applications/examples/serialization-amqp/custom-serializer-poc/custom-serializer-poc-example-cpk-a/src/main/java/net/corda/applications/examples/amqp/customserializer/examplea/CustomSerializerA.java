package net.corda.applications.examples.amqp.customserializer.examplea;

import net.corda.v5.serialization.SerializationCustomSerializer;

public class CustomSerializerA implements SerializationCustomSerializer<NeedsCustomSerializerExampleA, Integer> {
    @Override
    public NeedsCustomSerializerExampleA fromProxy(Integer integer) {
        return new NeedsCustomSerializerExampleA(integer);
    }

    @Override
    public Integer toProxy(NeedsCustomSerializerExampleA needsCustomSerializerExampleA) {
        return needsCustomSerializerExampleA.getB();
    }
}

