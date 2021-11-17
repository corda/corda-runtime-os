package net.corda.internal.serialization.amqp;

import com.google.common.collect.ImmutableList;
import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class JavaNestedInheritenceTestsBase {
    class DummyState implements JavaTestContractState {
        @Override
        @NotNull
        public List<JavaTestParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Wrapper {
    private JavaTestContractState cs;

    Wrapper(JavaTestContractState cs) {
        this.cs = cs;
    }
}

class TemplateWrapper<T> {
    public T obj;
    TemplateWrapper(T obj) { this.obj = obj; }
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaNestedInheritenceTests extends JavaNestedInheritenceTestsBase {
    @Test
    public void serializeIt() {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);

        assertThatThrownBy(() -> ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt2() {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        assertThatThrownBy(() -> ser.serialize(new Wrapper (new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt3() {
        SerializerFactory factory1 = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory1);

        assertThatThrownBy(() -> ser.serialize(new TemplateWrapper<JavaTestContractState>(new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }
}
