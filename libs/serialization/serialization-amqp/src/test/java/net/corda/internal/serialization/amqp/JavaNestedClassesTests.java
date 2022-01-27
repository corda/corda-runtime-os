package net.corda.internal.serialization.amqp;

import com.google.common.collect.ImmutableList;
import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.serialization.SerializedBytes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


abstract class JavaTestParty {

}

@CordaSerializable
interface JavaTestContractState {
    List<JavaTestParty> getParticipants();
}

class OuterClass1 {
    protected SerializationOutput ser;
    DeserializationInput desExisting;
    DeserializationInput desRegen;


    OuterClass1() {
        SerializerFactory factory1 = testDefaultFactory();

        SerializerFactory factory2 = testDefaultFactory();

        this.ser = new SerializationOutput(factory1);
        this.desExisting = new DeserializationInput(factory1);
        this.desRegen = new DeserializationInput(factory2);
    }

    class DummyState implements JavaTestContractState {
        @Override
        @NotNull
        public List<JavaTestParty> getParticipants() {
            return ImmutableList.of();
        }
    }

    public void run() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class Inherator1 extends OuterClass1 {
    public void iRun() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class OuterClass2 {
    protected SerializationOutput ser;
    DeserializationInput desExisting;
    DeserializationInput desRegen;

    OuterClass2() {
        SerializerFactory factory1 = testDefaultFactory();

        SerializerFactory factory2 = testDefaultFactory();

        this.ser = new SerializationOutput(factory1);
        this.desExisting = new DeserializationInput(factory1);
        this.desRegen = new DeserializationInput(factory2);
    }

    protected class DummyState implements JavaTestContractState {
        private Integer count;

        DummyState(Integer count) {
            this.count = count;
        }

        @Override
        @NotNull
        public List<JavaTestParty> getParticipants() {
            return ImmutableList.of();
        }
    }

    public void run() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(12), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class Inherator2 extends OuterClass2 {
    public void iRun() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(12), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

// Make the base class abstract
abstract class AbstractClass2 {
    protected SerializationOutput ser;

    AbstractClass2() {
        SerializerFactory factory = testDefaultFactory();

        this.ser = new SerializationOutput(factory);
    }

    protected class DummyState implements JavaTestContractState {
        @Override
        @NotNull
        public List<JavaTestParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Inherator4 extends AbstractClass2 {
    public void run() throws NotSerializableException {
        ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
    }
}

abstract class AbstractClass3 {
    protected class DummyState implements JavaTestContractState {
        @Override
        @NotNull
        public List<JavaTestParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Inherator5 extends AbstractClass3 {
    public void run() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
    }
}

class Inherator6 extends AbstractClass3 {
    public class Wrapper {
        //@Suppress("UnusedDeclaration"])
        private JavaTestContractState cState;

        Wrapper(JavaTestContractState cState) {
            this.cState = cState;
        }
    }

    public void run() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        ser.serialize(new Wrapper(new DummyState()), TestSerializationContext.testSerializationContext);
    }
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaNestedClassesTests {
    @Test
    public void publicNested() {
        assertThatThrownBy(() -> new OuterClass1().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void privateNested() {
        assertThatThrownBy(() -> new OuterClass2().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void publicNestedInherited() {
        assertThatThrownBy(() -> new Inherator1().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");

        assertThatThrownBy(() -> new Inherator1().iRun()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void protectedNestedInherited() {
        assertThatThrownBy(() -> new Inherator2().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");

        assertThatThrownBy(() -> new Inherator2().iRun()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNested() {
        assertThatThrownBy(() -> new Inherator4().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNestedFactoryOnNested() {
        assertThatThrownBy(() -> new Inherator5().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNestedFactoryOnNestedInWrapper() {
        assertThatThrownBy(() -> new Inherator6().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }
}

