package net.corda.kotlin;

import net.corda.kotlin.reflect.types.MemberSignature;
import net.corda.kotlin.reflect.types.TypeExtensions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toMap;
import static net.corda.kotlin.reflect.types.TypeExtensions.toSignature;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

@Timeout(value = 5, unit = MINUTES)
class JavaMemberSignatureTest {
    static class ApiProvider implements ArgumentsProvider {
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Arrays.stream(ExampleApi.class.getMethods()).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @DisplayName("Java Interface Method: {0}")
    @ArgumentsSource(ApiProvider.class)
    void testInterfaceForClass(Method apiMethod) throws NoSuchMethodException {
        MemberSignature api = toSignature(apiMethod);
        MemberSignature impl = toSignature(
            ExampleImpl.class.getMethod(apiMethod.getName(), apiMethod.getParameterTypes())
        );
        assertAll(apiMethod.getName(),
            () -> assertTrue(api.isAssignableFrom(impl), "API not assignable from Impl"),
            () -> assertTrue(!impl.isAssignableFrom(api) || impl.equals(api),
                    "Impl incorrectly assignable from API")
        );
    }

    @SuppressWarnings("unused")
    interface ExampleApi {
        Object getData(Object obj);
        Iterable<String> getValues();
        void setValues(Iterable<String> values);
    }

    static class ExampleImpl implements ExampleApi {
        private final List<String> values = new ArrayList<>();

        @Override
        public String getData(@NotNull Object obj) {
            return obj.toString();
        }

        @Override
        public List<String> getValues() {
            return values;
        }

        @Override
        public void setValues(@NotNull Iterable<String> values) {
            this.values.clear();
            values.forEach(this.values::add);
        }
    }

    @Test
    void testPrimitiveTypes() {
        Map<String, MemberSignature> signatures = Arrays.stream(DataTypes.class.getMethods())
            .map(TypeExtensions::toSignature)
            .collect(toMap(MemberSignature::getName, Function.identity()));
        assertAll("Primitive Types",
            () -> assertEquals(byte.class, signatures.get("getByte").getReturnType(), "byte"),
            () -> assertEquals(short.class, signatures.get("getShort").getReturnType(), "short"),
            () -> assertEquals(int.class, signatures.get("getInt").getReturnType(), "int"),
            () -> assertEquals(long.class, signatures.get("getLong").getReturnType(), "long"),
            () -> assertEquals(char.class, signatures.get("getChar").getReturnType(), "char"),
            () -> assertEquals(boolean.class, signatures.get("getBoolean").getReturnType(), "boolean"),
            () -> assertEquals(float.class, signatures.get("getFloat").getReturnType(), "float"),
            () -> assertEquals(double.class, signatures.get("getDouble").getReturnType(), "double"),

            () -> assertArrayEquals(arrayOf(byte[][].class), signatures.get("setBytes").getParameterTypes(), "byte"),
            () -> assertArrayEquals(arrayOf(short[][].class), signatures.get("setShorts").getParameterTypes(), "short"),
            () -> assertArrayEquals(arrayOf(int[].class), signatures.get("setInts").getParameterTypes(), "int"),
            () -> assertArrayEquals(arrayOf(long[][][].class), signatures.get("setLongs").getParameterTypes(), "long"),
            () -> assertArrayEquals(arrayOf(char[][].class), signatures.get("setChars").getParameterTypes(), "char"),
            () -> assertArrayEquals(arrayOf(boolean[].class), signatures.get("setBooleans").getParameterTypes(), "boolean"),
            () -> assertArrayEquals(arrayOf(float[][].class), signatures.get("setFloats").getParameterTypes(), "float"),
            () -> assertArrayEquals(arrayOf(double[][].class), signatures.get("setDoubles").getParameterTypes(), "double")
        );
    }

    @SuppressWarnings("unused")
    interface DataTypes {
        byte getByte();
        short getShort();
        int getInt();
        long getLong();
        char getChar();
        boolean getBoolean();
        float getFloat();
        double getDouble();

        void setBytes(byte[][] bytes);
        void setShorts(short[][] shorts);
        void setInts(int[] ints);
        void setLongs(long[][][] longs);
        void setChars(char[][] chars);
        void setBooleans(boolean[] booleans);
        void setFloats(float[][] floats);
        void setDoubles(double[][] doubles);
    }

    @NotNull
    private static Class<?>[] arrayOf(Class<?> clazz) {
        return new Class<?>[] { clazz };
    }
}
