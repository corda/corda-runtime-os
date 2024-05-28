package net.corda.restclient.dto

import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion

/**
 * This is a copy of [net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters] as the config as [net.corda.rest.JsonObject]
 * is not handled correctly. See [JIRA comment](https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=303404)
 */
data class UpdateConfigParametersObjectNode(
    val section: String, val version: Int, val config: ObjectNode, val schemaVersion: ConfigSchemaVersion
)
