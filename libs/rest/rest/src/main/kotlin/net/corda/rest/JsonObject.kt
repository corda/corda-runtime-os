package net.corda.rest

/**
 * Interface which can be used in HTTP request / response objects to indicate a field is a JSON value, object or array. Request payloads
 * will unmarshall to an escaped string which can be obtained through [escapedJson].
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
 */
interface JsonObject {
    val escapedJson: String
}