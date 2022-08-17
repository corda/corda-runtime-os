package net.corda.httprpc

/**
 * Marker interface which can be used in HTTP request / response objects to indicate a field is a JSON object. As a result, such fields
 * accept the object as a JSON object or a string version of the JSON with escaped quotation marks.
 *
 * For example, if the request object is defined as follows:
 *
 * ```
 * data class RequestWithJsonObject(val id: String, val obj: JsonObject)
 * ```
 *
 * Then the following payloads will be acceptable:
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
 * Internally, implementations of [RpcOps] endpoints should use `toString()` on the [JsonObject] to get access to the object's JSON.
 */
interface JsonObject