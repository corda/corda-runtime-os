package net.corda.v5.application.marshalling.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.mockito.Mockito.mock;

public class JsonWriterJavaApiTest {
    private final JsonWriter jw = mock(JsonWriter.class);

    private final static String TEST_STRING = "test";
    private final static char[] TEST_CHAR_ARRAY = new char[0];
    private final static BigDecimal TEST_BIG_DECIMAL = new BigDecimal("0");
    private final static BigInteger TEST_BIG_INTEGER = new BigInteger("0");
    private final static int TEST_INTEGER = 1;
    private final static double TEST_DOUBLE = 1.1d;
    private final static double TEST_FLOAT = 1.1f;
    private final static double TEST_LONG = 1L;
    private final static short TEST_SHORT = 1;
    private final static Object TEST_OBJECT = new Object();
    private final static int[] TEST_INT_ARRAY = new int[0];
    private final static long[] TEST_LONG_ARRAY = new long[0];
    private final static double[] TEST_DOUBLE_ARRAY = new double[0];
    private final static String[] TEST_STRING_ARRAY = new String[0];
    private final static byte[] TEST_BYTE_ARRAY = new byte[0];
    private final static InputStream TEST_INPUT_STREAM = new InputStream() {
        @Override
        public int read() throws IOException {
            return 0;
        }
    };
    private final static char TEST_CHAR = 'A';


    @Test
    void callEveryMethod() throws IOException {
        jw.writeStartObject();
        jw.writeEndObject();
        jw.writeFieldName(TEST_STRING);

        jw.writeString(TEST_CHAR_ARRAY, 1, 1);
        jw.writeString(TEST_STRING);
        jw.writeStringField(TEST_STRING, TEST_STRING);

        jw.writeNumber(TEST_BIG_DECIMAL);
        jw.writeNumber(TEST_BIG_INTEGER);
        jw.writeNumber(TEST_DOUBLE);
        jw.writeNumber(TEST_FLOAT);
        jw.writeNumber(TEST_INTEGER);
        jw.writeNumber(TEST_LONG);
        jw.writeNumber(TEST_SHORT);

        jw.writeNumberField(TEST_STRING, TEST_BIG_DECIMAL);
        jw.writeNumberField(TEST_STRING, TEST_DOUBLE);
        jw.writeNumberField(TEST_STRING, TEST_FLOAT);
        jw.writeNumberField(TEST_STRING, TEST_INTEGER);
        jw.writeNumberField(TEST_STRING, TEST_LONG);

        jw.writeObject(TEST_OBJECT);
        jw.writeObjectField(TEST_STRING, TEST_OBJECT);
        jw.writeObjectFieldStart(TEST_STRING);

        jw.writeBoolean(true);
        jw.writeBooleanField(TEST_STRING, false);

        jw.writeArrayFieldStart(TEST_STRING);
        jw.writeStartArray();
        jw.writeEndArray();
        jw.writeArray(TEST_INT_ARRAY, 1, 1);
        jw.writeArray(TEST_LONG_ARRAY, 1, 1);
        jw.writeArray(TEST_DOUBLE_ARRAY, 1, 1);
        jw.writeArray(TEST_STRING_ARRAY, 1, 1);

        jw.writeBinary(JsonSerializedBase64Config.MIME, TEST_BYTE_ARRAY, 1, 1);
        jw.writeBinary(JsonSerializedBase64Config.MIME_NO_LINEFEEDS, TEST_INPUT_STREAM, 1);
        jw.writeBinary(TEST_BYTE_ARRAY);
        jw.writeBinary(TEST_BYTE_ARRAY, 1, 1);
        jw.writeBinary(TEST_INPUT_STREAM, 1);
        jw.writeBinaryField(TEST_STRING, TEST_BYTE_ARRAY);

        jw.writeNull();
        jw.writeNullField(TEST_STRING);

        jw.writeRaw(TEST_CHAR);
        jw.writeRaw(TEST_CHAR_ARRAY, 1, 1);
        jw.writeRaw(TEST_STRING);
        jw.writeRaw(TEST_STRING, 1, 1);
        jw.writeRawValue(TEST_CHAR_ARRAY, 1, 1);
        jw.writeRawValue(TEST_STRING);
        jw.writeRawValue(TEST_STRING, 1, 1);
    }
}
