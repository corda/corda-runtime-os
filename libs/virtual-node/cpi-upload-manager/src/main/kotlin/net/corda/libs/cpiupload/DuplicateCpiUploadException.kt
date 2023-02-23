package net.corda.libs.cpiupload

/**
 * Exception type that is thrown if there is an attempt to upload a
 * duplicate CPI .
 *
 * This exception is passed via a kafka envelope message and then
 * "checked" in the rpc ops layer when received.
 *
 * @param resourceName Must be the 'resource name' rather than the message so we
 * can pass it back to [net.corda.rest.exception.ResourceAlreadyExistsException]
 */
class DuplicateCpiUploadException(resourceName: String) : Exception(resourceName)
