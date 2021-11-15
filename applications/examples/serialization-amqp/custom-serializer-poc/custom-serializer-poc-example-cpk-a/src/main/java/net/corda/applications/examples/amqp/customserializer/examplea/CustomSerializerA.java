package net.corda.applications.examples.amqp.customserializer.examplea;

import net.corda.v5.serialization.SerializationCustomSerializer;
import org.slf4j.Logger;

public class CustomSerializerA implements SerializationCustomSerializer<NeedsCustomSerializerExampleA, Integer> {

    private final String name;
    private final Logger logger;

    public CustomSerializerA() {
        name = "";
        logger = null;
    }

    public CustomSerializerA(String name, Logger logger) {
        this.name = name;
        this.logger = logger;
    }

    @Override
    public NeedsCustomSerializerExampleA fromProxy(Integer integer) {

        if (logger != null) {
            logger.info("fromProxy - " + name);
        }

        return new NeedsCustomSerializerExampleA(integer);
    }

    @Override
    public Integer toProxy(NeedsCustomSerializerExampleA needsCustomSerializerExampleA) {

        if (logger != null) {
            logger.info("toProxy - " + name);
        }

        return needsCustomSerializerExampleA.getB();
    }
}

