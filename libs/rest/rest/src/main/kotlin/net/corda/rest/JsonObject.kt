package net.corda.rest

/**
 * Interface which can be used in HTTP request / response objects to indicate a possibility of a field being a JSON value or a string.
 * Request payloads will unmarshall it to an escaped string which can be obtained through [escapedJson].
 *
 * This allows real JSON to be supplied in requests rather than escaping JSON and supplying it as a string value field.
 *
 * For example, if the request object is defined as follows:
 *
 * ```
 * data class RequestWithJsonObject(val id: String, val obj: JsonObject)
 * ```
 *
 * Then both the following payloads will be acceptable:
 *
 * A - "obj" is a JSON object.
 * ```
 * {
 *   "id": "real-object",
 *   "obj": {"message": "Hey Mars", "planetaryOnly":"true", "target":"C=GB, L=FOURTH, O=MARS, OU=PLANET"}
 * }
 * ```
 * B - "obj" is a String containing JSON with escaped quotation marks.
 * ```
 * {
 *   "id": "string-escaped",
 *   "obj": "{\"message\":\"Hey Mars\", \"planetaryOnly\":\"true\", \"target\":\"C=GB, L=FOURTH, O=MARS, OU=PLANET\"}"
 * }
 * ```
 *
 * In a similar way if this interface is used in the response and [escapedJson] represents a valid JSON - it will be
 * included in the response verbatim, i.e. without escaping.
 */
interface JsonObject {
    val escapedJson: String
}
