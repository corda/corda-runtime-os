package net.corda.cpi.upload.endpoints.service

import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.lifecycle.Lifecycle

/**
 * This service is used for creating a new [CpiUploadManager] on new configuration through
 * [net.corda.cpi.upload.endpoints.common.CpiUploadRestResourceHandler].
 */
interface CpiUploadService : Lifecycle {
    val cpiUploadManager: CpiUploadManager
}
