package net.corda.libs.cpiupload

/**
 * Exception type that is thrown if there is an attempt to upload a
 * CPI with a groupID that's already in use.
 *
 * This exception is passed via a kafka envelope message and then
 * "checked" in the rpc ops layer when received.
 *
 * @param resourceName Must be the 'resource name' rather than the message so we
 * can pass it back to [net.corda.httprpc.exception.ResourceAlreadyExistsException]
 * @param requestId The ID of the request that tried to reuse a group ID.
 */
class ReUsedGroupIdException(resourceName: String, val requestId: String) : Exception(resourceName)
