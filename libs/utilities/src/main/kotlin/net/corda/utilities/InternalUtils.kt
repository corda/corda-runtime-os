@file:JvmName("InternalUtils")

package net.corda.utilities

import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import java.io.InputStream
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.Temporal
import java.util.Collections
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

val Throwable.rootCause: Throwable get() = cause?.rootCause ?: this
val Throwable.rootMessage: String?
    get() {
        var message = this.message
        var throwable = cause
        while (throwable != null) {
            if (throwable.message != null) {
                message = throwable.message
            }
            throwable = throwable.cause
        }
        return message
    }

infix fun Temporal.until(endExclusive: Temporal): Duration = Duration.between(this, endExclusive)

operator fun Duration.div(divider: Long): Duration = dividedBy(divider)
operator fun Duration.times(multiplicand: Long): Duration = multipliedBy(multiplicand)

/** Returns the index of the given item or throws [IllegalArgumentException] if not found. */
fun <T> List<T>.indexOfOrThrow(item: T): Int {
    val i = indexOf(item)
    require(i != -1) { "No such element" }
    return i
}

fun InputStream.copyTo(target: Path, vararg options: CopyOption): Long = Files.copy(this, target, *options)

/** Same as [InputStream.readBytes] but also closes the stream. */
fun InputStream.readFully(): ByteArray = use { it.readBytes() }

inline fun <T> logElapsedTime(label: String, logger: Logger, body: () -> T): T {
    // Use nanoTime as it's monotonic.
    val now = System.nanoTime()
    var failed = false
    try {
        return body()
    } catch (th: Throwable) {
        failed = true
        throw th
    } finally {
        val elapsed = Duration.ofNanos(System.nanoTime() - now).toMillis()
        val msg = (if (failed) "Failed " else "") + "$label took $elapsed msec"
        logger.info(msg)
    }
}

inline fun <T, R : Any> Stream<T>.mapNotNull(crossinline transform: (T) -> R?): Stream<R> {
    return this.map { transform(it) }.filter { it != null }.map { it }
}

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
                if (it.type == this && it.isPublic && it.isStatic && it.isFinal) {
                    it.isAccessible = true
                    return uncheckedCast(it.get(null))
                } else {
                    null
                }
            }
        }
    }

fun <K, V> Iterable<Pair<K, V>>.toMultiMap(): Map<K, List<V>> = this.groupBy({ it.first }) { it.second }

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

inline val Member.isPublic: Boolean get() = Modifier.isPublic(modifiers)

inline val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

private inline val Member.isFinal: Boolean get() = Modifier.isFinal(modifiers)

fun URI.toPath(): Path = Paths.get(this)

fun URL.toPath(): Path = toURI().toPath()

fun ByteBuffer.copyBytes(): ByteArray = ByteArray(remaining()).also { get(it) }

/**
 * Simple Map structure that can be used as a cache in the DJVM.
 */
fun <K, V> createSimpleCache(maxSize: Int, onEject: (MutableMap.MutableEntry<K, V>) -> Unit = {}): MutableMap<K, V> {
    return object : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            val eject = size > maxSize
            if (eject) onEject(eldest!!)
            return eject
        }
    }
}

/** @see Collections.synchronizedMap */
fun <K, V> MutableMap<K, V>.toSynchronised(): MutableMap<K, V> = Collections.synchronizedMap(this)

/** @see Collections.synchronizedSet */
fun <E> MutableSet<E>.toSynchronised(): MutableSet<E> = Collections.synchronizedSet(this)

/**
 * Returns a [List] implementation that applies the expensive [transform] function only when an element is accessed and then caches the
 * calculated values. Size is very cheap as it doesn't call [transform].
 */
fun <T, U> List<T>.lazyMapped(transform: (T, Int) -> U): List<U> = LazyMappedList(this, transform)

private const val MAX_SIZE = 100
private val warnings = Collections.newSetFromMap(createSimpleCache<String, Boolean>(MAX_SIZE)).toSynchronised()

/**
 * Utility to help log a warning message only once.
 * It implements an ad hoc Fifo cache because there's none available in the standard libraries.
 */
fun Logger.warnOnce(warning: String) {
    if (warnings.add(warning)) {
        this.warn(warning)
    }
}
