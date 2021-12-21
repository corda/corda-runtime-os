package net.corda.crypto

import net.corda.lifecycle.Lifecycle

/**
 Defines component providing a map of HSM configuration labels to help to route messages for HSMs
 which have dedicated workers.
 */
interface HSMLabelMapComponent : HSMLabelMap, Lifecycle