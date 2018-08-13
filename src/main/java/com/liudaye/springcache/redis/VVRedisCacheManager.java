package com.liudaye.springcache.redis;

import com.liudaye.springcache.redis.slavesupport.MasterAndSlaveRedisGroup;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.DefaultRedisCachePrefix;
import org.springframework.data.redis.cache.RedisCachePrefix;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CacheManager} implementation for Redis. By default saves the keys directly, without appending a prefix (which
 * acts as a namespace). To avoid clashes, it is recommended to change this (by setting 'usePrefix' to 'true'). <br>
 * By default {@link VVRedisCache}s will be lazily initialized for each {@link #getCache(String)} request unless a set of
 * predefined cache names is provided. <br>
 * <br>
 * Setting {@link #setTransactionAware(boolean)} to {@code true} will force Caches to be decorated as
 * {@link TransactionAwareCacheDecorator} so values will only be written to the cache after successful commit of
 * surrounding transaction.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Thomas Darimont
 */

/**
 * 改造自RedisCacheManager
 * 1、删除了loadRemote功能，因为此功能调用keys*命令；
 * 2、删除单一主库RedisOperations,增加默认主从库组；
 * 3、增加cacheName-RedisOperations的多配置的，主从库组；
 * 4、dynamic强制为true,因为原逻辑是按照如果存在指定库名则不会动态加载未知库；
 * 5、删除Map<String, Long> expires属性，不同库的默认过期时间依赖改到主从库组
 * 6、删除Set<String> configuredCacheNames属性
 * 7、增加默认有效期defaultExpiration
 *
 * @author liuhongming
 * @date 2018-08-10
 */
public class VVRedisCacheManager extends AbstractTransactionSupportingCacheManager {
    private final static Log logger = LogFactory.getLog(VVRedisCacheManager.class);

    private final MasterAndSlaveRedisGroup deFaultMasterAndSlaveRedisGroup;

    @SuppressWarnings("rawtypes") //
    private ConcurrentHashMap<String, MasterAndSlaveRedisGroup> masterAndSlaveRedisGroupMap = new ConcurrentHashMap<>(3);


    private boolean usePrefix = false;

    private RedisCachePrefix cachePrefix = new DefaultRedisCachePrefix();

    private boolean dynamic = true;

    // 0 - never expire
    private long defaultExpiration = 0;


    private final boolean cacheNullValues;

    /**
     * Construct a {@link VVRedisCacheManager}.
     *
     * @param masterAndSlaveRedisGroup
     */
    @SuppressWarnings("rawtypes")
    public VVRedisCacheManager(MasterAndSlaveRedisGroup masterAndSlaveRedisGroup) {
        this(masterAndSlaveRedisGroup, false);
    }

    /**
     * Construct a static {@link VVRedisCacheManager}, managing caches for the specified cache names only. <br />
     * <br />
     * <strong>NOTE</strong> When enabling {@code cacheNullValues} please make sure the {@link RedisSerializer} used by
     * {@link RedisOperations} is capable of serializing {@link NullValue}.
     *
     * @param masterAndSlaveRedisGroup {@link MasterAndSlaveRedisGroup} to work upon.
     * @param cacheNullValues          set to {@literal true} to allow caching {@literal null}.
     * @since 1.8
     */
    @SuppressWarnings("rawtypes")
    public VVRedisCacheManager(MasterAndSlaveRedisGroup masterAndSlaveRedisGroup, boolean cacheNullValues) {
        this.deFaultMasterAndSlaveRedisGroup = masterAndSlaveRedisGroup;
        this.cacheNullValues = cacheNullValues;
    }

    public void setUsePrefix(boolean usePrefix) {
        this.usePrefix = usePrefix;
    }

    /**
     * Sets the cachePrefix. Defaults to 'DefaultRedisCachePrefix').
     *
     * @param cachePrefix the cachePrefix to set
     */
    public void setCachePrefix(RedisCachePrefix cachePrefix) {
        this.cachePrefix = cachePrefix;
    }

    /**
     * Sets the default expire time (in seconds).
     *
     * @param defaultExpireTime time in seconds.
     */
    public void setDefaultExpiration(long defaultExpireTime) {
        this.defaultExpiration = defaultExpireTime;
    }


    /*
     * (non-Javadoc)
     * @see org.springframework.cache.support.AbstractCacheManager#loadCaches()
     */
    @Override
    protected Collection<? extends Cache> loadCaches() {

        Assert.notNull(this.deFaultMasterAndSlaveRedisGroup, "A redis template is required in order to interact with data store");

        Set<Cache> caches = new LinkedHashSet<Cache>(new ArrayList<Cache>());

        Set<String> cachesToLoad = new LinkedHashSet<String>(this.getConfigredCacheNames());
        cachesToLoad.addAll(this.getCacheNames());

        if (!CollectionUtils.isEmpty(cachesToLoad)) {

            for (String cacheName : cachesToLoad) {
                caches.add(createCache(cacheName));
            }
        }

        return caches;
    }

    /**
     * Returns a new {@link Collection} of {@link Cache} from the given caches collection and adds the configured
     * {@link Cache}s of they are not already present.
     *
     * @param caches must not be {@literal null}
     * @return
     */
    protected Collection<? extends Cache> addConfiguredCachesIfNecessary(Collection<? extends Cache> caches) {

        Assert.notNull(caches, "Caches must not be null!");

        Collection<Cache> result = new ArrayList<Cache>(caches);

        for (String cacheName : getCacheNames()) {

            boolean configuredCacheAlreadyPresent = false;

            for (Cache cache : caches) {

                if (cache.getName().equals(cacheName)) {
                    configuredCacheAlreadyPresent = true;
                    break;
                }
            }

            if (!configuredCacheAlreadyPresent) {
                result.add(getCache(cacheName));
            }
        }

        return result;
    }

    /**
     * Will no longer add the cache to the set of
     *
     * @param cacheName
     * @return
     * @deprecated since 1.8 - please use {@link #getCache(String)}.
     */
    @Deprecated
    protected Cache createAndAddCache(String cacheName) {

        Cache cache = super.getCache(cacheName);
        return cache != null ? cache : createCache(cacheName);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.cache.support.AbstractCacheManager#getMissingCache(java.lang.String)
     */
    @Override
    protected Cache getMissingCache(String name) {
        return this.dynamic ? createCache(name) : null;
    }

    @SuppressWarnings("unchecked")
    protected VVRedisCache createCache(String cacheName) {
        long expiration = computeExpiration(cacheName);
        return new VVRedisCache(cacheName, (usePrefix ? cachePrefix.prefix(cacheName) : null), getDeFaultMasterAndSlaveRedisGroup(cacheName).getRedisOperations(), getDeFaultMasterAndSlaveRedisGroup(cacheName).getRedisSlaveOperations(), expiration,
                cacheNullValues);
    }

    protected long computeExpiration(String name) {
        MasterAndSlaveRedisGroup masterAndSlaveRedisGroup = getDeFaultMasterAndSlaveRedisGroup(name);
        return (masterAndSlaveRedisGroup.getExpires() != null ? masterAndSlaveRedisGroup.getExpires().longValue() : defaultExpiration);
    }

    protected RedisCachePrefix getCachePrefix() {
        return cachePrefix;
    }

    protected boolean isUsePrefix() {
        return usePrefix;
    }

    /* (non-Javadoc)
    * @see
    org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager#decorateCache(org.springframework.cache.Cache)
    */
    @Override
    protected Cache decorateCache(Cache cache) {

        if (isCacheAlreadyDecorated(cache)) {
            return cache;
        }

        return super.decorateCache(cache);
    }

    protected boolean isCacheAlreadyDecorated(Cache cache) {
        return isTransactionAware() && cache instanceof TransactionAwareCacheDecorator;
    }

    public void setMasterAndSlaveRedisGroupMap(Map<String, MasterAndSlaveRedisGroup> masterAndSlaveRedisGroupMap) {
        this.masterAndSlaveRedisGroupMap = new ConcurrentHashMap<>(masterAndSlaveRedisGroupMap);
    }

    private MasterAndSlaveRedisGroup getDeFaultMasterAndSlaveRedisGroup(String cacheName) {
        MasterAndSlaveRedisGroup masterAndSlaveRedisGroup = masterAndSlaveRedisGroupMap.get(cacheName);
        if (masterAndSlaveRedisGroup == null) {
            return deFaultMasterAndSlaveRedisGroup;
        }
        return masterAndSlaveRedisGroup;
    }

    private Set<String> getConfigredCacheNames() {
        if (masterAndSlaveRedisGroupMap != null && masterAndSlaveRedisGroupMap.size() > 0) {
            Set<String> configuredCacheNames = new LinkedHashSet<>(4);
            Iterator<String> keyIterators = masterAndSlaveRedisGroupMap.keySet().iterator();
            while (keyIterators.hasNext()) {
                configuredCacheNames.add(keyIterators.next());
            }
            return configuredCacheNames;
        }
        return Collections.emptySet();
    }

}
