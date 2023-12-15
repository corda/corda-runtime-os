package net.corda.libs.permissions.storage.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.messaging.api.processor.RPCResponderProcessor

/**
 * The [PermissionStorageWriterProcessor] is a [RPCResponderProcessor] that processes incoming [PermissionManagementRequest]s and returns
 * [PermissionManagementResponse]s.
 *
 * Construct instances of this interface using [PermissionStorageWriterProcessorFactory].
 */
interface PermissionStorageWriterProcessor : RPCResponderProcessor<PermissionManagementRequest, PermissionManagementResponse>
