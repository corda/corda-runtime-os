package net.cordacon.example.landregistry.states

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.time.LocalDateTime


@BelongsToContract(LandTitleContract::class)
class LandTitleState(
    val titleNumber: String,
    val location: String,
    val areaInSquareMeter: Int,
    val extraDetails: String,
    val registrationTitleStamp: LocalDateTime,
    val owner: PublicKey,
    val issuer: PublicKey
    ) : ContractState {

    override val participants: List<PublicKey>
        get() = listOf(issuer, owner).distinct()
    }