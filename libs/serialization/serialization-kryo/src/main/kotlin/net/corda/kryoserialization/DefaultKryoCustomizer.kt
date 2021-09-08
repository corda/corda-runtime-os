@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.BitSetSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.AutoCloseableSerialisationDetector
import net.corda.kryoserialization.serializers.CertPathSerializer
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.CordaClosureBlacklistSerializer
import net.corda.kryoserialization.serializers.CordaClosureSerializer
import net.corda.kryoserialization.serializers.InputStreamSerializer
import net.corda.kryoserialization.serializers.IteratorSerializer
import net.corda.kryoserialization.serializers.LazyMappedListSerializer
import net.corda.kryoserialization.serializers.LinkedHashMapEntrySerializer
import net.corda.kryoserialization.serializers.LinkedHashMapIteratorSerializer
import net.corda.kryoserialization.serializers.LinkedListItrSerializer
import net.corda.kryoserialization.serializers.LoggerSerializer
import net.corda.kryoserialization.serializers.StackTraceSerializer
import net.corda.kryoserialization.serializers.X509CertificateSerializer
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serializers.PrivateKeySerializer
import net.corda.serializers.PublicKeySerializer
import net.corda.utilities.LazyMappedList
import net.corda.v5.crypto.CompositeKey
import org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.net.www.protocol.jar.JarURLConnection
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*

class DefaultKryoCustomizer {

    companion object {
        private const val LOGGER_ID = Int.MAX_VALUE;

        fun customize(
            kryo: Kryo,
            serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>>,
            classResolver: CordaClassResolver,
            classSerializer: ClassSerializer,
            ): Kryo {
            return kryo.apply {

                classLoader = FrameworkUtil.getBundle(this::class.java)?.adapt(BundleWiring::class.java)?.classLoader
                    ?: this::class.java.classLoader

                classResolver.setKryo(this)

                // The ClassResolver can only be set in the Kryo constructor and Quasar doesn't
                // provide us with a way of doing that
                Kryo::class.java.getDeclaredField("classResolver").apply {
                    isAccessible = true
                }.set(this, classResolver)

                // Take the safest route here and allow subclasses to have fields named the same as super classes.
                fieldSerializerConfig.cachedFieldNameStrategy = FieldSerializer.CachedFieldNameStrategy.EXTENDED

                instantiatorStrategy = CustomInstantiatorStrategy()

                // These probably should go somewhere else, but they're meant to be right before building as,
                // for security purposes, we don't want someone overriding them
                listOf(
                    PublicKey::class.java, EdDSAPublicKey::class.java, CompositeKey::class.java,
                    BCECPublicKey::class.java, BCRSAPublicKey::class.java, BCSphincs256PublicKey::class.java
                ).forEach {
                    addDefaultSerializer(it, KryoCheckpointSerializerAdapter(PublicKeySerializer()).adapt())
                }
                listOf(
                    PrivateKey::class.java, EdDSAPrivateKey::class.java, BCECPrivateKey::class.java,
                    BCRSAPrivateCrtKey::class.java, BCSphincs256PrivateKey::class.java
                ).forEach {
                    addDefaultSerializer(it, KryoCheckpointSerializerAdapter(PrivateKeySerializer()).adapt())
                }

                // Required for HashCheckingStream (de)serialization.
                // Note that return type should be specifically set to InputStream, otherwise it may not work,
                // i.e. val aStream : InputStream = HashCheckingStream(...).
                addDefaultSerializer(InputStream::class.java, InputStreamSerializer)
                addDefaultSerializer(Logger::class.java, LoggerSerializer)
                addDefaultSerializer(X509Certificate::class.java, X509CertificateSerializer)

                // WARNING: reordering the registrations here will cause a change in the serialized form, since classes
                // with custom serializers get written as registration ids. This will break backwards-compatibility.
                // Please add any new registrations to the end.

                addDefaultSerializer(Class::class.java, classSerializer)
                addDefaultSerializer(
                    LinkedHashMapIteratorSerializer.getIterator()::class.java.superclass,
                    LinkedHashMapIteratorSerializer
                )
                addDefaultSerializer(LinkedHashMapEntrySerializer.getEntry()::class.java, LinkedHashMapEntrySerializer)
                addDefaultSerializer(LinkedListItrSerializer.getListItr()::class.java, LinkedListItrSerializer)
                addDefaultSerializer(Arrays.asList("").javaClass, ArraysAsListSerializer())
                addDefaultSerializer(LazyMappedList::class.java, LazyMappedListSerializer)
                UnmodifiableCollectionsSerializer.registerSerializers(this)
                // InputStream subclasses whitelisting, required for attachments.
                addDefaultSerializer(BufferedInputStream::class.java, InputStreamSerializer)
                val jarUrlInputStreamClass = JarURLConnection::class.java.declaredClasses.single {
                    it.simpleName == "JarURLInputStream"
                }
                addDefaultSerializer(jarUrlInputStreamClass, InputStreamSerializer)
                // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
                addDefaultSerializer(Array<StackTraceElement>::class.java, StackTraceSerializer())
                addDefaultSerializer(BitSet::class.java, BitSetSerializer())
                addDefaultSerializer(FileInputStream::class.java, InputStreamSerializer)
                addDefaultSerializer(CertPath::class.java, CertPathSerializer)

                register(java.lang.invoke.SerializedLambda::class.java)
                addDefaultSerializer(ClosureSerializer.Closure::class.java, CordaClosureBlacklistSerializer)

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
                addDefaultSerializer(ClosureSerializer.Closure::class.java, CordaClosureSerializer)

                //Add external serializers
                for (serializer in serializers) {
                    addDefaultSerializer(serializer.key, KryoCheckpointSerializerAdapter(serializer.value).adapt())
                }
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
