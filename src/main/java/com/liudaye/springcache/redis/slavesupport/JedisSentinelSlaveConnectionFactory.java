package com.liudaye.springcache.redis.slavesupport;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.util.Pool;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 */
public class JedisSentinelSlaveConnectionFactory extends JedisConnectionFactory {
    public JedisSentinelSlaveConnectionFactory() {
    }

    public JedisSentinelSlaveConnectionFactory(JedisShardInfo shardInfo) {
        super(shardInfo);
    }

    public JedisSentinelSlaveConnectionFactory(JedisPoolConfig poolConfig) {
        super(poolConfig);
    }

    public JedisSentinelSlaveConnectionFactory(RedisSentinelConfiguration sentinelConfig) {
        super(sentinelConfig);
    }

    public JedisSentinelSlaveConnectionFactory(RedisSentinelConfiguration sentinelConfig, JedisPoolConfig poolConfig) {
        super(sentinelConfig, poolConfig);
    }

    public JedisSentinelSlaveConnectionFactory(RedisClusterConfiguration clusterConfig) {
        super(clusterConfig);
    }

    public JedisSentinelSlaveConnectionFactory(RedisClusterConfiguration clusterConfig, JedisPoolConfig poolConfig) {
        super(clusterConfig, poolConfig);
    }

    private static final Method GET_TIMEOUT_METHOD;

    static {

        // We need to configure Jedis socket timeout via reflection since the method-name was changed between releases.
        Method setTimeoutMethodCandidate = ReflectionUtils.findMethod(JedisShardInfo.class, "setTimeout", int.class);
        if (setTimeoutMethodCandidate == null) {
            // Jedis V 2.7.x changed the setTimeout method to setSoTimeout
            setTimeoutMethodCandidate = ReflectionUtils.findMethod(JedisShardInfo.class, "setSoTimeout", int.class);
        }

        Method getTimeoutMethodCandidate = ReflectionUtils.findMethod(JedisShardInfo.class, "getTimeout");
        if (getTimeoutMethodCandidate == null) {
            getTimeoutMethodCandidate = ReflectionUtils.findMethod(JedisShardInfo.class, "getSoTimeout");
        }

        GET_TIMEOUT_METHOD = getTimeoutMethodCandidate;
    }


    @Override
    protected Pool<Jedis> createRedisSentinelPool(RedisSentinelConfiguration config) {
        GenericObjectPoolConfig poolConfig = getPoolConfig() != null ? getPoolConfig() : new JedisPoolConfig();

        return new JedisSentinelSlavePool(config.getMaster().getName(), convertToJedisSentinelSet(config.getSentinels()),
                poolConfig, getTimeoutFrom(getShardInfo()),
                getShardInfo().getPassword(), getDatabase(), getClientName());
    }

    private Set<String> convertToJedisSentinelSet(Collection<RedisNode> nodes) {

        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptySet();
        }

        Set<String> convertedNodes = new LinkedHashSet<String>(nodes.size());
        for (RedisNode node : nodes) {
            if (node != null) {
                convertedNodes.add(node.asString());
            }
        }
        return convertedNodes;
    }

    private int getTimeoutFrom(JedisShardInfo shardInfo) {
        return (Integer) ReflectionUtils.invokeMethod(GET_TIMEOUT_METHOD, shardInfo);
    }
}
