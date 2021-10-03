package io.ktor.samples.sandbox

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

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