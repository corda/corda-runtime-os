package net.corda.internal.serialization.amqp;

import net.corda.serialization.ClassWhitelist;
import net.corda.serialization.EncodingWhitelist;
import net.corda.serialization.ObjectWithCompatibleContext;
import net.corda.serialization.SerializationContext;
import net.corda.serialization.SerializationEncoding;
import net.corda.serialization.SerializationFactory;
import net.corda.v5.base.types.ByteSequence;
import net.corda.v5.base.types.OpaqueBytesSubSequence;
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
        public void getWhitelist() {
            when(serializationContext.getWhitelist()).thenReturn(classWhitelist);
            var result = serializationContext.getWhitelist();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(classWhitelist);
        }

        @Test
        public void getEncodingWhitelist() {
            when(serializationContext.getEncodingWhitelist()).thenReturn(encodingWhitelist);
            var result = serializationContext.getEncodingWhitelist();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(encodingWhitelist);
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
        public void getClassInfoService() {
            when(serializationContext.getClassInfoService()).thenReturn(proxy);
            var result = serializationContext.getClassInfoService();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(proxy);
        }

        @Test
        public void getSandboxGroup() {
            when(serializationContext.getSandboxGroup()).thenReturn(obj);
            var result = serializationContext.getSandboxGroup();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(obj);
        }

        @Test
        public void withClassInfoService() {
            when(serializationContext.withClassInfoService(obj)).thenReturn(serializationContext);
            var result = serializationContext.withClassInfoService(obj);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(serializationContext);
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
        public void withWhitelisted() {
            when(serializationContext.withWhitelisted(String.class)).thenReturn(serializationContext);
            var result = serializationContext.withWhitelisted(String.class);

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
        public void withEncodingWhitelist() {
            when(serializationContext.withEncodingWhitelist(encodingWhitelist)).thenReturn(serializationContext);
            var result = serializationContext.withEncodingWhitelist(encodingWhitelist);

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

        @Test
        public void getSummary() {
            var result = serializedBytes.getSummary();

            assertThat(result).isNotNull();
        }
    }

    @Nested
    public class ClassWhitelistJavaApiTest {

        @Test
        public void getSummary() {
            when(classWhitelist.hasListed(String.class)).thenReturn(true);
            var result = classWhitelist.hasListed(String.class);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(true);
        }
    }

    @Nested
    public class EncodingWhitelistJavaApiTest {

        @Test
        public void acceptEncoding() {
            when(encodingWhitelist.acceptEncoding(serializationEncoding)).thenReturn(true);
            var result = encodingWhitelist.acceptEncoding(serializationEncoding);

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
