package net.corda.customserializer.a;

import net.corda.v5.serialization.SerializationCustomSerializer;
import org.slf4j.Logger;

public class CustomSerializerA implements SerializationCustomSerializer<NeedsCustomSerializerExampleA, CustomSerializerA.MyProxy> {

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
    public NeedsCustomSerializerExampleA fromProxy(MyProxy proxy) {

        if (logger != null) {
            logger.info("fromProxy - {}", name);
        }

        return new NeedsCustomSerializerExampleA(proxy.getInteger());
    }

    @Override
    public MyProxy toProxy(NeedsCustomSerializerExampleA needsCustomSerializerExampleA) {

        if (logger != null) {
            logger.info("toProxy - {}", name);
        }

        return new MyProxy(needsCustomSerializerExampleA.getB());
    }

    public static class MyProxy {
        private final int integer;

        MyProxy(int integer) {
            this.integer = integer;
        }

        int getInteger() {
            return integer;
        }
    }
}

