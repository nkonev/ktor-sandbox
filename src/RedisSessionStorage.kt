package io.ktor.samples.sandbox

import redis.clients.jedis.JedisPool

class RedisSessionStorage(private val jedisPool: JedisPool) : SimplifiedSessionStorage() {

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