package net.corda.ledger.consensual.persistence.impl.processor.tests

import net.corda.persistence.common.EntitySandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManagerFactory

data class DbTestContext(
    val virtualNodeInfo: VirtualNodeInfo,
    val entitySandboxService: EntitySandboxService,
    val sandbox: SandboxGroupContext,
    private val entityManagerFactory: EntityManagerFactory,
    val schemaName: String
)