package net.corda.crypto.client

import net.corda.crypto.KeyRegistrarClient
import net.corda.lifecycle.Lifecycle

interface KeyRegistrarClientComponent : KeyRegistrarClient, Lifecycle