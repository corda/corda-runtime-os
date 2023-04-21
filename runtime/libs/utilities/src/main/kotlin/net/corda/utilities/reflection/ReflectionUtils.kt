package net.corda.utilities.reflection

import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) static field of the receiver [Class]. */
fun <T> Class<*>.staticField(name: String): DeclaredField<T> = DeclaredField(this, name, null)

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) static field of the receiver [KClass]. */
fun <T> KClass<*>.staticField(name: String): DeclaredField<T> = DeclaredField(java, name, null)

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) instance field of the receiver object. */
fun <T> Any.declaredField(name: String): DeclaredField<T> = DeclaredField(javaClass, name, this)

/**
 * Returns a [DeclaredField] wrapper around the (possibly non-public) instance field of the receiver object, but declared
 * in its superclass [clazz].
 */
fun <T> Any.declaredField(clazz: KClass<*>, name: String): DeclaredField<T> = DeclaredField(clazz.java, name, this)

/**
 * Returns a [DeclaredField] wrapper around the (possibly non-public) instance field of the receiver object, but declared
 * in its superclass [clazz].
 */
fun <T> Any.declaredField(clazz: Class<*>, name: String): DeclaredField<T> = DeclaredField(clazz, name, this)

inline val Member.isPublic: Boolean get() = Modifier.isPublic(modifiers)

inline val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

private inline val Member.isFinal: Boolean get() = Modifier.isFinal(modifiers)

fun <T> Class<T>.castIfPossible(obj: Any): T? = if (isInstance(obj)) cast(obj) else null

/** creates a new instance if not a Kotlin object */
fun <T : Any> KClass<T>.objectOrNewInstance(): T {
    return this.objectInstance ?: this.createInstance()
}

/** Similar to [KClass.objectInstance] but also works on private objects. */
val <T : Any> Class<T>.kotlinObjectInstance: T?
    get() {
        return try {
            kotlin.objectInstance
        } catch (_: Throwable) {
            val field = try {
                getDeclaredField("INSTANCE")
            } catch (_: NoSuchFieldException) {
                null
            }
            field?.let {
                if (it.type == this && it.isPublicStaticFinal) {
                    it.isAccessible = true
                    @Suppress("unchecked_cast")
                    return it.get(null) as? T
                } else {
                    null
                }
            }
        }
    }

private val Field.isPublicStaticFinal: Boolean get() = isPublic && isStatic && isFinal

/** Returns the location of this class. */
val Class<*>.location: URL get() = protectionDomain.codeSource.location

/** Convenience method to get the package name of a class literal. */
val KClass<*>.packageName: String get() = java.packageName_

// re-defined to prevent clash with Java 9 Class.packageName: https://docs.oracle.com/javase/9/docs/api/java/lang/Class.html#getPackageName--
val Class<*>.packageName_: String get() = requireNotNull(this.packageNameOrNull) { "$this not defined inside a package" }
// This intentionally does not go via `package` as that code path is slow and contended and just ends up doing this.
val Class<*>.packageNameOrNull: String?
    get() {
        val name = this.name
        val i = name.lastIndexOf('.')
        return if (i != -1) {
            name.substring(0, i)
        } else {
            null
        }
    }