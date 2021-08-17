package io.ktor.samples.sandbox

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext


/**
 * Main entrypoint of the executable that starts a Netty webserver at port 8080
 * and registers the [module].
 *
 * This is a hello-world application, while the important part is that the build.gradle
 * includes all the available artifacts and serves to use as module for a scratch or to autocomplete APIs.
 */
fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

data class Customer(val id: Int, val firstName: String, val lastName: String)

data class UserSession(val id: String, val count: Int)

abstract class SimplifiedSessionStorage : SessionStorage {
    abstract suspend fun read(id: String): String?
    abstract suspend fun write(id: String, data: String?): Unit

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val data = read(id) ?: throw NoSuchElementException("Session $id not found")
        return consumer(ByteReadChannel(data))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        return provider(CoroutineScope(Dispatchers.IO).reader(coroutineContext, autoFlush = true) {
            write(id, channel.readAvailable())
        }.channel)
    }
}

suspend fun ByteReadChannel.readAvailable(): String {
    val data = ByteArrayOutputStream()
    val temp = ByteArray(1024)
    while (!isClosedForRead) {
        val read = readAvailable(temp)
        if (read <= 0) break
        data.write(temp, 0, read)
    }
    return String(data.toByteArray())
}

class RedisSessionStorage : SimplifiedSessionStorage {

    private val jedis: Jedis

    constructor() {
        jedis = Jedis(HostAndPort("localhost", 36379))
    }

    override suspend fun read(id: String): String? {
        return jedis.get(id)
    }

    override suspend fun write(id: String, data: String?) {
        jedis.set(id, data)
    }

    override suspend fun invalidate(id: String) {
        jedis.del(id)
    }


}


/**
 * Module that just registers the root path / and replies with a text.
 */
fun Application.module() {
    install(ContentNegotiation) {
        jackson()
    }
    install(Sessions) {
        cookie<UserSession>("user_session", storage = RedisSessionStorage())
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/customer") {
            call.respond(Customer(1, "Jet", "Brains"))
        }
        get("/login") {
            call.sessions.set(UserSession(id = "123abc", count = 0))
            call.respondRedirect("/")
        }
        get("/session") {
            val userSession: UserSession? = call.sessions.get<UserSession>()
            if (userSession != null) {
                call.respond(userSession)
            } else {
                call.respond(HttpStatusCode.Gone)
            }
        }
        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
    }
}