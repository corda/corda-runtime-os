package net.corda.internal.serialization.amqp;

import net.corda.base.internal.ByteSequence;
import net.corda.base.internal.OpaqueBytesSubSequence;
import net.corda.internal.serialization.SerializedBytesImpl;
import net.corda.serialization.EncodingAllowList;
import net.corda.serialization.ObjectWithCompatibleContext;
import net.corda.serialization.SerializationContext;
import net.corda.serialization.SerializationEncoding;
import net.corda.serialization.SerializationFactory;
import net.corda.v5.serialization.SerializationCustomSerializer;
import net.corda.v5.serialization.SerializedBytes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SerializationAPIJavaApiTest {

    private final SerializationContext serializationContext = mock(SerializationContext.class);
    private final SerializationEncoding serializationEncoding = mock(SerializationEncoding.class);
    private final ClassLoader classLoader = mock(ClassLoader.class);
    private final EncodingAllowList encodingAllowList = mock(EncodingAllowList.class);
    private final MySerializationFactory serializationFactory = new MySerializationFactory();
    private final BaseProxyTestClass baseProxyTestClass = new BaseProxyTestClass();
    private final BaseTestClass<String> obj = new BaseTestClass<>();
    private final ProxyTestClass proxy = new ProxyTestClass();
    private final byte[] bytesArr = {101, 111, 110};
    private final int offset = 0;
    private final int size = bytesArr.length;
    private final OpaqueBytesSubSequence opaqueBytesSubSequence = new OpaqueBytesSubSequence(bytesArr, offset, size);
    private final ObjectWithCompatibleContext<String> objectWithCompatibleContext = new ObjectWithCompatibleContext<>("testObj", serializationContext);
    private final SerializedBytes<String> serializedBytes = new SerializedBytesImpl<>(bytesArr);

    @Nested
    public class SerializationFactoryJavaApiTest {

        @Test
        public void deserialize() {
            var result = serializationFactory.deserialize(opaqueBytesSubSequence, ProxyTestClass.class, serializationContext);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(proxy);
        }

        @Test
        public void deserializeWithCompatibleContext() {
            var result = serializationFactory.deserializeWithCompatibleContext(opaqueBytesSubSequence, String.class, serializationContext);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(objectWithCompatibleContext);
        }

        @Test
        public void serialize() {
            var result = serializationFactory.serialize("testObj", serializationContext);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializedBytes);
        }
    }

    @Nested
    public class SerializationContextJavaApiTest {

        @Test
        public void getTypeNames() {
            when(serializationContext.getPreferredSerializationVersion()).thenReturn(opaqueBytesSubSequence);
            var result = serializationContext.getPreferredSerializationVersion();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(opaqueBytesSubSequence);
        }

        @Test
        public void getEncoding() {
            when(serializationContext.getEncoding()).thenReturn(serializationEncoding);
            var result = serializationContext.getEncoding();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationEncoding);
        }

        @Test
        public void getEncodingAllowList() {
            when(serializationContext.getEncodingAllowList()).thenReturn(encodingAllowList);
            var result = serializationContext.getEncodingAllowList();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(encodingAllowList);
        }

        @Test
        public void getProperties() {
            Map<Object, Object> testMap = Map.of("key", "value");
            when(serializationContext.getProperties()).thenReturn(testMap);
            var result = serializationContext.getProperties();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(testMap);
        }

        @Test
        public void getObjectReferencesEnabled() {
            when(serializationContext.getObjectReferencesEnabled()).thenReturn(true);
            var result = serializationContext.getObjectReferencesEnabled();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(true);
        }

        @Test
        public void getPreventDataLoss() {
            when(serializationContext.getPreventDataLoss()).thenReturn(false);
            var result = serializationContext.getPreventDataLoss();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(false);
        }

        @Test
        public void getUseCase() {
            when(serializationContext.getUseCase()).thenReturn(SerializationContext.UseCase.P2P);
            var result = serializationContext.getUseCase();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(SerializationContext.UseCase.P2P);
        }

        @Test
        public void getCustomSerializers() {
            when(serializationContext.getCustomSerializers()).thenReturn(Set.of(baseProxyTestClass));
            var result = serializationContext.getCustomSerializers();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(Set.of(baseProxyTestClass));
        }

        @Test
        public void getSandboxGroup() {
            when(serializationContext.getSandboxGroup()).thenReturn(obj);
            var result = serializationContext.getSandboxGroup();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(obj);
        }

        @Test
        public void withSandboxGroup() {
            when(serializationContext.withSandboxGroup(obj)).thenReturn(serializationContext);
            var result = serializationContext.withSandboxGroup(obj);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withProperty() {
            when(serializationContext.withProperty(obj, proxy)).thenReturn(serializationContext);
            var result = serializationContext.withProperty(obj, proxy);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withoutReferences() {
            when(serializationContext.withoutReferences()).thenReturn(serializationContext);
            var result = serializationContext.withoutReferences();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withPreventDataLoss() {
            when(serializationContext.withPreventDataLoss()).thenReturn(serializationContext);
            var result = serializationContext.withPreventDataLoss();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withClassLoader() {
            when(serializationContext.withClassLoader(classLoader)).thenReturn(serializationContext);
            var result = serializationContext.withClassLoader(classLoader);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withCustomSerializers() {
            when(serializationContext.withCustomSerializers(Set.of(baseProxyTestClass))).thenReturn(serializationContext);
            var result = serializationContext.withCustomSerializers(Set.of(baseProxyTestClass));

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withPreferredSerializationVersion() {
            when(serializationContext.withPreferredSerializationVersion(opaqueBytesSubSequence)).thenReturn(serializationContext);
            var result = serializationContext.withPreferredSerializationVersion(opaqueBytesSubSequence);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withEncoding() {
            when(serializationContext.withEncoding(serializationEncoding)).thenReturn(serializationContext);
            var result = serializationContext.withEncoding(serializationEncoding);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withEncodingAllowList() {
            when(serializationContext.withEncodingAllowList(encodingAllowList)).thenReturn(serializationContext);
            var result = serializationContext.withEncodingAllowList(encodingAllowList);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
        }
    }

    @Nested
    public class SerializedBytesJavaApiTest {

        @Test
        public void getBytes() {
            var result = serializedBytes.getBytes();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(bytesArr);
        }
    }

    @Nested
    public class EncodingAllowListJavaApiTest {

        @Test
        public void acceptEncoding() {
            when(encodingAllowList.acceptEncoding(serializationEncoding)).thenReturn(true);
            var result = encodingAllowList.acceptEncoding(serializationEncoding);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(true);

        }
    }

    @SuppressWarnings("unchecked")
    class MySerializationFactory implements SerializationFactory {

        @Override
        @NotNull
        public <T> ObjectWithCompatibleContext<T> deserializeWithCompatibleContext(
                @NotNull ByteSequence byteSequence, @NotNull Class<T> clazz, @NotNull SerializationContext context
        ) {
            return (ObjectWithCompatibleContext<T>) objectWithCompatibleContext;
        }

        @Override
        @NotNull
        public <T> T deserialize(@NotNull ByteSequence byteSequence, @NotNull Class<T> clazz, @NotNull SerializationContext context) {
            return (T) proxy;
        }

        @Override
        @NotNull
        public <T> SerializedBytes<T> serialize(@NotNull T obj, @NotNull SerializationContext context) {
            return (SerializedBytes<T>) serializedBytes;
        }
    }

    static class BaseTestClass<T> {

    }

    class BaseProxyTestClass implements SerializationCustomSerializer<BaseTestClass<?>, ProxyTestClass> {

        @Override
        @NotNull
        public ProxyTestClass toProxy(@NotNull BaseTestClass<?> baseTestClass) {
            return proxy;
        }

        @Override
        @NotNull
        public BaseTestClass<?> fromProxy(@NotNull ProxyTestClass proxyTestClass) {
            return obj;
        }
    }

    static class ProxyTestClass {

    }
}
