package net.corda.utxo.token.sync.services

import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.HoldingIdentity

interface SyncService {

    fun validateUnspentTokens(holdingIdentity: HoldingIdentity, tokensToCheck: List<StateRef>): List<TokenRecord>

    fun getNextFullSyncBlock(state: CurrentSyncState): List<TokenRecord>

    fun getNextPeriodCheckBlock(state: CurrentSyncState): Map<TokenPoolKeyRecord, List<TokenRefRecord>>
}

