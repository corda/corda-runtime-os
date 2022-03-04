package net.corda.chunking.db.impl

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.v5.base.exceptions.CordaRuntimeException

object GroupPolicyParser {
    /**
     * Parse the group policy json string into a map
     *
     * @throws [CordaRuntimeException] if we cannot parse the map
     * @return map of values
     */
    fun parse(groupPolicyJson: String): Map<String, Any> {
        try {
            val objectMapper = ObjectMapper()
            return objectMapper.readValue<MutableMap<String, Any>>(groupPolicyJson)
        } catch (e: JsonParseException) {
            throw CordaRuntimeException("Failed to parse group policy file", e)
        }
    }
}
