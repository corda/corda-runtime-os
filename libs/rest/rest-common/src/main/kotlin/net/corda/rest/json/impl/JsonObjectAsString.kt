package net.corda.rest.json.impl

import net.corda.rest.JsonObject

/**
 * Implementation of [JsonObject] that provides the [escapedJson] of a Json object for marshalling purposes.
 */
internal data class JsonObjectAsString(override val escapedJson: String) : JsonObject {
    override fun toString(): String {
        return escapedJson
    }
}