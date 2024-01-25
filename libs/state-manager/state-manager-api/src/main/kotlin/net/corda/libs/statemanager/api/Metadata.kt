package net.corda.libs.statemanager.api

/**
 * Metadata key used to store the actual State Type, if relevant.
 *
 * TODO-[CORE-16416]: remove once Isolated State Managers per State Type has been implemented.
 */
const val STATE_TYPE = "state.type"

/**
 * Map that allows only primitive types to be used as values.
 */
class Metadata(
    private val map: Map<String, Any> = emptyMap()
) : Map<String, Any> by map {
    companion object {
        private val supportedType = listOf(
            String::class.java,
            java.lang.String::class.java,
            Number::class.java,
            java.lang.Number::class.java,
            Boolean::class.java,
            java.lang.Boolean::class.java,
        )

        private fun isPrimitiveOrBoxedValue(value: Any): Boolean {
            return supportedType.any { it.isAssignableFrom(value.javaClass) }
        }
    }

    init {
        map.filter { kvp -> !isPrimitiveOrBoxedValue(kvp.value) }.takeIf { it.isNotEmpty() }?.also { kvp ->
            val invalidPairs = kvp.entries.joinToString { "${it.key}/${it.value::class.java.name}" }
            throw IllegalArgumentException("Type(s) not supported: $invalidPairs")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Metadata

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    fun containsKeyWithValue(key: String, value: Any) = map.containsKey(key) && map[key] == value
}

fun metadata(): Metadata = Metadata()

fun metadata(vararg pairs: Pair<String, Any>): Metadata = Metadata(mapOf(*pairs))
