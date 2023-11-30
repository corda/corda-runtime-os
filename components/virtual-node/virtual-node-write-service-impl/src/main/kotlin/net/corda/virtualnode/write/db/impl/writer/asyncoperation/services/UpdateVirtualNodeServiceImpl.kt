package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.data.virtualnode.VirtualNodeDbConnectionUpdateRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher

@Suppress("LongParameterList")
internal class UpdateVirtualNodeServiceImpl(
    dbConnectionManager: DbConnectionManager,
    virtualNodeRepository: VirtualNodeRepository,
    holdingIdentityRepository: HoldingIdentityRepository,
    publisher: Publisher,
) : UpdateVirtualNodeService, AbstractVirtualNodeService(
    dbConnectionManager,
    holdingIdentityRepository,
    virtualNodeRepository,
    publisher
) {

    override fun validateRequest(request: VirtualNodeDbConnectionUpdateRequest): String? {
        with(request) {
            if (!vaultDdlConnection.isNullOrBlank() && vaultDmlConnection.isNullOrBlank()) {
                return "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
            }

            if (!cryptoDdlConnection.isNullOrBlank() && cryptoDmlConnection.isNullOrBlank()) {
                return "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
            }

            if (!uniquenessDdlConnection.isNullOrBlank() && uniquenessDmlConnection.isNullOrBlank()) {
                return "If Uniqueness DDL connection is provided, Uniqueness DML connection needs to be provided as well."
            }
        }
        return null
    }
}
