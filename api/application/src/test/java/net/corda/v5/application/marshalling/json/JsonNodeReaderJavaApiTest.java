package net.corda.v5.application.marshalling.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class JsonNodeReaderJavaApiTest {

    private final JsonNodeReader jnr = mock(JsonNodeReader.class);

    private static class TestClass {
    }

    private final static String TEST_STRING = "test";
    private final static Number TEST_NUMBER = new Number() {
        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0;
        }

        @Override
        public float floatValue() {
            return 0;
        }

        @Override
        public double doubleValue() {
            return 0;
        }
    };

    @Test
    void callEveryMethod() {
        JsonNodeReaderType type = jnr.getType();

        boolean isObject = jnr.isObject();
        Iterator<Map.Entry<String, JsonNodeReader>> fieldIt = jnr.fields();
        boolean hasField = jnr.hasField(TEST_STRING);
        JsonNodeReader getField = jnr.getField(TEST_STRING);

        boolean isArray = jnr.isArray();
        Iterator<JsonNodeReader> arrayIt = jnr.asArray();

        boolean isBoolean = jnr.isBoolean();
        boolean asBoolean = jnr.asBoolean();
        boolean asBooleanDefault = jnr.asBoolean(true);

        boolean isNumber = jnr.isNumber();
        Number asNumber = jnr.numberValue();

        boolean isFloatingPointNumber = jnr.isFloatingPointNumber();

        boolean isDouble = jnr.isDouble();
        double doubleValue = jnr.doubleValue();
        double asDouble = jnr.asDouble();
        double asDoubleDefault = jnr.asDouble(1.1);

        float floatValue = jnr.floatValue();

        boolean isInt = jnr.isInt();
        boolean canConvertToInt = jnr.canConvertToInt();
        int asInt = jnr.asInt();
        int asIntDefault = jnr.asInt(0);

        boolean canConvertToLong = jnr.canConvertToLong();
        long asLong = jnr.asLong();
        long asLongDefault = jnr.asLong(0L);

        short shortValue = jnr.shortValue();

        BigInteger bigInteger = jnr.bigIntegerValue();
        BigDecimal bigDecimal = jnr.bigDecimalValue();

        boolean isText = jnr.isText();
        String asTest = jnr.asText();
        String asTestDefault = jnr.asText(TEST_STRING);

        byte[] binaryValue = jnr.binaryValue();

        boolean isNull = jnr.isNull();

        TestClass parsedObject = jnr.parse(TestClass.class);
    }
}
