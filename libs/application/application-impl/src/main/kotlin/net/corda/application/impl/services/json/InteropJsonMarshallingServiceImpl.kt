package net.corda.application.impl.services.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.databind.util.LookupCache
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.cipher.suite.merkle.MerkleProofProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableMap

/**
 * Advanced implementation, requires alignment with other serialization such as that used in the HTTP library
 */
@Component(
    service = [ InteropJsonMarshallingService::class, UsedByFlow::class ],
    scope = PROTOTYPE
)
class InteropJsonMarshallingServiceImpl
@Activate constructor(
    @Reference(service = MerkleProofProvider::class)
    private val merkleProofProvider: MerkleProofProvider
) : InteropJsonMarshallingService, UsedByFlow, SingletonSerializeAsToken {
    private companion object {
        private const val INITIAL_SIZE = 16
        private const val MAX_SIZE = 200
    }

    private val mapper = ObjectMapper().apply {
        // Provide our own TypeFactory instance rather than using shared global one.
        typeFactory = TypeFactory.defaultInstance()
            .withCache(LRUMap<Any, JavaType>(INITIAL_SIZE, MAX_SIZE) as LookupCache<Any, JavaType>)

        // Provide our own AnnotationIntrospector to avoid using a shared global cache.
        setAnnotationIntrospector(JacksonAnnotationIntrospector())
        val module = SimpleModule()
        module.addSerializer(SecureHash::class.java, SecureHashSerializer)
        module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)

        // Interoperability
        registerModule(DigitalSignatureAndMetadataSerialisationModule(merkleProofProvider).module)
        registerModule(JavaTimeModule())

        // Register Kotlin after resetting the AnnotationIntrospector.
        registerModule(KotlinModule.Builder().build())
        registerModule(module)
    }

    override fun format(data: Any): String {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.writeValueAsString(data)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.readValue(input, clazz)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                unmodifiableList(mapper.readValue(
                    input, mapper.typeFactory.constructCollectionType(List::class.java, clazz)
                ))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <K, V> parseMap(input: String, keyClass: Class<K>, valueClass: Class<V>): Map<K, V> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                unmodifiableMap(mapper.readValue(
                    input, mapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)
                ))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}
