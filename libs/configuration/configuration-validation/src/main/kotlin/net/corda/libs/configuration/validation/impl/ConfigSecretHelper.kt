package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.secret.MaskedSecretsLookupService

/**
 *
 */
class ConfigSecretHelper {

    private companion object {
        const val TMP_SECRET = MaskedSecretsLookupService.MASK_VALUE
        val TMP_SECRET_NODE: JsonNode = TextNode(TMP_SECRET)

    }

    /**
     * Replaces all secret paths with the string MaskedSecretsLookupService.MASK_VALUE
     * and returns a new JsonNode with the secrets that have been removed.
     * @param node Node to replace secrets with strings
     * @return a new JSON node containing only secrets
     */
    fun hideSecrets(node: JsonNode): MutableMap<String, JsonNode> {
        return hideSecretsRecursive(node, node)
    }

    private fun hideSecretsRecursive(
        parentNode: JsonNode,
        node: JsonNode,
        secrets: MutableMap<String, JsonNode> = mutableMapOf(),
        nodePath: String = "",
        nodeName: String = ""
    ): MutableMap<String, JsonNode> {
        val newPath = if (nodePath == "") nodeName else "$nodePath.$nodeName"
        if (node.isObject) {
            for ((fieldName, fieldNode) in node.fields().asSequence().toList()) {
                if (fieldName.endsWith(".${SmartConfig.SECRET_KEY}") || fieldName == SmartConfig.SECRET_KEY) {
                    secrets[newPath] = node
                    (parentNode as ObjectNode).remove(nodeName)
                    parentNode.set(nodeName, TMP_SECRET_NODE)
                } else {
                    hideSecretsRecursive(node, fieldNode, secrets, newPath, fieldName)
                }
            }
        }

        return secrets
    }

    /**
     * Replaces all the masked secrets that are present in [node] with the secrets stored in [secretsNode]
     * @param node Node to update with secrets
     * @param secretsNode Secrets to insert into [node]
     */
    fun insertSecrets(node: JsonNode, secretsNode: MutableMap<String, JsonNode>) {
        insertSecretsRecursive(node, secretsNode)
    }

    private fun insertSecretsRecursive(
        node: JsonNode,
        secretsNode: MutableMap<String, JsonNode>,
        nodeName: String = ""
    ) {
        if (node.isObject) {
            for ((name, newNode) in node.fields().asSequence().toList()) {
                val nodePath = if (nodeName == "") name else "$nodeName.$name"
                if (secretsNode.keys.contains(nodePath)) {
                    val secret = secretsNode[nodePath]
                    (node as ObjectNode).set(name, secret)
                } else {
                    insertSecretsRecursive(newNode, secretsNode, nodePath)
                }
            }
        }
    }

}
