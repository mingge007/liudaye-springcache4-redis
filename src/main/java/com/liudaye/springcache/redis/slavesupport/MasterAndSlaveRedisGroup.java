package com.liudaye.springcache.redis.slavesupport;

import org.springframework.data.redis.core.RedisOperations;

public class MasterAndSlaveRedisGroup {

    private final RedisOperations redisOperations;

    private final RedisOperations redisSlaveOperations;

    private Long expires = null;

    public MasterAndSlaveRedisGroup(RedisOperations redisOperations, RedisOperations redisSlaveOperations) {
        this.redisOperations = redisOperations;
        this.redisSlaveOperations = redisSlaveOperations;
    }

    public Long getExpires() {
        return expires;
    }

    public void setExpires(Long expires) {
        this.expires = expires;
    }

    public RedisOperations getRedisOperations() {
        return redisOperations;
    }

    public RedisOperations getRedisSlaveOperations() {
        return redisSlaveOperations;
    }
}
