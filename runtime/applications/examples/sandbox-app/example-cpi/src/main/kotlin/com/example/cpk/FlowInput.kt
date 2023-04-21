package com.example.cpk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class FlowInput @JsonCreator constructor(
    @JsonProperty("message")
    val message: String?
)
