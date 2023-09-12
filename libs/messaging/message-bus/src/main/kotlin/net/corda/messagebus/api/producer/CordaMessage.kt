package net.corda.messagebus.api.producer

/**
 * Object to encapsulate a generic producer message.
 * @property payload the payload of the message.
 * @property props an additional properties map.
 */
@Suppress("UNCHECKED_CAST")
data class CordaMessage<T: Any>(
    val payload: T?,
    val props: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Adds a new property to the internal storage.
     *
     * @param property A key value [Pair] to insert into our additional properties map.
     */
    fun addProperty(key: String, value: Any) {
        props[key] = value
    }

    /**
     * Retrieves a property from the additional properties map without casting.
     *
     * @param key The key of the property to retrieve.
     * @return The property associated with the given key.
     * @throws NoSuchElementException if no property with the given key exists.
     */
    fun getProperty(key: String) : Any {
        return getPropertyOrNull(key) ?: throw NoSuchElementException("No property found with the key: '$key'")
    }

    /**
     * Retrieves a property of a specific type from the additional properties map.
     *
     * @param key The key of the property to retrieve.
     * @return The property associated with the given key, cast to the specified type.
     * @throws NoSuchElementException if no property with the given key exists.
     * @throws ClassCastException if the property cannot be cast to the specified type.
     */
    @JvmName("getPropertyTyped")
    inline fun <reified T> getProperty(key: String) : T {
        return (getProperty(key) as? T)
            ?: throw ClassCastException("Property '$key' could not be cast to type: '${T::class.java}'.")
    }

    /**
     * Retrieves a property from the additional properties map without casting, returning null if not found.
     *
     * @param key The key of the property to retrieve.
     * @return The property associated with the given key, or null if not found.
     */
    fun getPropertyOrNull(key: String) : Any? {
        return props[key]
    }

    /**
     * Retrieves a property of a specific type from the additional properties map, returning null if not found or if the cast fails.
     *
     * @param key The identifier of the property to retrieve.
     * @return The property associated with the given key cast to the specified type, or null if not found or casting fails.
     * @throws ClassCastException if the property cannot be cast to the specified type.
     */
    @JvmName("getPropertyOrNullTyped")
    inline fun <reified T> getPropertyOrNull(key: String) : T? {
        val value = props[key] ?: return null
        return (value as? T)
            ?: throw ClassCastException(
                "Property '$key' could not be cast to type: '${T::class.java}'."
            )
    }
}
