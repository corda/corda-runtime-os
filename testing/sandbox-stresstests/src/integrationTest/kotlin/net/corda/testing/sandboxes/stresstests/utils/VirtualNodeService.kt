package net.corda.testing.sandboxes.stresstests.utils

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.persistence.testkit.components.DataSourceAdmin
import net.corda.db.schema.DbSchema
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.testing.sandboxes.testkit.RequireSandboxTestkit
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.atomic.AtomicInteger

@RequireSandboxTestkit
@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,
    @Reference
    private val dataSourceAdmin: DataSourceAdmin,
    @Reference
    private val liquibaseSchemaMigrator: LiquibaseSchemaMigrator,
    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    private companion object {
        fun generateHoldingIdentity(id: Int): HoldingIdentity {
            val x500Name = "CN=Testing$id, OU=Application, O=R3, L=London, C=GB"
            return createTestHoldingIdentity(x500Name, "test")
        }
    }

    private var connectionCounter = AtomicInteger(0)

    fun load(resourceName: String, id: Int): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity(id))
    }

    fun loadWithDbMigration(resourceName: String, id: Int): VirtualNodeInfo {
        val virtualNodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity(id))

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
        dataSource.connection.use { connection ->
            liquibaseSchemaMigrator.updateDb(connection, vaultSchema)
        }

        return virtualNodeInfo
    }
}