package net.corda.httprpc.server.impl.internal

import net.corda.httprpc.JsonObject

/**
 * Implementation of [JsonObject] that provides the String [value] of a Json object for marshalling purposes.
 */
data class JsonObjectAsString(val value: String) : JsonObject {
    override fun toString(): String {
        return value
    }
}