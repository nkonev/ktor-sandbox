package io.ktor.samples.sandbox

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
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.kodein.di.*
import org.kodein.di.bindings.*
import org.kodein.di.ktor.DIFeature
import org.kodein.di.ktor.closestDI
import org.litote.kmongo.KMongo
import java.security.SecureRandom
import java.util.*
import org.litote.kmongo.* //NEEDED! import KMongo extensions
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

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

data class Jedi(val name: String, val age: Int)

/**
 * Module that just registers the root path / and replies with a text.
 */
fun Application.module() {
    val env = environment.config.propertyOrNull("ktor.custom")?.getString()
    log.info("Got custom variable $env")
    install(DIFeature) {
        val cleseableScope = scope as NoScope
        bind<NoScope> { singleton { cleseableScope } }

        bind<Random> { singleton { SecureRandom() } }

        bind<MongoClient> { singleton { KMongo.createClient("mongodb://localhost:27027") }}
        bind<MongoDatabase> { singleton { this.instance<MongoClient>().getDatabase("test") } }
        bind<MongoCollection<Jedi>> { singleton { this.instance<MongoDatabase>().getCollection<Jedi>() } }
        bind<JedisPool> { scoped(cleseableScope).singleton {
            val jedisPoolConfig: GenericObjectPoolConfig<Jedis> = GenericObjectPoolConfig<Jedis>()
            // TODO timeouts

            log.info("Configuring redis pool")
            val closeLoggingPool = object : JedisPool(jedisPoolConfig, "localhost", 36379), ScopeCloseable {
                override fun close() {
                    log.info("Closing redis pool")
                    super.close()
                }
            }

            return@singleton closeLoggingPool
        } }
    }

    environment.monitor.subscribe(ApplicationStopping) {
        log.info("Application is stopping")
        val instance by closestDI().instance<NoScope>()
        val standardScopeRegistry = instance.getRegistry(null)
        standardScopeRegistry.close()
    }

    // This adds automatically Date and Server headers to each response, and would allow you to configure
    // additional headers served to each response.
    install(DefaultHeaders)

    install(ContentNegotiation) {
        jackson()
    }
    install(Sessions) {
        val redisPool by closestDI().on(Any()).instance<JedisPool>()

        cookie<UserSession>("user_session", storage = RedisSessionStorage(redisPool)) {
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
