package net.corda.chunking.db.impl

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.exceptions.CordaRuntimeException

object GroupPolicyParser {
    fun groupId(groupPolicyJson: String): String {
        try {
            return ObjectMapper().readTree(groupPolicyJson).get("groupId").asText()
        } catch (e: JsonParseException) {
            throw CordaRuntimeException("Failed to parse group policy file", e)
        }
    }
}
