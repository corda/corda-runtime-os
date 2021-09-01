@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.BitSetSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import net.corda.kryoserialization.serializers.AutoCloseableSerialisationDetector
import net.corda.kryoserialization.serializers.CertPathSerializer
import net.corda.kryoserialization.serializers.CordaClosureBlacklistSerializer
import net.corda.kryoserialization.serializers.CordaClosureSerializer
import net.corda.kryoserialization.serializers.InputStreamSerializer
import net.corda.kryoserialization.serializers.IteratorSerializer
import net.corda.kryoserialization.serializers.LazyMappedListSerializer
import net.corda.kryoserialization.serializers.LinkedHashMapEntrySerializer
import net.corda.kryoserialization.serializers.LinkedHashMapIteratorSerializer
import net.corda.kryoserialization.serializers.LinkedListItrSerializer
import net.corda.kryoserialization.serializers.LoggerSerializer
import net.corda.kryoserialization.serializers.SerializeAsTokenSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.kryoserialization.serializers.X509CertificateSerializer
import net.corda.utilities.LazyMappedList
import net.corda.v5.serialization.SerializeAsToken
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.net.www.protocol.jar.JarURLConnection
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*

class DefaultKryoCustomizer {

    companion object {
        private const val LOGGER_ID = Int.MAX_VALUE;

        fun customize(kryo: Kryo): Kryo {
            return kryo.apply {
                // Take the safest route here and allow subclasses to have fields named the same as super classes.
                fieldSerializerConfig.cachedFieldNameStrategy = FieldSerializer.CachedFieldNameStrategy.EXTENDED

                instantiatorStrategy = CustomInstantiatorStrategy()

                // Required for HashCheckingStream (de)serialization.
                // Note that return type should be specifically set to InputStream, otherwise it may not work,
                // i.e. val aStream : InputStream = HashCheckingStream(...).
                addDefaultSerializer(InputStream::class.java, InputStreamSerializer)
                addDefaultSerializer(SingletonSerializeAsToken::class.java, SingletonSerializeAsTokenSerializer)
                addDefaultSerializer(SerializeAsToken::class.java, SerializeAsTokenSerializer<SerializeAsToken>())
                addDefaultSerializer(Logger::class.java, LoggerSerializer)
                addDefaultSerializer(X509Certificate::class.java, X509CertificateSerializer)

                // WARNING: reordering the registrations here will cause a change in the serialized form, since classes
                // with custom serializers get written as registration ids. This will break backwards-compatibility.
                // Please add any new registrations to the end.

                addDefaultSerializer(
                    LinkedHashMapIteratorSerializer.getIterator()::class.java.superclass,
                    LinkedHashMapIteratorSerializer
                )
                register(LinkedHashMapEntrySerializer.getEntry()::class.java, LinkedHashMapEntrySerializer)
                register(LinkedListItrSerializer.getListItr()::class.java, LinkedListItrSerializer)
                register(Arrays.asList("").javaClass, ArraysAsListSerializer())
                register(LazyMappedList::class.java, LazyMappedListSerializer)
                UnmodifiableCollectionsSerializer.registerSerializers(this)
                // InputStream subclasses whitelisting, required for attachments.
                register(BufferedInputStream::class.java, InputStreamSerializer)
                val jarUrlInputStreamClass = JarURLConnection::class.java.declaredClasses.single {
                    it.simpleName == "JarURLInputStream"
                }
                register(jarUrlInputStreamClass, InputStreamSerializer)
                // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
                register(Array<StackTraceElement>::class, read = { _, _ -> emptyArray() }, write = { _, _, _ -> })
                register(BitSet::class.java, BitSetSerializer())
                register(FileInputStream::class.java, InputStreamSerializer)
                register(CertPath::class.java, CertPathSerializer)

                register(java.lang.invoke.SerializedLambda::class.java)
                register(ClosureSerializer.Closure::class.java, CordaClosureBlacklistSerializer)

                addDefaultSerializer(Iterator::class.java) { kryo, type ->
                    IteratorSerializer(type, CompatibleFieldSerializer<Iterator<*>>(kryo, type).apply {
                        setIgnoreSyntheticFields(false)
                    })
                }

                //register loggers using an int ID to reduce information saved in kryo
                //ensures Kryo does not write the name of the concrete logging impl class into the serialized stream
                //See CORE-812 for more details
                //need to register all known ways of obtaining org.slf4j.Logger here against the same Id
                register(LoggerFactory.getLogger("ROOT")::class.java, LOGGER_ID)
                register(LoggerFactory.getLogger(this::class.java)::class.java, LOGGER_ID)

                addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
                register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
            }
        }
    }

    private class CustomInstantiatorStrategy : InstantiatorStrategy {
        private val fallbackStrategy = StdInstantiatorStrategy()

        // Use this to allow construction of objects using a JVM backdoor that skips invoking the constructors, if there
        // is no no-arg constructor available.
        private val defaultStrategy = Kryo.DefaultInstantiatorStrategy(fallbackStrategy)

        override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
            // However this doesn't work for non-public classes in the java. namespace
            val strat =
                if (type.name.startsWith("java.") && !isPublic(type.modifiers)) fallbackStrategy else defaultStrategy
            return strat.newInstantiatorOf(type)
        }
    }
}
