package net.corda.cpiinfo.read

import net.corda.lifecycle.Lifecycle

interface CpiInfoReaderComponent : CpiInfoReader, Lifecycle
