package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.network.output.Output
import net.corda.membership.lib.MemberInfoExtension

internal class OutputStub : Output {
    private val objectMapper = ObjectMapper()
    var printedOutput: JsonNode? = null

    override fun generateOutput(content: String) {
        printedOutput = objectMapper.readTree(content)
    }

    fun getFirstPartyName(): String? {
        return printedOutput?.get(0)?.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText()
    }

    fun getAllPartyNames(): Collection<String> {
        val names = mutableSetOf<String>()
        return printedOutput?.mapNotNullTo(names) { it.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText() }
            ?: emptySet()
    }
}
