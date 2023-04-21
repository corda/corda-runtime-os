package net.corda.v5.application.marshalling.json;

import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * An interface to a creator of Json. Part of the support to add custom serializer support to the
 * {@link JsonMarshallingService}. Custom serializers are passed a {@link JsonWriter} in order they can translate their object
 * types to Json.
 * <p>
 * When writing arrays and objects, methods which denote the start and end are provided, which should enclose calls to
 * write any content within those arrays or objects. The root of the Json being serialized should always start with a
 * call to {@link #writeStartObject} and end with a call to {@link #writeEndObject}. Arrays of course can only contain values, not
 * fields. Objects must contain fields and values. Separators between objects, arrays, and array items are automatically
 * added.
 * <p>
 * When fields and values are written with either two method calls or the single methods which take a field name and
 * value parameter, the separators and enclosing double quotation marks are added automatically. The {@link JsonWriter} will
 * throw if you attempt to write values without field names, the exception being the {@link #writeRaw} methods which dump
 * whatever you pass directly into the Json output unchanged. To write raw content with validation instead call one of
 * the {@link #writeRawValue} methods, which will assume whatever is passed is a valid Json value.
 */
@DoNotImplement
public interface JsonWriter {
    /**
     * Writes the marker denoting the start of an object.
     *
     * @throws IOException if there is a writing error.
     */
    void writeStartObject() throws IOException;

    /**
     * Writes the marker denoting the end of an object.
     *
     * @throws IOException if there is a writing error.
     */
    void writeEndObject() throws IOException;

    /**
     * Writes a field name surrounded by double quotes.
     *
     * @param fieldName The name of the field.
     *
     * @throws IOException if there is a writing error.
     */
    void writeFieldName(@NotNull String fieldName) throws IOException;

    /**
     * Writes a string value.
     *
     * @param c The char array to write.
     * @param offset The offset into the char array to begin writing from.
     * @param len The length of data to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeString(@NotNull char[] c, int offset, int len) throws IOException;

    /**
     * Writes a string value.
     *
     * @param text The string to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeString(@NotNull String text) throws IOException;

    /**
     * Writes a field name and a string value.
     *
     * @param fieldName The name of the field.
     * @param text The string to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeStringField(@NotNull String fieldName, @NotNull String text) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(@NotNull BigDecimal v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(@NotNull BigInteger v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(double v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(float v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(int v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(long v) throws IOException;

    /**
     * Writes a number value.
     *
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumber(short v) throws IOException;

    /**
     * Writes a field name and a number value.
     *
     * @param fieldName The name of the field.
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumberField(@NotNull String fieldName, @NotNull BigDecimal v) throws IOException;

    /**
     * Writes a field name and a number value.
     *
     * @param fieldName The name of the field.
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumberField(@NotNull String fieldName, double v) throws IOException;

    /**
     * Writes a field name and a number value.
     *
     * @param fieldName The name of the field.
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumberField(@NotNull String fieldName, float v) throws IOException;

    /**
     * Writes a field name and a number value.
     *
     * @param fieldName The name of the field.
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumberField(@NotNull String fieldName, int v) throws IOException;

    /**
     * Writes a field name and a number value.
     *
     * @param fieldName The name of the field.
     * @param v The number.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNumberField(@NotNull String fieldName, long v) throws IOException;

    /**
     * Writes a Java Object (POJO) as a Json (object) value via the usual means that object type would be serialized.
     * This means it will call any custom serializer registered for that type, otherwise it will write the object as-is
     * into Json format.
     *
     * @param pojo The Java object.
     *
     * @throws IOException if there is a writing error.
     */
    void writeObject(@NotNull Object pojo) throws IOException;

    /**
     * Writes a field name and Java Object (POJO) as a Json (object) value via the usual means that object type would be serialized.
     *
     * @param fieldName The name of the field.
     * @param pojo The Java object.
     *
     * @throws IOException if there is a writing error.
     */
    void writeObjectField(@NotNull String fieldName, @NotNull Object pojo) throws IOException;

    /**
     * Writes a field that will contain a Json object value, including the marker denoting the beginning of an object.
     *
     * @param fieldName The name of the field.
     *
     * @throws IOException if there is a writing error.
     */
    void writeObjectFieldStart(@NotNull String fieldName) throws IOException;

    /**
     * Writes a boolean value, denoted in Json as either 'true' or 'false' strings.
     *
     * @param state The boolean state.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBoolean(boolean state) throws IOException;

    /**
     * Writes a field name and boolean value, denoted in Json as either 'true' or 'false' strings.
     *
     * @param fieldName The name of the field.
     * @param state The boolean state.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBooleanField(@NotNull String fieldName, boolean state) throws IOException;

    /**
     * Writes a field that will contain a Json Array value, including the marker denoting the beginning of an array.
     *
     * @param fieldName The name of the field.
     *
     * @throws IOException if there is a writing error.
     */
    void writeArrayFieldStart(@NotNull String fieldName) throws IOException;

    /**
     * Writes a marker denoting the beginning of an array.
     *
     * @throws IOException if there is a writing error.
     */
    void writeStartArray() throws IOException;

    /**
     * Writes the marker denoting the end of an array.
     *
     * @throws IOException if there is a writing error.
     */
    void writeEndArray() throws IOException;

    /**
     * Writes an entire array including the start and end marker, so do not try to start or end an array around this
     * method call.
     *
     * @param array The array to write.
     * @param offset The offset into the array to begin writing from.
     * @param len The number of items to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeArray(@NotNull int[] array, int offset, int len) throws IOException;

    /**
     * Writes an entire array including the start and end marker, so do not try to start or end an array around this
     * method call.
     *
     * @param array The array to write.
     * @param offset The offset into the array to begin writing from.
     * @param len The number of items to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeArray(@NotNull long[] array, int offset, int len) throws IOException;

    /**
     * Writes an entire array including the start and end marker, so do not try to start or end an array around this
     * method call.
     *
     * @param array The array to write.
     * @param offset The offset into the array to begin writing from.
     * @param len The number of items to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeArray(@NotNull double[] array, int offset, int len) throws IOException;

    /**
     * Writes an entire array including the start and end marker, so do not try to start or end an array around this
     * method call.
     *
     * @param array The array to write.
     * @param offset The offset into the array to begin writing from.
     * @param len The number of items to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeArray(@NotNull String[] array, int offset, int len) throws IOException;

    /**
     * Writes a base64 encoded binary chunk value (surrounded by double quotes).
     *
     * @param config A configuration option which determines how the base64 string is written into the Json.
     * @param data The data to base64 encode and write into Json.
     * @param offset The offset into the data to begin encoding from.
     * @param len The length of data to encode.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinary(@NotNull JsonSerializedBase64Config config, @NotNull byte[] data, int offset, int len) throws IOException;

    /**
     * Writes a base64 encoded binary chunk value (surrounded by double quotes).
     *
     * @param config A configuration option which determines how the base64 string is written into the Json.
     * @param data The data to base64 encode and write into Json.
     * @param len The length of data to encode.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinary(@NotNull JsonSerializedBase64Config config, @NotNull InputStream data, int len) throws IOException;

    /**
     * Writes a base64 encoded binary chunk value (surrounded by double quotes). Uses configuration
     * {@lonk JsonSerializedBase64Config#MIME_NO_LINEFEEDS}.
     *
     * @param data The data to base64 encode and write into Json.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinary(@NotNull byte[] data) throws IOException;

    /**
     * Writes a base64 encoded binary chunk value (surrounded by double quotes). Uses configuration
     * {@link JsonSerializedBase64Config#MIME_NO_LINEFEEDS}.
     *
     * @param data The data to base64 encode and write into Json.
     * @param offset The offset into the data to begin encoding at.
     * @param len The length of data to encode.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinary(@NotNull byte[] data, int offset, int len) throws IOException;

    /**
     * Writes a base64 encoded binary chunk value (surrounded by double quotes). Uses configuration
     * {@link JsonSerializedBase64Config#MIME_NO_LINEFEEDS}.
     *
     * @param data The data to base64 encode and write into Json.
     * @param len The length of data to encode.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinary(@NotNull InputStream data, int len) throws IOException;

    /**
     * Writes a field name and base64 encoded binary chunk value (surrounded by double quotes).
     *
     * @param fieldName The name of the field.
     * @param data The data to base64 encode and write into Json.
     *
     * @throws IOException if there is a writing error.
     */
    void writeBinaryField(@NotNull String fieldName, @NotNull byte[] data) throws IOException;

    /**
     * Writes a Json null value.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNull() throws IOException;

    /**
     * Writes a field name and Json null value.
     *
     * @param fieldName The name of the field.
     *
     * @throws IOException if there is a writing error.
     */
    void writeNullField(@NotNull String fieldName) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method does not make assumptions about whether the
     * data is intended to be a field or value, it does not add any separators or escaping, nor does it require that the
     * Json output that results is valid Json.
     * <p>
     * Note that writeRaw methods do not change the context of the jsonWriter, so the field and value including the
     * opening field separator (a common if a field has come before it) must all be written explicitly to create valid
     * Json. A closing separator (a common before the next field if there is one) need not be written as that is written
     * by the next call to write a field name against the {@link JsonWriter}. To write only a raw value consider using the
     * {@link #writeRawValue} methods instead as they do change context and handle the separators and enclosing double quotes
     * like any other value writing methods.
     *
     * @param c The char to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRaw(char c) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method does not make assumptions about whether the
     * data is intended to be a field or value, it does not add any separators or escaping, nor does it require that the
     * Json output that results is valid Json.
     * <p>
     * Note that writeRaw methods do not change the context of the jsonWriter, so the field and value including the
     * opening field separator (a common if a field has come before it) must all be written explicitly to create valid
     * Json. A closing separator (a common before the next field if there is one) need not be written as that is written
     * by the next call to write a field name against the {@link JsonWriter}. To write only a raw value consider using the
     * {@link #writeRawValue} methods instead as they do change context and handle the separators and enclosing double quotes
     * like any other value writing methods.
     *
     * @param c The char array to write.
     * @param offset The offset into the char array to begin writing from.
     * @param len The length of data to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRaw(@NotNull char[] c, int offset, int len) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method does not make assumptions about whether the
     * data is intended to be a field or value, it does not add any separators or escaping, nor does it require that the
     * Json output that results is valid Json.
     * <p>
     * Note that writeRaw methods do not change the context of the jsonWriter, so the field and value including the
     * opening field separator (a common if a field has come before it) must all be written explicitly to create valid
     * Json. A closing separator (a common before the next field if there is one) need not be written as that is written
     * by the next call to write a field name against the {@link JsonWriter}. To write only a raw value consider using the
     * {@link #writeRawValue} methods instead as they do change context and handle the separators and enclosing double quotes
     * like any other value writing methods.
     *
     * @param text The string to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRaw(@NotNull String text) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method does not make assumptions about whether the
     * data is intended to be a field or value, it does not add any separators or escaping, nor does it require that the
     * Json output that results is valid Json.
     * <p>
     * Note that writeRaw methods do not change the context of the jsonWriter, so the field and value including the
     * opening field separator (a common if a field has come before it) must all be written explicitly to create valid
     * Json. A closing separator (a common before the next field if there is one) need not be written as that is written
     * by the next call to write a field name against the {@link JsonWriter}. To write only a raw value consider using the
     * {@link #writeRawValue} methods instead as they do change context and handle the separators and enclosing double quotes
     * like any other value writing methods.
     *
     * @param text The string to write.
     * @param offset The offset into the text to begin writing from.
     * @param len The length of data to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRaw(@NotNull String text, int offset, int len) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method requires the data to be a valid Json value.
     * That can be a simple Json value, or a complete array or object. Like any other 'value' method on the JsonWriter
     * (but unlike the non-value raw methods) the required separators are added around the value in the Json output and
     * the context (field followed by value repeated) in this {@link JsonWriter} is preserved correctly by this method. As
     * such the {@link JsonWriter} is ready to have the next field written after a call to this method. Enclosing double
     * quotation marks are not added unless part of the raw data passed by the caller.
     *
     * @param c The char array to write.
     * @param offset The offset into the char array to begin writing from.
     * @param len The length of data to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRawValue(@NotNull char[] c, int offset, int len) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method requires the data to be a valid Json value.
     * That can be a simple Json value, or a complete array or object. Like any other 'value' method on the JsonWriter
     * (but unlike the non-value raw methods) the required separators are added around the value in the Json output and
     * the context (field followed by value repeated) in this {@link JsonWriter} is preserved correctly by this method. As
     * such the {@link JsonWriter} is ready to have the next field written after a call to this method. Enclosing double
     * quotation marks are not added unless part of the raw data passed by the caller.
     *
     * @param text The string to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRawValue(@NotNull String text) throws IOException;

    /**
     * Writes raw data directly to the Json output unchanged. This method requires the data to be a valid Json value.
     * That can be a simple Json value, or a complete array or object. Like any other 'value' method on the JsonWriter
     * (but unlike the non-value raw methods) the required separators are added around the value in the Json output and
     * the context (field followed by value repeated) in this {@link JsonWriter} is preserved correctly by this method. As
     * such the {@link JsonWriter} is ready to have the next field written after a call to this method. Enclosing double
     * quotation marks are not added unless part of the raw data passed by the caller.
     *
     * @param text The string to write.
     * @param offset The offset into the text to begin writing from.
     * @param len The length of data to write.
     *
     * @throws IOException if there is a writing error.
     */
    void writeRawValue(@NotNull String text, int offset, int len) throws IOException;
}
