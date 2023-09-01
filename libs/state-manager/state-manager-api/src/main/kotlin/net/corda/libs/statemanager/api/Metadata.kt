package net.corda.libs.statemanager.api

/**
 * Mutable map that allows only primitive types to be used as values.
 */
class Metadata<V : Any>(
    private val map: MutableMap<String, V> = mutableMapOf()
) : MutableMap<String, V> by map {

    private val supportedBoxedPrimitives = listOf(
        String::class.java,
        Byte::class.java,
        Short::class.java,
        Integer::class.java,
        Long::class.java,
        Float::class.java,
        Double::class.java,
        Character::class.java
    )

    private fun isPrimitiveOrBoxedValue(value: V): Boolean {
        return when {
            value::class.java.isPrimitive -> true
            supportedBoxedPrimitives.contains(value::class.java) -> true
            else -> false
        }
    }

    override fun put(key: String, value: V): V? {
        if (!isPrimitiveOrBoxedValue(value)) {
            throw IllegalArgumentException("Only primitive types are allowed: ${value::class.simpleName}")
        }

        return map.put(key, value)
    }
}

fun <V : Any> metadata(): Metadata<V> = Metadata()

fun <V : Any> metadata(vararg pairs: Pair<String, V>): Metadata<V> = Metadata(mutableMapOf(*pairs))
