package net.corda.interop.service

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.virtualnode.HoldingIdentity

interface InteropAliasTranslator : LifecycleWithDominoTile {
    fun getRealHoldingIdentity(recipientId: String?): HoldingIdentity?
}