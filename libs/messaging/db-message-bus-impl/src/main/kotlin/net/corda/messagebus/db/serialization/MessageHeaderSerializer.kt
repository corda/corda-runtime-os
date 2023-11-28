package net.corda.messagebus.db.serialization

interface MessageHeaderSerializer {
    fun serialize(headers: List<Pair<String, String>>): String
    fun deserialize(headers: String): List<Pair<String, String>>
}
