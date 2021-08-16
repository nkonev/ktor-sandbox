package io.ktor.samples.sandbox

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

/**
 * Main entrypoint of the executable that starts a Netty webserver at port 8080
 * and registers the [module].
 *
 * This is a hello-world application, while the important part is that the build.gradle
 * includes all the available artifacts and serves to use as module for a scratch or to autocomplete APIs.
 */
fun main(args: Array<String>) {
    embeddedServer(CIO, port = 8080) { module()}.start(wait = true)
}

data class Customer(val id: Int, val firstName: String, val lastName: String)

/**
 * Module that just registers the root path / and replies with a text.
 */
fun Application.module() {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/customer") {
            call.respond(Customer(1, "Jet", "Brains"))
        }
    }
}