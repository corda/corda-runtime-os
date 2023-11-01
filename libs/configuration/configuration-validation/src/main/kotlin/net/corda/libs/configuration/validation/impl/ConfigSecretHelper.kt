package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory

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
     * and returns a map of all the secrets that have been removed keyed by their path.
     * @param node Node to replace secrets with strings
     * @return a map containing all secrets
     */
    fun hideSecrets(node: JsonNode): MutableMap<String, JsonNode> {
        return hideSecretsRecursive(node, node)
    }

    // Make it easy to walk JSON node structures by returning the names of paths
    // within a node, paired with the value of each path, at one layer only.

    // e.g. {"a":1, "b":2} returns ["a" to JsonNode("1"), "b" to JsonNode("2")]
    // and  ["alpha", "beta"] returns ["1" to JsonNode("alpha"), "2" to JsonNode("beta")]
    // and "foo" returns []
    //
    // (except the second half of the pairs are actually JsonNode instances)
    private fun getFieldValues(node: JsonNode): List<Pair<String, JsonNode>> = when(node) {
        is ObjectNode -> node.fields().asSequence().toList().map { it.key to it.value }
        is ArrayNode -> node.toList().withIndex().map { it.index.toString() to it.value }
        else -> emptyList()
    }

    private fun hideSecretsRecursive(
        parentNode: JsonNode,
        node: JsonNode,
        secrets: MutableMap<String, JsonNode> = mutableMapOf(),
        nodePath: String = "",
        nodeName: String = ""
    ): MutableMap<String, JsonNode> {
        val newPath = if (nodePath == "") nodeName else "$nodePath.$nodeName"
        getFieldValues(node).forEach { (fieldName, fieldNode) ->
            if ( fieldName == ConfigKeys.SECRET_KEY) {
                secrets[newPath] = node
                (parentNode as ObjectNode).remove(nodeName)
                parentNode.set(nodeName, TMP_SECRET_NODE)
            } else {
                hideSecretsRecursive(node, fieldNode, secrets, newPath, fieldName)
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
        getFieldValues(node).forEach { (name, newNode) ->
            val nodePath = if (nodeName == "") name else "$nodeName.$name"
            val secret = secretsNode[nodePath]
            if (secret != null) {
                (node as ObjectNode).set<ObjectNode>(name, secret)
            } else {
                insertSecretsRecursive(newNode, secretsNode, nodePath)
            }
        }
    }
}
