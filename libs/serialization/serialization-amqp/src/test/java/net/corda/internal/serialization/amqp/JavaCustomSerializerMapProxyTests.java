package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.v5.serialization.SerializationCustomSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(value = 30)
public class JavaCustomSerializerMapProxyTests {
    public static class ExampleSerializer implements SerializationCustomSerializer<ClassThatNeedsCustomSerializer, Map<String, Integer>> {

        public Map<String, Integer> toProxy(ClassThatNeedsCustomSerializer obj) {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("a", obj.getA());
            map.put("b", obj.getB());
            return map;
        }

        public ClassThatNeedsCustomSerializer fromProxy(Map<String, Integer> proxy) {
            List<Integer> constructorInput = new ArrayList<>(2);
            constructorInput.add(proxy.get("a"));
            constructorInput.add(proxy.get("b"));
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
