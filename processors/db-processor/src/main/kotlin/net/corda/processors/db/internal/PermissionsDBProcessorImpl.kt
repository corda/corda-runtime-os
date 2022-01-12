package net.corda.processors.db.internal

import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.PermissionStorageWriterService
import net.corda.processors.db.PermissionsDBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [PermissionsDBProcessor::class])
class PermissionsDBProcessorImpl @Activate constructor(
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService
) : PermissionsDBProcessor {

    companion object {
        private val log = contextLogger()
    }

    @Volatile
    private var started = false

    override val isRunning: Boolean
        get() = started

    override fun start() {

        log.info("Starting PermissionCacheService")
        permissionCacheService.start()

        log.info("Starting PermissionStorageReaderService")
        permissionStorageReaderService.start()

        log.info("Starting PermissionStorageWriterService")
        permissionStorageWriterService.start()

        started = true
    }

    override fun stop() {
        permissionStorageWriterService.stop()
        permissionStorageReaderService.stop()
        permissionCacheService.stop()

        started = false
    }
}