package net.corda.messaging.api.mediator.statemanager

/**
 * Supported comparison operations on metadata values.
 */
enum class Operation {
    Equals,
    NotEquals,
    LesserThan,
    GreaterThan,
}

/**
 * Mutable map that allows only primitive types to be used as values.
 */
class Metadata(
    private val map: MutableMap<String, Any> = mutableMapOf()
) : MutableMap<String, Any> by map {

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

    override fun put(key: String, value: Any): Any? {
        if (!isPrimitiveOrBoxedValue(value)) {
            throw IllegalArgumentException("Type not supported: ${value::class}")
        }

        return map.put(key, value)
    }
}

fun metadata(): Metadata = Metadata()

fun metadata(vararg pairs: Pair<String, Any>): Metadata = Metadata(mutableMapOf(*pairs))