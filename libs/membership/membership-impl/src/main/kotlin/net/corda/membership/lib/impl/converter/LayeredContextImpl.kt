package net.corda.membership.lib.impl.converter

import net.corda.v5.base.types.LayeredPropertyMap

internal class LayeredContextImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map