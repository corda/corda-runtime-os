package net.corda.messagebus.api.producer

@Suppress("UNCHECKED_CAST")
data class CordaMessage<T: Any>(
    val payload: T?,
    val props: MutableMap<String, Any> = mutableMapOf()
) {
    fun addProperty(property: Pair<String, Any>) {
        props[property.first] = property.second
    }
    fun getProperty(id: String) : Any {
        return getPropertyOrNull(id) ?: throw NoSuchElementException("")
    }

    fun <T> getProperty(id: String) : T {
        return getPropertyOrNull<T>(id) ?: throw NoSuchElementException("")
    }

    fun getPropertyOrNull(id: String) : Any? {
        return props[id]
    }

    fun <T> getPropertyOrNull(id: String) : T? {
        return props[id] as? T ?: throw IllegalArgumentException("")
    }
}