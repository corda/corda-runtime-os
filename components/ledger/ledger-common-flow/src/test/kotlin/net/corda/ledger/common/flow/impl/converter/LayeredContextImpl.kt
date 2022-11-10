package net.corda.ledger.common.flow.impl.converter

import net.corda.v5.base.types.LayeredPropertyMap

internal class LayeredContextImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map