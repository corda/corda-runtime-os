package net.corda.services.json

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.services.JsonMarshallingServiceInternal
import net.corda.v5.application.injection.CordaFlowInjectable
import org.osgi.service.component.annotations.Component

/**
 * Simple implementation, requires alignment with other serialization such as that used
 * in the HTTP library
 */
@Component(service = [JsonMarshallingServiceInternal::class])
class JsonMarshallingServiceImpl : JsonMarshallingServiceInternal, CordaFlowInjectable {

    private val mapper = ObjectMapper()

    override fun formatJson(input: Any): String {
        return mapper.writeValueAsString(input)
    }

    override fun <T> parseJson(input: String, clazz: Class<T>): T {
        return mapper.readValue(input, clazz)
    }

    override fun <T> parseJsonList(input: String, clazz: Class<T>): List<T> {
        return mapper.readValue(input, mapper.typeFactory.constructCollectionType(List::class.java, clazz))
    }
}
