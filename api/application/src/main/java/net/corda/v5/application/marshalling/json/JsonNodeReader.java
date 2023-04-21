package net.corda.v5.application.marshalling.json;

import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/**
 * Every {@link JsonNodeReader} can be thought of as a node in the tree of Json objects being read. Each node in the tree,
 * including the root node represents a value in Json. Every node is referenced in its parent by a field name, except
 * the root node which has no parent. Thus the field name is a property of the parent not the node itself. Writers of
 * deserializers can traverse the tree and extract values of different types in order to construct a class which is
 * represented by Json.
 * <p>
 * Where the value type of a {@link JsonNodeReader} is not a Json object or array, that {@link JsonNodeReader} is a leaf in three
 * (no children) from which a single value can be read.
 * <p>
 * Where the value type of a {@link JsonNodeReader} is a Json object it will contain multiple field value pairs, and each of
 * those values is represented by a {@link JsonNodeReader} which can be thought of as a child node.
 * <p>
 * Where the value type of a {@link JsonNodeReader} is an array, that array is represented by an iterator of {@link JsonNodeReader}
 * objects, which also can be thought of as child nodes.
 */
@DoNotImplement
public interface JsonNodeReader {
    /**
     * The type of this node.
     *
     * @return The type as a value from {@link JsonNodeReaderType}.
     */
    @NotNull
    JsonNodeReaderType getType();

    /**
     * Returns true if this node represents an object in the Json.
     *
     * @return true if a Json object, false otherwise
     */
    boolean isObject();

    /**
     * Get an iterator to map entries which allows iteration over all field names and values in this {@link JsonNodeReader}.
     * If this node is not representing a Json object, null is returned.
     *
     * @return An iterator to a map entry consisting of field name and value, where value is represented by a
     * {@link JsonNodeReader}. Returns {@code null} if this node is not representing a Json object.
     */
    @Nullable
    Iterator<Map.Entry<String, JsonNodeReader>> fields();

    /**
     * Determine whether this {@link JsonNodeReader} is an object with the specified field.
     *
     * @param fieldName The name of the field.
     *
     * @return true if this {@link JsonNodeReader} represents a Json object and the field exists within that object, false
     * otherwise.
     */
    boolean hasField(@NotNull String fieldName);

    /**
     * If this {@link JsonNodeReader} represents a Json object, get the value of a field in that object by name.
     *
     * @param fieldName The name of the field.
     *
     * @return The field's value represented by a {@link JsonNodeReader} or null if no such field exists or this
     * {@link JsonNodeReader} is not representing a Json object.
     */
    @Nullable
    JsonNodeReader getField(@NotNull String fieldName);

    /**
     * Returns true if this node represents an array in the Json.
     *
     * @return true if a Json array, false otherwise
     */
    boolean isArray();

    /**
     * Returns an iterator allowing iteration over a group of {@link JsonNodeReader} each of which represents the next value
     * in the array.
     *
     * @return an iterator or null if this node type does not represent a Json array.
     */
    @Nullable
    Iterator<JsonNodeReader> asArray();

    /**
     * Returns true if this node represents a boolean in the Json.
     *
     * @return true if a boolean, otherwise false.
     */
    boolean isBoolean();

    /**
     * This method attempts to convert the underlying Json for this node to a Boolean type.
     *
     * @return the value of the underlying Json if it is a boolean type, or false if it is a different type.
     */
    boolean asBoolean();

    /**
     * This method attempts to convert the underlying Json for this node to a Boolean type.
     *
     * @param defaultValue The value to return if the underlying Json type is not a boolean.
     *
     * @return the value of the underlying Json if it is a boolean type, or {@code defaultValue} if it is a different type.
     */
    boolean asBoolean(boolean defaultValue);

    /**
     * Returns true if this node represents a number in the Json.
     *
     * @return true if a number, otherwise false.
     */
    boolean isNumber();

    /**
     * If this node represents a number in the underlying Json, this method returns the value as a Number. Otherwise it
     * returns {@code null}.
     *
     * @return the value of the underlying Json number as a Number or null if the Json value is not a number.
     */
    @Nullable
    Number numberValue();

    /**
     * Returns true if this node represents a number in the underlying Json and that number has a decimal component.
     *
     * @return true if this is floating point number.
     */
    boolean isFloatingPointNumber();

    /**
     * Returns true if this node represents a double in the Json. This is not the same as asking if the number can be
     * converted to a Double type, which can be the case even if the underlying Json value is not an explicit double.
     * Integers for example are not considered doubles even though they can be easily converted to Double types.
     * See [asDouble] and [doubleValue] for more information.
     *
     * @return true if a Double, otherwise false.
     */
    boolean isDouble();

    /**
     * If this node represents a number in the underlying Json, this method returns the value as a {@code double}. Otherwise, it
     * returns 0.0. Note that integers are considered numbers but not {@code double}s, thus {@link #isDouble} will indicate integers
     * are not {@code double}s even though {@code doubleValue} will return a {@code double} quite happily.
     * To convert non-number Json values to Double see {@link #asDouble}. When the underlying Json represents number which does
     * not fit in a {@code double} type it is converted using Java coercion rules.
     *
     * @return the value of the underlying Json number as a {@code double} or 0.0 if the Json value is not a number.
     */
    double doubleValue();

    /**
     * This method attempts to convert the underlying Json for this node to a double type using default Java coercion
     * rules. Booleans are converted to 0.0 for false and 1.0 for true. Strings are also parsed and converted as per
     * standard Java rules. Where conversion is not possible 0.0 is returned.
     *
     * @return the underlying Json value as a Double or 0.0 if that is not possible.
     */
    double asDouble();

    /**
     * This method attempts to convert the underlying Json for this node to a Double type using default Java coercion
     * rules. Booleans are converted to 0.0 for false and 1.0 for true. Strings are also parsed and converted as per
     * standard Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json value cannot be converted to a Double.
     *
     * @return the underlying Json value as a {@code double} or {@code defaultValue} if that is not possible.
     */
    double asDouble(double defaultValue);

    /**
     * If this node represents a Json number type, this method returns the value as a {@code float}. Note that there is no
     * {@code asFloat} support, {@link #asDouble} can be used to convert all Json values to a floating point number. When the
     * underlying Json represents a number which does not fit in a {@code float} type, it is converted using Java coercion.
     *
     * @return the value of the underlying Json number as a {@code float} or 0.0f if the Json value is not a number.
     */
    float floatValue();

    /**
     * Returns true if this node represents an integer in the Json. This is not the same as asking if the number can be
     * converted to an Int, see {@link #asInt} for more information.
     *
     * @return true if an integer, otherwise false.
     */
    boolean isInt();

    /**
     * Returns true if this node represents a number in the underlying Json and can be converted to an {@code int}. It includes
     * floating point numbers which have an integral part which does not overflow an {@code int}. See also
     * {@link #isFloatingPointNumber}.
     *
     * @return true if the underlying Json value can be converted, otherwise false.
     */
    boolean canConvertToInt();

    /**
     * This method attempts to convert the underlying Json for this node to an {@code int} type using default Java rules.
     * Booleans are converted to 0 for false and 1 for true. Strings are also parsed and converted as per standard
     * Java rules. Where conversion is not possible 0 is returned.
     *
     * @return the value of the underlying Json as an {@code int} or 0 if that is not possible.
     */
    int asInt();

    /**
     * This method attempts to convert the underlying Json for this node to an {@code int} type using default Java rules.
     * Booleans are converted to 0 for false and 1 for true. {@code String}s are also parsed and converted as per standard
     * coercion Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json cannot be converted to an {@code int}.
     *
     * @return the value of the underlying Json as an {@code int} or {@code defaultValue} if that is not possible.
     */
    int asInt(int defaultValue);

    /**
     * Returns true if this node represents a number in the underlying Json and can be converted to a {@code long}. It includes
     * floating point numbers which have an integral part which does not overflow a {@code long}. See also
     * {@link #isFloatingPointNumber}.
     *
     * @return true if the underlying Json value can be converted, otherwise false.
     */
    boolean canConvertToLong();

    /**
     * This method attempts to convert the underlying Json for this node to a {@code long} type using default Java rules.
     * Booleans are converted to 0L for false and 1L for true. Strings are also parsed and converted as per Java
     * coercion rules. Where conversion is not possible 0L is returned.
     *
     * @return the value of the underlying Json as a {@code long} or 0 if that is not possible.
     */
    long asLong();

    /**
     * This method attempts to convert the underlying Json for this node to a {@code long} type using default Java rules.
     * Booleans are converted to 0L for false and 1L for true. Strings are also parsed and converted as per standard
     * Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json cannot be converted to a {@code long}.
     *
     * @return the value of the underlying Json as a {@code long} or {@code defaultValue} if that is not possible.
     */
    long asLong(long defaultValue);

    /**
     * If this node represents a Json Number type, this method returns the value as a {@code short}. Note that there is no
     * {@code asShort} support, {@link #asInt} can be used to attempt to convert all Json values to an integer. When the underlying
     * Json represents a number which does not fit in a {@code Short} type, it is converted using Java coercion.
     *
     * @return the value of the underlying Json Number as a {@code short} or 0 if the Json is not a Number.
     */
    short shortValue();

    /**
     * If this node represents a Json number, this method returns the value as a {@link BigInteger}.
     *
     * @return the value of the underlying Json Number as a {@link BigInteger} or 0 if the Json is not a number.
     */
    @NotNull
    BigInteger bigIntegerValue();

    /**
     * If this node represents a Json number, this method returns the value as a {@link BigDecimal}.
     *
     * @return the value of the underlying Json Number as a {@link BigDecimal} or 0 if the Json is not a Number.
     */
    @NotNull
    BigDecimal bigDecimalValue();

    /**
     * Returns true if this node represents a string in the Json.
     *
     * @return true if a string, otherwise false.
     */
    boolean isText();

    /**
     * Returns the value of this node as a {@code String} if the underlying Json value is not an array or object. If it is an
     * array or object an empty {@code String} is returned. If the Json value is null, the string "null" is returned.
     *
     * @return the value as a {@code String}.
     */
    @NotNull
    String asText();

    /**
     * Returns the value of this node as a {@code String} if the underlying Json value is not an array or object. If it is an
     * array or object a default {@code String} is returned. If the Json value is null, the string "null" is returned.
     *
     * @param defaultValue The default value to return if this node is an array or object type.
     *
     * @return the value as a {@code String} or {@code defaultValue}
     */
    @NotNull
    String asText(@NotNull String defaultValue);

    /**
     * Returns a byte array if this node represents a string in the Json which contains a base64 encoded byte array.
     *
     * @return the base64 decoded byte array if this node represents that, or {@code null} if it represents any other type or
     * the string content is not a valid base64 encoded string.
     */
    @Nullable
    byte[] binaryValue();

    /**
     * Returns true if this node represents the value of "null" in the underlying Json.
     *
     * @return true if {@code null}, otherwise false.
     */
    boolean isNull();

    /**
     * Parse this {@link JsonNodeReader} to strongly typed objects. Will deserialize using default deserializers or any custom
     * Json deserializers registered. This method can be used if during custom deserialization of one class type, the
     * deserializer expects a field's value to contain a Json object which can be deserialized to another class type which
     * is already known to either be default deserializable, or for which other custom deserializers are registered.
     * It is the equivalent of calling the {@link JsonMarshallingService} parse method on a Json string representation of this
     * node.
     *
     * @param clazz The type to try and parse this node into.
     *
     * @return An instance of the required type or {@code null} if this node does not represent a Json serialized version of
     * that type.
     */
    @Nullable
    <T> T parse(@NotNull Class<T> clazz);
}
