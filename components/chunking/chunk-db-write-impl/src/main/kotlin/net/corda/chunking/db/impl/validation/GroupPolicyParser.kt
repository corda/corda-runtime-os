package net.corda.chunking.db.impl.validation

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.exceptions.CordaRuntimeException

object GroupPolicyParser {
    fun groupId(groupPolicyJson: String): String {
        try {
            return ObjectMapper().readTree(groupPolicyJson).get("groupId").asText()
        } catch (e: NullPointerException) {
            throw CordaRuntimeException("Failed to parse group policy file - could not find `groupId` in the JSON", e)
        } catch (e: JsonParseException) {
            throw CordaRuntimeException("Failed to parse group policy file", e)
        }
    }
}
