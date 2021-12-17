package net.corda.crypto.client

import net.corda.crypto.clients.KeyRegistrarClient
import net.corda.lifecycle.Lifecycle

interface KeyRegistrarClientComponent : KeyRegistrarClient, Lifecycle