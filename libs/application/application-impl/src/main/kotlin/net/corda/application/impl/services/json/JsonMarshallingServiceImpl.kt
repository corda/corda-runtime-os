package net.corda.application.impl.services.json

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.databind.util.LookupCache
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.JsonDeserializerAdaptor
import net.corda.common.json.serializers.JsonSerializerAdaptor
import net.corda.common.json.serializers.SerializationCustomizer
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableMap

/**
 * Simple implementation, requires alignment with other serialization such as that used
 * in the HTTP library
 */
@Component(service = [ JsonMarshallingService::class, UsedByFlow::class, UsedByPersistence::class ], scope = PROTOTYPE)
class JsonMarshallingServiceImpl : JsonMarshallingService,
    UsedByFlow, UsedByPersistence, SingletonSerializeAsToken, SerializationCustomizer {
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

        // Register Kotlin after resetting the AnnotationIntrospector.
        registerModule(KotlinModule.Builder().build())
    }

    private val customSerializableClasses = mutableSetOf<Class<*>>()
    private val customDeserializableClasses = mutableSetOf<Class<*>>()

    override fun format(data: Any): String {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.writeValueAsString(data)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                mapper.readValue(input, clazz)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
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
            AccessController.doPrivileged(PrivilegedExceptionAction {
                unmodifiableMap(mapper.readValue(
                    input, mapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)
                ))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun setSerializer(serializer: JsonSerializer<*>, type: Class<*>): Boolean {
        val jsonSerializerAdaptor = JsonSerializerAdaptor(serializer, type)
        if (customSerializableClasses.contains(jsonSerializerAdaptor.serializingType)) return false
        customSerializableClasses.add(jsonSerializerAdaptor.serializingType)

        val module = SimpleModule()
        module.addSerializer(jsonSerializerAdaptor.serializingType, jsonSerializerAdaptor)
        mapper.registerModule(module)

        return true
    }

    override fun setDeserializer(deserializer: JsonDeserializer<*>, type: Class<*>): Boolean {
        val jsonDeserializerAdaptor = JsonDeserializerAdaptor(deserializer, type)
        if (customDeserializableClasses.contains(jsonDeserializerAdaptor.deserializingType)) return false
        customDeserializableClasses.add(jsonDeserializerAdaptor.deserializingType)

        val module = SimpleModule()
        // Here we have to cast from Class<*> to Class<Any> because Jackson generics try to ensure we're not trying to
        // associate a deserializer with a Class<...> it doesn't support at compile time, which would normally be quite
        // convenient. Because we have no type information available at compile time we need to be very unspecific about
        // what our deserializer can support. This has no effect at runtime because type erasure precludes Jackson
        // knowing anything about these types except via typeless Class objects once the code is compiled.
        @Suppress("unchecked_cast")
        module.addDeserializer(jsonDeserializerAdaptor.deserializingType as Class<Any>, jsonDeserializerAdaptor)
        mapper.registerModule(module)

        return true
    }
}
