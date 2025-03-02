package test

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.camel.component.kafka.KafkaConstants
import java.util.logging.Level
import java.util.logging.Logger

from("kafka:cp4waiops-cartridge.connector-async-config?maxPollIntervalMs=30000").process { exchange ->
    val deleteHeader = exchange.`in`.getHeader("ce_method", String::class.java)
    if (deleteHeader != null && deleteHeader.toLowerCase() == "delete") {
        Logger.getLogger("Test").log(Level.INFO, "DELETE")
        exchange.`in`.body = ObjectMapper().writeValueAsString(
            mapOf(
                "phase" to "Deleted"
            )
        )
        return@process
    }
    val bodyMap = ObjectMapper().readValue(exchange.`in`.getBody(String::class.java),
        object : TypeReference<Map<*, *>>() {})
    val isStructured = bodyMap["contentMode"]?.equals("Structured") == true
    val bodyNew: Map<*, *> = when {
        isStructured -> exchange.`in`.headers.entries.associate { (key, _) ->
            Pair(
                key.substringAfter("ce_").toLowerCase(),
                kotlin.runCatching { String(exchange.`in`.getHeader(key, ByteArray::class.java)) }
                    .getOrDefault("")
            )
        } + mapOf(
            "data" to mapOf(
                "phase" to "Accepted",
                "requeueAfter" to 60e9.toLong()
            )
        )
        else -> mapOf(
            "phase" to "Accepted",
            "requeueAfter" to null
        )
    }
    exchange.`in`.body = ObjectMapper().writeValueAsString(bodyNew)
    if (isStructured) {
        val key = exchange.`in`.headers[KafkaConstants.KEY]
        exchange.`in`.headers = emptyMap()
        exchange.`in`.headers[KafkaConstants.KEY] = key
    }
}.to("kafka:cp4waiops-cartridge.connector-async-response")