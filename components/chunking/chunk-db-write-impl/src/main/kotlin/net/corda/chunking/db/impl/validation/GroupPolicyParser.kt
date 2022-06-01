package net.corda.chunking.db.impl.validation

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.UUID

object GroupPolicyParser {
    private const val MGM_GROUP_ID = "CREATE_ID"

    fun groupId(groupPolicyJson: String): String {
        try {
            val groupId = ObjectMapper().readTree(groupPolicyJson).get("groupId").asText()
            return if (groupId == MGM_GROUP_ID) UUID.randomUUID().toString() else groupId
        } catch (e: NullPointerException) {
            throw CordaRuntimeException("Failed to parse group policy file - could not find `groupId` in the JSON", e)
        } catch (e: JsonParseException) {
            throw CordaRuntimeException("Failed to parse group policy file", e)
        }
    }
}
