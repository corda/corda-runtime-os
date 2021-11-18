package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.v5.serialization.SerializationCustomSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(value = 30)
public class JavaCustomSerializerListProxyTests {
    public static class ExampleSerializer implements SerializationCustomSerializer<ClassThatNeedsCustomSerializer, List<Integer>> {

        public List<Integer> toProxy(ClassThatNeedsCustomSerializer obj) {
            return asList(obj.getA(), obj.getB());
        }

        public ClassThatNeedsCustomSerializer fromProxy(List<Integer> proxy) {
            List<Integer> constructorInput = new ArrayList<>(2);
            constructorInput.add(proxy.get(0));
            constructorInput.add(proxy.get(1));
            return new ClassThatNeedsCustomSerializer(constructorInput);
        }
    }

    @Test
    public void serializeExample() {
        SerializerFactory factory = testDefaultFactory();
        SerializationOutput ser = new SerializationOutput(factory);

        List<Integer> l = new ArrayList<>(2);
        l.add(10);
        l.add(20);
        ClassThatNeedsCustomSerializer e = new ClassThatNeedsCustomSerializer(l);

        factory.registerExternal(new ExampleSerializer(), factory);

        assertThrows(NotSerializableException.class,
                () -> ser.serialize(e, TestSerializationContext.getTestSerializationContext()));
    }
}
