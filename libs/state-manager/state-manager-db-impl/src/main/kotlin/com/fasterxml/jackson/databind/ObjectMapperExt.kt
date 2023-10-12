package com.fasterxml.jackson.databind

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.Metadata

fun ObjectMapper.convertToMetadata(json: String) = Metadata(this.readValue(json))
