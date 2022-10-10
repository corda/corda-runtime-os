package net.corda.v5.application.marshalling.json

import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.DoNotImplement
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Every [JsonNodeReader] can be thought of as a node in the tree of Json objects being read. Each node in the tree,
 * including the root node represents a value in Json. Every node is referenced in its parent by a field name, except
 * the root node which has no parent. Thus the field name is a property of the parent not the node itself. Writers of
 * deserializers can traverse the tree and extract values of different types in order to construct a class which is
 * represented by Json.
 *
 * Where the value type of a [JsonNodeReader] is not a Json object or array, that [JsonNodeReader] is a leaf in three
 * (no children) from which a single value can be read.
 *
 * Where the value type of a [JsonNodeReader] is a Json object it will contain multiple field value pairs, and each of
 * those values is represented by a [JsonNodeReader] which can be thought of as a child node.
 *
 * Where the value type of a [JsonNodeReader] is an array, that array is represented by an iterator of [JsonNodeReader]
 * objects, which also can be thought of as child nodes.
 */
@Suppress("TooManyFunctions")
@DoNotImplement
interface JsonNodeReader {
    /**
     * The type of this node.
     *
     * @return The type as a value from [JsonNodeReaderType].
     */
    fun getType(): JsonNodeReaderType

    /**
     * Returns true if this node represents an object in the Json.
     *
     * @return true if a Json object, false otherwise
     */
    fun isObject(): Boolean

    /**
     * Get an iterator to map entries which allows iteration over all field names and values in this [JsonNodeReader].
     * If this node is not representing a Json object, null is returned.
     *
     * @return An iterator to a map entry consisting of field name and value, where value is represented by a
     * [JsonNodeReader]. Returns null if this node is not representing a Json object.
     */
    fun fields(): Iterator<Map.Entry<String, JsonNodeReader>>?

    /**
     * Determine whether this [JsonNodeReader] is an object with the specified field.
     *
     * @param fieldName The name of the field.
     *
     * @return true if this [JsonNodeReader] represents a Json object and the field exists within that object, false
     * otherwise.
     */
    fun hasField(fieldName: String): Boolean

    /**
     * If this [JsonNodeReader] represents a Json object, get the value of a field in that object by name.
     *
     * @param fieldName The name of the field.
     *
     * @return The field's value represented by a [JsonNodeReader] or null if no such field exists or this
     * [JsonNodeReader] is not representing a Json object.
     */
    fun getField(fieldName: String): JsonNodeReader?

    /**
     * Returns true if this node represents an array in the Json.
     *
     * @return true if a Json array, false otherwise
     */
    fun isArray(): Boolean

    /**
     * Returns an iterator allowing iteration over a group of [JsonNodeReader] each of which represents the next value
     * in the array.
     *
     * @return an iterator or null if this node type does not represent a Json array.
     */
    fun asArray(): Iterator<JsonNodeReader>?

    /**
     * Returns true if this node represents a boolean in the Json.
     *
     * @return true if a boolean, otherwise false.
     */
    fun isBoolean(): Boolean

    /**
     * This method attempts to convert the underlying Json for this node to a Boolean type.
     *
     * @return the value of the underlying Json if it is a boolean type, or false if it is a different type.
     */
    fun asBoolean(): Boolean

    /**
     * This method attempts to convert the underlying Json for this node to a Boolean type.
     *
     * @param defaultValue The value to return if the underlying Json type is not a boolean.
     *
     * @return the value of the underlying Json if it is a boolean type, or [defaultValue] if it is a different type.
     */
    fun asBoolean(defaultValue: Boolean): Boolean

    /**
     * Returns true if this node represents a number in the Json.
     *
     * @return true if a number, otherwise false.
     */
    fun isNumber(): Boolean

    /**
     * If this node represents a number in the underlying Json, this method returns the value as a Number. Otherwise it
     * returns null.
     *
     * @return the value of the underlying Json number as a Number or null if the Json value is not a number.
     */
    fun numberValue(): Number?

    /**
     * Returns true if this node represents a number in the underlying Json and that number has a decimal component.
     *
     * @return true if this is floating point number.
     */
    fun isFloatingPointNumber(): Boolean

    /**
     * Returns true if this node represents a double in the Json. This is not the same as asking if the number can be
     * converted to a Double type, which can be the case even if the underlying Json value is not an explicit double.
     * Integers for example are not considered doubles even though they can be easily converted to Double types.
     * See [asDouble] and [doubleValue] for more information.
     *
     * @return true if a Double, otherwise false.
     */
    fun isDouble(): Boolean

    /**
     * If this node represents a number in the underlying Json, this method returns the value as a Double. Otherwise it
     * returns 0.0. Note that integers are considered numbers but not doubles, thus [isDouble] will indicate integers
     * are not doubles even though [doubleValue] will return a Double quite happily.
     * To convert non-number Json values to Double see [asDouble]. When the underlying Json represents number which does
     * not fit in a Double type it is converted using Java coercion rules.
     *
     * @return the value of the underlying Json number as a Double or 0.0 if the Json value is not a number.
     */
    fun doubleValue(): Double

    /**
     * This method attempts to convert the underlying Json for this node to a Double type using default Java coercion
     * rules. Booleans are converted to 0.0 for false and 1.0 for true. Strings are also parsed and converted as per
     * standard Java rules. Where conversion is not possible 0.0 is returned.
     *
     * @return the underlying Json value as a Double or 0.0 if that is not possible.
     */
    fun asDouble(): Double

    /**
     * This method attempts to convert the underlying Json for this node to a Double type using default Java coercion
     * rules. Booleans are converted to 0.0 for false and 1.0 for true. Strings are also parsed and converted as per
     * standard Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json value cannot be converted to a Double.
     *
     * @return the underlying Json value as a Double or [defaultValue] if that is not possible.
     */
    fun asDouble(defaultValue: Double): Double

    /**
     * If this node represents a Json number type, this method returns the value as a Float. Note that there is no
     * asFloat support, [asDouble] can be used to convert all Json values to a floating point number. When the
     * underlying Json represents a number which does not fit in a Float type, it is converted using Java coercion.
     *
     * @return the value of the underlying Json number as a Float or 0.0f if the Json value is not a number.
     */
    fun floatValue(): Float

    /**
     * Returns true if this node represents an integer in the Json. This is not the same as asking if the number can be
     * converted to an Int, see [asInt] for more information.
     *
     * @return true if an integer, otherwise false.
     */
    fun isInt(): Boolean

    /**
     * Returns true if this node represents a number in the underlying Json and can be converted to an Int. It includes
     * floating point numbers which have an integral part which does not overflow an Int. See also
     * [isFloatingPointNumber].
     *
     * @return true if the underlying Json value can be converted, otherwise false.
     */
    fun canConvertToInt(): Boolean

    /**
     * This method attempts to convert the underlying Json for this node to an Int type using default Java rules.
     * Booleans are converted to 0 for false and 1 for true. Strings are also parsed and converted as per standard
     * Java rules. Where conversion is not possible 0 is returned.
     *
     * @return the value of the underlying Json as an Int or 0 if that is not possible.
     */
    fun asInt(): Int

    /**
     * This method attempts to convert the underlying Json for this node to an Int type using default Java rules.
     * Booleans are converted to 0 for false and 1 for true. Strings are also parsed and converted as per standard
     * coercion Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json cannot be converted to an Int.
     *
     * @return the value of the underlying Json as an Int or [defaultValue] if that is not possible.
     */
    fun asInt(defaultValue: Int): Int

    /**
     * Returns true if this node represents a number in the underlying Json and can be converted to a Long. It includes
     * floating point numbers which have an integral part which does not overflow a Long. See also
     * [isFloatingPointNumber].
     *
     * @return true if the underlying Json value can be converted, otherwise false.
     */
    fun canConvertToLong(): Boolean

    /**
     * This method attempts to convert the underlying Json for this node to a Long type using default Java rules.
     * Booleans are converted to 0L for false and 1L for true. Strings are also parsed and converted as per Java
     * coercion rules. Where conversion is not possible 0L is returned.
     *
     * @return the value of the underlying Json as a Long or 0 if that is not possible.
     */
    fun asLong(): Long

    /**
     * This method attempts to convert the underlying Json for this node to a Long type using default Java rules.
     * Booleans are converted to 0L for false and 1L for true. Strings are also parsed and converted as per standard
     * Java rules. Where conversion is not possible the supplied default is returned.
     *
     * @param defaultValue The value to return if the underlying Json cannot be converted to a Long.
     *
     * @return the value of the underlying Json as a Long or [defaultValue] if that is not possible.
     */
    fun asLong(defaultValue: Long): Long

    /**
     * If this node represents a Json Number type, this method returns the value as a Short. Note that there is no
     * asShort support, [asInt] can be used to attempt to convert all Json values to an integer. When the underlying
     * Json represents a number which does not fit in a Short type, it is converted using Java coercion.
     *
     * @return the value of the underlying Json Number as a Short or 0 if the Json is not a Number.
     */
    fun shortValue(): Short

    /**
     * If this node represents a Json number, this method returns the value as a BigInteger.
     *
     * @return the value of the underlying Json Number as a BigInteger or 0 if the Json is not a number.
     */
    fun bigIntegerValue(): BigInteger

    /**
     * If this node represents a Json number, this method returns the value as a BigDecimal.
     *
     * @return the value of the underlying Json Number as a BigDecimal or 0 if the Json is not a Number.
     */
    fun bigDecimalValue(): BigDecimal

    /**
     * Returns true if this node represents a string in the Json.
     *
     * @return true if a string, otherwise false.
     */
    fun isText(): Boolean

    /**
     * Returns the value of this node as a String if the underlying Json value is not an array or object. If it is an
     * array or object an empty String is returned. If the Json value is null, the string "null" is returned.
     *
     * @return the value as a String.
     */
    fun asText(): String

    /**
     * Returns the value of this node as a String if the underlying Json value is not an array or object. If it is an
     * array or object a default String is returned. If the Json value is null, the string "null" is returned.
     *
     * @param defaultValue The default value to return if this node is an array or object type.
     *
     * @return the value as a String or defaultValue
     */
    fun asText(defaultValue: String): String

    /**
     * Returns a byte array if this node represents a string in the Json which contains a base64 encoded byte array.
     *
     * @return the base64 decoded byte array if this node represents that, or null if it represents any other type or
     * the string content is not a valid base64 encoded string.
     */
    fun binaryValue(): ByteArray?

    /**
     * Returns true if this node represents the value of "null" in the underlying Json.
     *
     * @return true if null, otherwise false.
     */
    fun isNull(): Boolean

    /**
     * Parse this [JsonNodeReader] to strongly typed objects. Will deserialize using default deserializers or any custom
     * Json deserializers registered. This method can be used if during custom deserialization of one class type, the
     * deserializer expects a field's value to contain a Json object which can be deserialized to another class type which
     * is already known to either be default deserializable, or for which other custom deserializers are registered.
     * It is the equivalent of calling the [JsonMarshallingService] parse method on a Json string representation of this
     * node.
     *
     * @param clazz The type to try and parse this node into.
     *
     * @return An instance of the required type or null if this node does not represent a Json serialized version of
     * that type.
     */
    fun <T> parse(clazz: Class<T>): T?
}

/**
 * Parse this [JsonNodeReader] to strongly typed objects. Will deserialize using default deserializers or any custom
 * Json deserializers registered. This method can be used if during custom deserialization of one class type, the
 * deserializer expects a field's value to contain a Json object which can be deserialized to another class type which
 * is already known to either be default deserializable, or for which other custom deserializers are registered.
 * It is the equivalent of calling the [JsonMarshallingService] parse method on a Json string representation of this
 * node.
 *
 * @return An instance of the required type or null if this node does not represent a Json serialized version of
 * that type.
 */
inline fun <reified T> JsonNodeReader.parse(): T? = this.parse(T::class.java)
