package io.ktor.samples.sandbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.sessions.*

class JacksonSerializer<T>(
    private val type: Class<T>
): SessionSerializer<T> {
    private val jackson = ObjectMapper().registerModule(KotlinModule())
    override fun deserialize(text: String): T {
        return jackson.readValue(text, type) as T
    }

    override fun serialize(session: T): String {
        return jackson.writeValueAsString(session)
    }
}