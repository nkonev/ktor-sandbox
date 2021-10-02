package io.ktor.samples.sandbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
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
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.kodein.di.bind
import org.kodein.di.instance
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import org.kodein.di.ktor.DIFeature
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.singleton
import org.litote.kmongo.KMongo
import java.security.SecureRandom
import java.util.*
import kotlin.NoSuchElementException
import org.litote.kmongo.* //NEEDED! import KMongo extensions

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

    private val jedisPool: JedisPool

    constructor() {
        val jedisPoolConfig: GenericObjectPoolConfig<Jedis> = GenericObjectPoolConfig<Jedis>()
        // TODO timeouts
        // TODO close pool
        jedisPool = JedisPool(jedisPoolConfig, "localhost", 37779)
    }

    override suspend fun read(id: String): String? {
        jedisPool.resource.use {
            return it.get(id)
        }
    }

    override suspend fun write(id: String, data: String?) {
        jedisPool.resource.use {
            it.set(id, data)
        }
    }

    override suspend fun invalidate(id: String) {
        jedisPool.resource.use {
            it.del(id)
        }
    }
}


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

data class Jedi(val name: String, val age: Int)

/**
 * Module that just registers the root path / and replies with a text.
 */
fun Application.module() {
    install(DIFeature) {
        bind<Random> { singleton { SecureRandom() } }

        bind<MongoClient> { singleton { KMongo.createClient("mongodb://localhost:27027") }}
        bind<MongoDatabase> { singleton { this.instance<MongoClient>().getDatabase("test") } }
        bind<MongoCollection<Jedi>> { singleton { this.instance<MongoDatabase>().getCollection<Jedi>() } }
    }
    // This adds automatically Date and Server headers to each response, and would allow you to configure
    // additional headers served to each response.
    install(DefaultHeaders)

    install(ContentNegotiation) {
        jackson()
    }
    install(Sessions) {
        cookie<UserSession>("user_session", storage = RedisSessionStorage()) {
            serializer = JacksonSerializer(UserSession::class.java)
        }
    }

    // can use kodein here
    routing {
        val collection by closestDI().instance<MongoCollection<Jedi>>()

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

        get("/random") {
            val random by closestDI().instance<Random>()
            call.respond(random.nextInt())
        }

        post("/mongo") {
            log.info("Got mongo collection $collection")
            collection.insertOne(Jedi("Luke Skywalker", 19))
            call.respond(HttpStatusCode.OK)
        }

        get("/mongo") {
            val yoda : Jedi? = collection.findOne(Jedi::name eq "Luke Skywalker")
            if (yoda == null) {
                call.respond(HttpStatusCode.Gone)
            } else {
                call.respond(yoda)
            }
        }
    }
}
