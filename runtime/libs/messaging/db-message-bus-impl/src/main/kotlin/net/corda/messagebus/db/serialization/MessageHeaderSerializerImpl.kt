package net.corda.messagebus.db.serialization

import com.fasterxml.jackson.databind.ObjectMapper

class MessageHeaderSerializerImpl : MessageHeaderSerializer {
    private val objectMapper = ObjectMapper()

    override fun serialize(headers: List<Pair<String, String>>): String {
        return objectMapper.writeValueAsString(Headers(headers.map { Header(it.first, it.second) }))
    }

    override fun deserialize(headers: String): List<Pair<String, String>> {
        return objectMapper.readValue(headers, Headers::class.java).items!!.map { Pair(it.key!!, it.value!!) }
    }

    private class Header(var key: String? = null, var value: String? = null)
    private class Headers(var items: List<Header>? = listOf())
}
