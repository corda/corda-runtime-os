package net.corda.common.json.serialization

interface JsonRepresentable {
    fun toJsonString(): String
}