package net.corda.db.persistence.testkit.components

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.DbSchema
import net.corda.persistence.common.EntitySandboxService
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    private val dataSourceAdmin: DataSourceAdmin,

    @Reference
    private val liquibaseSchemaMigrator: LiquibaseSchemaMigrator,

    @Reference
    val entitySandboxService: EntitySandboxService,

    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        fun generateHoldingIdentity() = createTestHoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    private var connectionCounter = AtomicInteger(0)

    init {
        sandboxGroupContextComponent.initCaches(2)
    }

    fun load(resourceName: String): VirtualNodeInfo {
        val virtualNodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
        val dbConnectionId = virtualNodeInfo.vaultDmlConnectionId

        // migrate DB schema
        val vaultSchema = ClassloaderChangeLog(linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                DbSchema::class.java.packageName,
                listOf("net/corda/db/schema/vnode-vault/db.changelog-master.xml"),
                DbSchema::class.java.classLoader
            )
        ))

        val dataSource = dataSourceAdmin.getOrCreateDataSource(dbConnectionId, "connection-${connectionCounter.incrementAndGet()}")
        liquibaseSchemaMigrator.updateDb(dataSource.connection, vaultSchema)
        return virtualNodeInfo
    }
}
