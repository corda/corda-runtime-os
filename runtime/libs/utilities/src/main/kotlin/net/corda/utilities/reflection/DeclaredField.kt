package net.corda.utilities.reflection

import java.lang.reflect.Field

/**
 * A simple wrapper around a [Field] object providing type safe read and write access using [value], ignoring the field's
 * visibility.
 */
class DeclaredField<T>(clazz: Class<*>, name: String, private val receiver: Any?) {
    private val javaField = findField(name, clazz)
    var value: T
        get() {
            synchronized(this) {
                @Suppress("unchecked_cast")
                return javaField.accessible { get(receiver) as T }
            }
        }
        set(value) {
            synchronized(this) {
                javaField.accessible {
                    set(receiver, value)
                }
            }
        }
    val name: String = javaField.name

    private fun <RESULT> Field.accessible(action: Field.() -> RESULT): RESULT {
        @Suppress("DEPRECATION")    // JDK11: isAccessible() should be replaced with canAccess() (since 9)
        val accessible = isAccessible
        isAccessible = true
        try {
            return action(this)
        } finally {
            isAccessible = accessible
        }
    }

    @Throws(NoSuchFieldException::class)
    private fun findField(fieldName: String, clazz: Class<*>?): Field {
        if (clazz == null) {
            throw NoSuchFieldException(fieldName)
        }
        return try {
            return clazz.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            findField(fieldName, clazz.superclass)
        }
    }
}