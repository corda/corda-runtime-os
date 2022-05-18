package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.secret.MaskedSecretsLookupService

/**
 *
 */
class ConfigSecretHelper {

    private companion object {
        const val TMP_SECRET = MaskedSecretsLookupService.MASK_VALUE
    }

    /**
     * Replaces all secret paths with the string MaskedSecretsLookupService.MASK_VALUE and returns a new JsonNode with the secrets that have been removed.
     * @param node Node to replace secrets with strings
     * @return a new JSON node containing only secrets
     */
    fun hideSecrets(node: JsonNode): JsonNode {
        return hideSecretsRecursive(node)
    }

    private fun hideSecretsRecursive(
        node: JsonNode,
        secrets: MutableMap<String, JsonNode> = mutableMapOf(),
        nodeName: String = ""
    ): JsonNode {
        if (node.isObject) {
            for ((name, newNode) in node.fields().asSequence().toList()) {
                val nodePath = if (nodeName == "") name else "$nodeName.$name"
                if (name.endsWith(".${SmartConfig.SECRET_KEY}") || name == SmartConfig.SECRET_KEY) {
                    secrets[nodePath] = newNode
                    (node as ObjectNode).remove(name)
                    node.put(name, TMP_SECRET)
                } else {
                    hideSecretsRecursive(newNode, secrets, nodePath)
                }
            }
        }

        return secrets.toJSONNode()
    }

    /**
     * Replaces all the masked secrets that are present in [node] with the secrets stored in [secretsNode]
     * @param node Node to update with secrets
     * @param secretsNode Secrets to insert into [node]
     */
    fun insertSecrets(node: JsonNode, secretsNode: JsonNode) {
        insertSecretsRecursive(node, secretsNode)
    }

    private fun insertSecretsRecursive(
        node: JsonNode,
        secretsNode: JsonNode,
        nodeName: String = ""
    ) {
        if (node.isObject) {
            for ((name, newNode) in node.fields().asSequence().toList()) {
                val nodePath = if (nodeName == "") name else "$nodeName.$name"
                if (name.endsWith(".${SmartConfig.SECRET_KEY}") || name == SmartConfig.SECRET_KEY) {
                    val secret = secretsNode[nodePath]
                    (node as ObjectNode).set(name, secret)
                } else {
                    insertSecretsRecursive(newNode, secretsNode, nodePath)
                }
            }
        }
    }

    private fun <K, V> MutableMap<K, V>.toJSONNode(): JsonNode {
        val objectMapper = ObjectMapper()
        val jsonNode = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
        return objectMapper.readTree(jsonNode)
    }
}
