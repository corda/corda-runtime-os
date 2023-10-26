package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.Metadata

fun ObjectMapper.convertToMetadata(json: String) = Metadata(readValue(json))
