package net.corda.internal.serialization.amqp;

import kotlin.jvm.functions.Function1;
import net.corda.internal.serialization.amqp.helper.TestSerializationContext;
import net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt;
import net.corda.v5.serialization.SerializationCustomSerializer;
import org.jetbrains.annotations.NotNull;
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

        @NotNull
        public List<Integer> toProxy(@NotNull ClassThatNeedsCustomSerializer obj) {
            return asList(obj.getA(), obj.getB());
        }

        @NotNull
        public ClassThatNeedsCustomSerializer fromProxy(@NotNull List<Integer> proxy) {
            List<Integer> constructorInput = new ArrayList<>(2);
            constructorInput.add(proxy.get(0));
            constructorInput.add(proxy.get(1));
            return new ClassThatNeedsCustomSerializer(constructorInput);
        }
    }

    @Test
    public void serializeExample() {
        SerializerFactory factory = testDefaultFactory(new DefaultDescriptorBasedSerializerRegistry());
        SerializationOutput ser = new SerializationOutput(factory);

        List<Integer> l = new ArrayList<>(2);
        l.add(10);
        l.add(20);
        ClassThatNeedsCustomSerializer e = new ClassThatNeedsCustomSerializer(l);

        factory.registerExternal(new ExampleSerializer(), factory);

        assertThrows(NotSerializableException.class,
                () -> ser.serialize(e, TestSerializationContext.testSerializationContext));
    }
}
