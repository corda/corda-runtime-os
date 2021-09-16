package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.v5.serialization.SerializationCustomSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.List;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;

@Timeout(value = 30)
public class JavaCustomSerializerTests {

    /**
     * This is the class that will Proxy instances of ClassThatNeedsCustomSerializer within the serializer
     */
    public static class ExampleProxy {
        /**
         * These properties will be serialized into the byte stream, this is where we choose how to
         * represent instances of the object we're proxying. In this example, which is somewhat
         * contrived, this choice is obvious. In your own classes / 3rd party libraries, however, this
         * may require more thought.
         */
        private Integer proxiedA;
        private Integer proxiedB;

        /**
         * The proxu class itself must be serializable by the framework, it must thus have a constructor that
         * can be mapped to the properties of the class via getter methods.
         */
        public Integer getProxiedA() { return proxiedA; }
        public Integer getProxiedB() { return  proxiedB; }


        public ExampleProxy(Integer proxiedA, Integer proxiedB) {
            this.proxiedA = proxiedA;
            this.proxiedB = proxiedB;
        }
    }

    /**
     * Finally this is the custom serializer that will automatically loaded into the serialization
     * framework when the CorDapp Jar is scanned at runtime.
     */
    public static class ExampleSerializer implements SerializationCustomSerializer<ClassThatNeedsCustomSerializer, ExampleProxy> {

        /**
         *  Given an instance of the Example class, create an instance of the proxying object ExampleProxy.
         *
         *  Essentially convert ClassThatNeedsCustomSerializer -> ExampleProxy
         */
        public ExampleProxy toProxy(ClassThatNeedsCustomSerializer obj) {
            return new ExampleProxy(obj.getA(), obj.getB());
        }

        /**
         * Conversely, given an instance of the proxy object, revert that back to an instance of the
         * type being proxied.
         *
         *  Essentially convert ExampleProxy -> Example
         *
         */
        public ClassThatNeedsCustomSerializer fromProxy(ExampleProxy proxy) {
            List<Integer> l = new ArrayList<Integer>(2);
            l.add(proxy.getProxiedA());
            l.add(proxy.getProxiedB());
            return new ClassThatNeedsCustomSerializer(l);
        }

    }

    @Test
    public void serializeExample() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory);

        List<Integer> l = new ArrayList<Integer>(2);
        l.add(10);
        l.add(20);
        ClassThatNeedsCustomSerializer e = new ClassThatNeedsCustomSerializer(l);

        CorDappCustomSerializer ccs = new CorDappCustomSerializer(new ExampleSerializer(), factory);
        factory.registerExternal(ccs);

        var serializedBytes = ser.serialize(e, TestSerializationContext.testSerializationContext);
        var deserialize = new DeserializationInput(factory).deserialize(serializedBytes, ClassThatNeedsCustomSerializer.class, TestSerializationContext.testSerializationContext);

        Assertions.assertEquals(10, deserialize.getA());
        Assertions.assertEquals(20, deserialize.getB());

    }
}
