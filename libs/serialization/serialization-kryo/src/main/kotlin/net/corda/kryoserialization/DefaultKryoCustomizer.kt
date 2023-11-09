@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory.BaseSerializerFactory
import com.esotericsoftware.kryo.SerializerFactory.FieldSerializerFactory
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import net.corda.kryoserialization.serializers.AutoCloseableSerializer
import net.corda.kryoserialization.serializers.AvroRecordRejectSerializer
import net.corda.kryoserialization.serializers.CertPathSerializer
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.CordaClosureSerializer
import net.corda.kryoserialization.serializers.InputStreamSerializer
import net.corda.kryoserialization.serializers.IteratorSerializer
import net.corda.kryoserialization.serializers.LazyMappedListSerializer
import net.corda.kryoserialization.serializers.LinkedEntrySetSerializer
import net.corda.kryoserialization.serializers.LinkedHashMapEntrySerializer
import net.corda.kryoserialization.serializers.LinkedHashMapIteratorSerializer
import net.corda.kryoserialization.serializers.LinkedKeySetSerializer
import net.corda.kryoserialization.serializers.LinkedListItrSerializer
import net.corda.kryoserialization.serializers.LoggerSerializer
import net.corda.kryoserialization.serializers.NonSerializableSerializer
import net.corda.kryoserialization.serializers.ThrowableSerializer
import net.corda.kryoserialization.serializers.X509CertificateSerializer
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.utilities.LazyMappedList
import org.apache.avro.specific.SpecificRecord
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.Collections.unmodifiableSet

class DefaultKryoCustomizer {

    companion object {
        private const val LOGGER_ID = Int.MAX_VALUE
        private val FORBIDDEN_TYPES = unmodifiableSet(setOf(NonSerializable::class.java, SpecificRecord::class.java))

        internal fun customize(
            kryo: Kryo,
            serializers: Map<Class<*>, Serializer<*>>,
            classSerializer: ClassSerializer
        ): Kryo {
            return kryo.apply {

                isRegistrationRequired = false
                references = true
                // Needed because of https://github.com/EsotericSoftware/kryo/issues/864
                setOptimizedGenerics(false)

                val defaultFactoryConfig = FieldSerializer.FieldSerializerConfig()
                // Take the safest route here and allow subclasses to have fields named the same as super classes.
                defaultFactoryConfig.extendedFieldNames = true
                // For checkpoints we still want all the synthetic fields.  This allows inner classes to reference
                // their parents after deserialization.
                defaultFactoryConfig.ignoreSyntheticFields = false
                kryo.setDefaultSerializer(FieldSerializerFactory(defaultFactoryConfig))

                instantiatorStrategy = CustomInstantiatorStrategy()

                // Serializers for more specific types have higher precedence, and
                // so we cannot add serializers for extensions of forbidden types.
                val externalSerializers = serializers.filterNot { serializer ->
                    FORBIDDEN_TYPES.any { type -> type.isAssignableFrom(serializer.key) }
                }.toSortedMap(compareBy(Class<*>::getName))

                // Add external serializers
                for ((clazz, serializer) in externalSerializers) {
                    addDefaultSerializer(clazz, serializer)
                }

                addDefaultSerializer(Iterator::class.java, object: BaseSerializerFactory<IteratorSerializer>() {
                    override fun newSerializer(kryo: Kryo, type: Class<*>) : IteratorSerializer {
                        val config = CompatibleFieldSerializer.CompatibleFieldSerializerConfig().apply {
                            ignoreSyntheticFields = false
                            extendedFieldNames = true
                        }
                        return IteratorSerializer(type, CompatibleFieldSerializer(kryo, type, config))
                    }
                })

                addDefaultSerializer(Logger::class.java, LoggerSerializer)
                addDefaultSerializer(X509Certificate::class.java, X509CertificateSerializer)
                addDefaultSerializer(Class::class.java, classSerializer)
                addDefaultSerializer(
                    LinkedHashMapIteratorSerializer.getIterator()::class.java.superclass,
                    LinkedHashMapIteratorSerializer
                )
                addDefaultSerializer(LinkedHashMapEntrySerializer.serializedType, LinkedHashMapEntrySerializer)
                addDefaultSerializer(LinkedListItrSerializer.serializedType, LinkedListItrSerializer)
                addDefaultSerializer(LazyMappedList::class.java, LazyMappedListSerializer)
                UnmodifiableCollectionsSerializer.registerSerializers(this)

                addDefaultSerializer(CertPath::class.java, CertPathSerializer)

                addDefaultSerializer(LinkedEntrySetSerializer.serializedType, LinkedEntrySetSerializer)
                addDefaultSerializer(LinkedKeySetSerializer.serializedType, LinkedKeySetSerializer)

                register(java.lang.invoke.SerializedLambda::class.java)
                register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)

                addDefaultSerializer(Throwable::class.java, object: BaseSerializerFactory<ThrowableSerializer<*>>() {
                    override fun newSerializer(kryo: Kryo, type: Class<*>) = ThrowableSerializer(kryo, type)
                })

                addDefaultSerializer(InputStream::class.java, InputStreamSerializer)
                register(FileInputStream::class.java, InputStreamSerializer)
                // InputStream subclasses whitelisting, required for attachments
                register(BufferedInputStream::class.java, InputStreamSerializer)

                //register loggers using an int ID to reduce information saved in kryo
                //ensures Kryo does not write the name of the concrete logging impl class into the serialized stream
                //See CORE-812 for more details
                //need to register all known ways of obtaining org.slf4j.Logger here against the same Id
                register(LoggerFactory.getLogger("ROOT")::class.java, LOGGER_ID)
                register(LoggerFactory.getLogger(this::class.java)::class.java, LOGGER_ID)

                addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerializer)

                // Register a serializer to reject the serialization of Avro generated classes
                addDefaultSerializer(SpecificRecord::class.java, AvroRecordRejectSerializer)
                // Register a serializer to reject the serialization of NonSerializable classes
                addDefaultSerializer(NonSerializable::class.java, NonSerializableSerializer)
            }
        }
    }

    private class CustomInstantiatorStrategy : InstantiatorStrategy {
        private val fallbackStrategy = StdInstantiatorStrategy()

        // Use this to allow construction of objects using a JVM backdoor that skips invoking the constructors, if there
        // is no no-arg constructor available.
        private val defaultStrategy = DefaultInstantiatorStrategy(fallbackStrategy)

        override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
            // However, this doesn't work for non-public classes in the java. namespace
            val strat =
                if (type.name.startsWith("java.") && !isPublic(type.modifiers)) fallbackStrategy else defaultStrategy
            return strat.newInstantiatorOf(type)
        }
    }
}
