package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.states.LockState
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.membership.MemberInfo

class CompositeKeys {
    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    private fun constructLockedAsset(asset: LockState, newOwner: MemberInfo): LockState {
        // Build composite key
        val compositeKey =  compositeKeyGenerator.create(listOf(CompositeKeyNodeAndWeight(asset.owner, 1),
            CompositeKeyNodeAndWeight(newOwner.ledgerKeys.single(), 1)
        ), 1)

        return asset.withNewOwner(compositeKey, listOf(asset.owner, newOwner.ledgerKeys[0]))

    }
}