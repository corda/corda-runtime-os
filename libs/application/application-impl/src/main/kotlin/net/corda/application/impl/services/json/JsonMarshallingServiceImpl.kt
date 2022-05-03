package net.corda.application.impl.services.json

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.databind.util.LookupCache
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController.doPrivileged
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
    }

    // Truncate the execution stack down to JsonMarshallingServiceImpl
    // so that we can invoke this service from within a Corda sandbox.
    // The JSON library will almost certainly want to use reflection!
    private fun <T> doWithPrivileges(action: PrivilegedExceptionAction<T>): T {
        return try {
            // This function is caller-sensitive! Please keep it within
            // JsonMarshallingServiceImpl so that we know whose privileges
            // we are actually using here!
            doPrivileged(action)
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun formatJson(input: Any): String {
        return doWithPrivileges {
            mapper.writeValueAsString(input)
        }
    }

    override fun <T> parseJson(input: String, clazz: Class<T>): T {
        return doWithPrivileges {
            mapper.readValue(input, clazz)
        }
    }

    override fun <T> parseJsonList(input: String, clazz: Class<T>): List<T> {
        return doWithPrivileges {
            mapper.readValue(input, mapper.typeFactory.constructCollectionType(List::class.java, clazz))
        }
    }
}
