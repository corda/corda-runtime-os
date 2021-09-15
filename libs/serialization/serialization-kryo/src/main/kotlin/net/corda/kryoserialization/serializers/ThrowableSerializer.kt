package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.factories.ReflectionSerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import net.corda.v5.base.util.uncheckedCast
import java.util.*

/**
 * For serializing instances if [Throwable] honoring the fact that [java.lang.Throwable.suppressedExceptions]
 * might be un-initialized/empty.
 * In the absence of this class [CompatibleFieldSerializer] will be used which will assign a *new* instance of
 * unmodifiable collection to [java.lang.Throwable.suppressedExceptions] which will fail some sentinel identity checks
 * e.g. in [java.lang.Throwable.addSuppressed]
 */
class ThrowableSerializer<T>(kryo: Kryo, type: Class<T>) : Serializer<Throwable>(false, true) {

    private companion object {
        private val IS_OPENJ9 = System.getProperty("java.vm.name").toLowerCase().contains("openj9")
        private val suppressedField = Throwable::class.java.getDeclaredField("suppressedExceptions")

        private val sentinelValue = let {
            if (!IS_OPENJ9) {
                val sentinelField = Throwable::class.java.getDeclaredField("SUPPRESSED_SENTINEL")
                sentinelField.isAccessible = true
                sentinelField.get(null)
            } else {
                Collections.EMPTY_LIST
            }
        }

        init {
            suppressedField.isAccessible = true
        }
    }

    private val delegate: Serializer<Throwable> =
        uncheckedCast(ReflectionSerializerFactory.makeSerializer(kryo, FieldSerializer::class.java, type))

    override fun write(kryo: Kryo, output: Output, throwable: Throwable) {
        delegate.write(kryo, output, throwable)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Throwable>): Throwable {
        val throwableRead = delegate.read(kryo, input, type)
        if (throwableRead.suppressed.isEmpty()) {
            throwableRead.setSuppressedToSentinel()
        }
        return throwableRead
    }

    private fun Throwable.setSuppressedToSentinel() = suppressedField.set(this, sentinelValue)
}
