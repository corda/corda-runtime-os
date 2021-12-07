package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.AMQPTestUtils;
import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.serialization.SerializationContext;
import net.corda.v5.serialization.SerializedBytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

class ForbiddenLambdaSerializationTests {

    private final EnumSet<SerializationContext.UseCase> contexts = EnumSet.complementOf(
            EnumSet.of(SerializationContext.UseCase.Testing)
    );

    private SerializerFactory factory;

    @BeforeEach
    void setup() {
        factory = AMQPTestUtils.testDefaultFactory();
    }

    @Test
    void serialization_fails_for_serializable_java_lambdas() {
        contexts.forEach(ctx -> {
            SerializationContext context = TestSerializationContext.testSerializationContext;
            String value = "Hey";
            Callable<String> target = (Callable<String> & Serializable) () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable)
                    .isNotNull()
                    .isInstanceOf(NotSerializableException.class)
                    .hasMessageContaining("Serializer does not support synthetic classes");
        });
    }

    @Test
    void serialization_fails_for_not_serializable_java_lambdas() {
        contexts.forEach(ctx -> {
            SerializationContext context = TestSerializationContext.testSerializationContext;
            String value = "Hey";
            Callable<String> target = () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable)
                    .isInstanceOf(NotSerializableException.class)
                    .hasMessageContaining("Serializer does not support synthetic classes");
        });
    }

    private <T> SerializedBytes<T> serialize(
            final T target,
            final SerializationContext context
    ) throws NotSerializableException {
        return new SerializationOutput(factory).serialize(target, context);
    }
}
