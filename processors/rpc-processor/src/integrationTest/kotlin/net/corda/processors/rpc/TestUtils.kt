package net.corda.processors.rpc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.ServerSocket
import java.nio.file.Path

// We cannot use implementations from `http-rpc-test-common` module as it is not OSGi module
// And it cannot become OSGi module due to UniRest not being OSGi compliant
fun findFreePort() = ServerSocket(0).use { it.localPort }

val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")

fun removeDocContentFromJson(jsonString: String): String {
    val json = ObjectMapper().readTree(jsonString)
    json.removeDocContent()
    return json.toPrettyString()
}

fun JsonNode.removeDocContent() {
    if(this is ObjectNode) {
        this.remove(listOf("name", "title", "description"))
    }
    this.elements().forEach { node ->
        node.removeDocContent()
    }
}