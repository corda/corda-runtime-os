package net.corda.processors.rpc

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class OpenApiCompatibilityTest {

    companion object {
        private val logger = contextLogger()

        private val importantRpcOps = setOf(
            "CertificatesRpcOps",
            "ConfigRPCOps",
            "CpiUploadRPCOps",
            "FlowRpcOps",
            "HsmRpcOps",
            "KeysRpcOps",
            "MemberLookupRpcOps",
            "MemberRegistrationRpcOps",
            "PermissionEndpoint",
            "RoleEndpoint",
            "UserEndpoint",
            "VirtualNodeMaintenanceRPCOps",
            "VirtualNodeRPCOps"
        )

        // `cardinality` is not equal to `expectedRpcOps.size` as there might be some test RpcOps as well
        @InjectService(service = PluggableRPCOps::class, cardinality = 15, timeout = 10_000)
        lateinit var dynamicRpcOps: List<RpcOps>

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {

        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun tearDown() {

        }
    }

    @Test
    fun test() {
        val allOps = dynamicRpcOps.map { (it as PluggableRPCOps<*>).targetInterface.simpleName }.sorted()
        logger.info("RPC Ops discovered: $allOps")
        assertThat(allOps).containsAll(importantRpcOps)
    }
}