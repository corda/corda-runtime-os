package net.corda.cli.commands.network

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.commands.network.output.Output
import net.corda.membership.lib.MemberInfoExtension
import net.corda.v5.base.types.MemberX500Name

internal class OutputStub : Output {
    private val objectMapper = ObjectMapper()
    var printedOutput: JsonNode? = null

    override fun generateOutput(content: String) {
        printedOutput = objectMapper.readTree(content)
    }

    fun getFirstPartyName(): MemberX500Name? {
        return printedOutput?.get(0)?.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText()?.let {
            MemberX500Name.parse(it)
        }
    }

    fun getAllPartyNames(): Collection<MemberX500Name> {
        val names = mutableSetOf<String>()
        return printedOutput?.mapNotNullTo(names) { it.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText() }?.let {
            names.map { MemberX500Name.parse(it) }
        } ?: emptySet()
    }
}
