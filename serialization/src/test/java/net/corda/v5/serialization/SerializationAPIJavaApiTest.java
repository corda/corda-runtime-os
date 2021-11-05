package net.corda.v5.serialization;

import net.corda.v5.base.types.ByteSequence;
import net.corda.v5.base.types.OpaqueBytesSubSequence;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SerializationAPIJavaApiTest {

    private final SerializationContext serializationContext = mock(SerializationContext.class);
    private final SerializationEncoding serializationEncoding = mock(SerializationEncoding.class);
    private final ClassLoader classLoader = mock(ClassLoader.class);
    private final ClassWhitelist classWhitelist = mock(ClassWhitelist.class);
    private final EncodingWhitelist encodingWhitelist = mock(EncodingWhitelist.class);
    private final MySerializationFactory serializationFactory = new MySerializationFactory();
    private final BaseProxyTestClass baseProxyTestClass = new BaseProxyTestClass();
    private final BaseTestClass<String> obj = new BaseTestClass<>();
    private final ProxyTestClass proxy = new ProxyTestClass();
    private final byte[] bytesArr = {101, 111, 110};
    private final int offset = 0;
    private final int size = bytesArr.length;
    private final OpaqueBytesSubSequence opaqueBytesSubSequence = new OpaqueBytesSubSequence(bytesArr, offset, size);
    private final ObjectWithCompatibleContext<String> objectWithCompatibleContext = new ObjectWithCompatibleContext<>("testObj", serializationContext);
    private final SerializedBytes<String> serializedBytes = new SerializedBytes<>(bytesArr);

    @Nested
    public class ObjectWithCompatibleContextJavaApiTest {

        @Test
        public void getObj() {
            var result = objectWithCompatibleContext.getObj();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo("testObj");
        }

        @Test
        public void getContext() {
            var result = objectWithCompatibleContext.getContext();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }
    }

    @Nested
    public class SerializationFactoryJavaApiTest {

        @Test
        public void deserialize() {
            var result = serializationFactory.deserialize(opaqueBytesSubSequence, ProxyTestClass.class, serializationContext);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(proxy);
        }

        @Test
        public void deserializeWithCompatibleContext() {
            var result = serializationFactory.deserializeWithCompatibleContext(opaqueBytesSubSequence, String.class, serializationContext);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(objectWithCompatibleContext);
        }

        @Test
        public void serialize() {
            var result = serializationFactory.serialize("testObj", serializationContext);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializedBytes);
        }

        @Test
        public void withCurrentContext() {
            var result = serializationFactory.withCurrentContext(serializationContext, () -> 1);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(1);
        }

        @Test
        public void asCurrent() {
            var result = serializationFactory.asCurrent(serializationFactory1 -> 1);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    public class SerializationContextJavaApiTest {

        @Test
        public void getTypeNames() {
            when(serializationContext.getPreferredSerializationVersion()).thenReturn(opaqueBytesSubSequence);
            var result = serializationContext.getPreferredSerializationVersion();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(opaqueBytesSubSequence);
        }

        @Test
        public void getEncoding() {
            when(serializationContext.getEncoding()).thenReturn(serializationEncoding);
            var result = serializationContext.getEncoding();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationEncoding);
        }

        @Test
        public void getDeserializationClassLoader() {
            when(serializationContext.getDeserializationClassLoader()).thenReturn(classLoader);
            var result = serializationContext.getDeserializationClassLoader();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(classLoader);
        }

        @Test
        public void getWhitelist() {
            when(serializationContext.getWhitelist()).thenReturn(classWhitelist);
            var result = serializationContext.getWhitelist();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(classWhitelist);
        }

        @Test
        public void getEncodingWhitelist() {
            when(serializationContext.getEncodingWhitelist()).thenReturn(encodingWhitelist);
            var result = serializationContext.getEncodingWhitelist();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(encodingWhitelist);
        }

        @Test
        public void getProperties() {
            Map<Object, Object> testMap = Map.of("key", "value");
            when(serializationContext.getProperties()).thenReturn(testMap);
            var result = serializationContext.getProperties();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(testMap);
        }

        @Test
        public void getObjectReferencesEnabled() {
            when(serializationContext.getObjectReferencesEnabled()).thenReturn(true);
            var result = serializationContext.getObjectReferencesEnabled();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(true);
        }

        @Test
        public void getPreventDataLoss() {
            when(serializationContext.getPreventDataLoss()).thenReturn(false);
            var result = serializationContext.getPreventDataLoss();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(false);
        }

        @Test
        public void getUseCase() {
            when(serializationContext.getUseCase()).thenReturn(SerializationContext.UseCase.P2P);
            var result = serializationContext.getUseCase();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(SerializationContext.UseCase.P2P);
        }

        @Test
        public void getCustomSerializers() {
            when(serializationContext.getCustomSerializers()).thenReturn(Set.of(baseProxyTestClass));
            var result = serializationContext.getCustomSerializers();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(Set.of(baseProxyTestClass));
        }

        @Test
        public void getClassInfoService() {
            when(serializationContext.getClassInfoService()).thenReturn(proxy);
            var result = serializationContext.getClassInfoService();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(proxy);
        }

        @Test
        public void getSandboxGroup() {
            when(serializationContext.getSandboxGroup()).thenReturn(obj);
            var result = serializationContext.getSandboxGroup();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(obj);
        }

        @Test
        public void withClassInfoService() {
            when(serializationContext.withClassInfoService(obj)).thenReturn(serializationContext);
            var result = serializationContext.withClassInfoService(obj);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withSandboxGroup() {
            when(serializationContext.withSandboxGroup(obj)).thenReturn(serializationContext);
            var result = serializationContext.withSandboxGroup(obj);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withProperty() {
            when(serializationContext.withProperty(obj, proxy)).thenReturn(serializationContext);
            var result = serializationContext.withProperty(obj, proxy);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withoutReferences() {
            when(serializationContext.withoutReferences()).thenReturn(serializationContext);
            var result = serializationContext.withoutReferences();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withPreventDataLoss() {
            when(serializationContext.withPreventDataLoss()).thenReturn(serializationContext);
            var result = serializationContext.withPreventDataLoss();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withClassLoader() {
            when(serializationContext.withClassLoader(classLoader)).thenReturn(serializationContext);
            var result = serializationContext.withClassLoader(classLoader);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withWhitelisted() {
            when(serializationContext.withWhitelisted(String.class)).thenReturn(serializationContext);
            var result = serializationContext.withWhitelisted(String.class);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withCustomSerializers() {
            when(serializationContext.withCustomSerializers(Set.of(baseProxyTestClass))).thenReturn(serializationContext);
            var result = serializationContext.withCustomSerializers(Set.of(baseProxyTestClass));

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withPreferredSerializationVersion() {
            when(serializationContext.withPreferredSerializationVersion(opaqueBytesSubSequence)).thenReturn(serializationContext);
            var result = serializationContext.withPreferredSerializationVersion(opaqueBytesSubSequence);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withEncoding() {
            when(serializationContext.withEncoding(serializationEncoding)).thenReturn(serializationContext);
            var result = serializationContext.withEncoding(serializationEncoding);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }

        @Test
        public void withEncodingWhitelist() {
            when(serializationContext.withEncodingWhitelist(encodingWhitelist)).thenReturn(serializationContext);
            var result = serializationContext.withEncodingWhitelist(encodingWhitelist);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(serializationContext);
        }
    }

    @Nested
    public class SerializedBytesJavaApiTest {

        @Test
        public void getBytes() {
            var result = serializedBytes.getBytes();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(bytesArr);
        }

        @Test
        public void getSummary() {
            var result = serializedBytes.getSummary();

            Assertions.assertThat(result).isNotNull();
        }
    }

    @Nested
    public class ClassWhitelistJavaApiTest {

        @Test
        public void getSummary() {
            when(classWhitelist.hasListed(String.class)).thenReturn(true);
            var result = classWhitelist.hasListed(String.class);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(true);
        }
    }

    @Nested
    public class EncodingWhitelistJavaApiTest {

        @Test
        public void acceptEncoding() {
            when(encodingWhitelist.acceptEncoding(serializationEncoding)).thenReturn(true);
            var result = encodingWhitelist.acceptEncoding(serializationEncoding);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(true);

        }
    }

    class MySerializationFactory extends SerializationFactory {

        @NotNull
        @Override
        public <T> ObjectWithCompatibleContext<T> deserializeWithCompatibleContext(
                @NotNull ByteSequence byteSequence, @NotNull Class<T> clazz, @NotNull SerializationContext context
        ) {
            return (ObjectWithCompatibleContext<T>) objectWithCompatibleContext;
        }

        @NotNull
        @Override
        public <T> T deserialize(@NotNull ByteSequence byteSequence, @NotNull Class<T> clazz, @NotNull SerializationContext context) {
            return (T) proxy;
        }

        @Override
        public <T> SerializedBytes<T> serialize(@NotNull T obj, @NotNull SerializationContext context) {
            return (SerializedBytes<T>) serializedBytes;
        }
    }

    static class BaseTestClass<T> {

    }

    class BaseProxyTestClass implements SerializationCustomSerializer<BaseTestClass<?>, ProxyTestClass> {

        @Override
        public ProxyTestClass toProxy(BaseTestClass<?> baseTestClass) {
            return proxy;
        }

        @Override
        public BaseTestClass<?> fromProxy(ProxyTestClass proxyTestClass) {
            return obj;
        }
    }

    static class ProxyTestClass {

    }
}
