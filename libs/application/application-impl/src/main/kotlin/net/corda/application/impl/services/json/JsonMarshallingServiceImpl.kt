package net.corda.application.impl.services.json

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.databind.util.LookupCache
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

/**
 * Simple implementation, requires alignment with other serialization such as that used
 * in the HTTP library
 */
@Component(service = [ JsonMarshallingService::class, SingletonSerializeAsToken::class ], scope = PROTOTYPE)
class JsonMarshallingServiceImpl : JsonMarshallingService, SingletonSerializeAsToken {
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

        registerModule(standardTypesModule())
    }

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
                mapper.readValue(input, mapper.typeFactory.constructCollectionType(List::class.java, clazz))
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}
