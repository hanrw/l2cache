package com.coy.l2cache.spring;

import com.coy.l2cache.cache.CacheExpiredListener;
import com.coy.l2cache.cache.DefaultCacheExpiredListener;
import com.coy.l2cache.builder.CacheBuilder;
import com.coy.l2cache.config.CacheConfig;
import com.coy.l2cache.provider.CacheSupport;
import com.coy.l2cache.spi.ServiceLoader;
import com.coy.l2cache.sync.CacheSyncPolicy;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * L2Cache Cache Manager
 *
 * @author chenck
 * @date 2020/7/6 11:18
 */
public class L2CacheCacheManager implements CacheManager {

    // 缓存Map<cacheName, Cache>
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

    private static final CacheExpiredListener defaultCacheExpiredListener = new DefaultCacheExpiredListener();

    /**
     * 是否支持动态创建缓存
     */
    private boolean dynamic = true;

    private final CacheConfig cacheConfig;

    private CacheExpiredListener expiredListener;

    private CacheSyncPolicy cacheSyncPolicy;

    private Object actualCacheClient;

    public L2CacheCacheManager(CacheConfig cacheConfig) {
        this(cacheConfig, null, defaultCacheExpiredListener);
    }

    public L2CacheCacheManager(CacheConfig cacheConfig, CacheSyncPolicy cacheSyncPolicy, CacheExpiredListener expiredListener) {
        this.dynamic = cacheConfig.isDynamic();
        this.cacheConfig = cacheConfig;
        if (null == expiredListener) {
            this.expiredListener = defaultCacheExpiredListener;
        } else {
            this.expiredListener = expiredListener;
        }
        this.cacheSyncPolicy = cacheSyncPolicy;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = this.cacheMap.get(name);
        if (cache == null && this.dynamic) {
            synchronized (this.cacheMap) {
                cache = this.cacheMap.get(name);
                if (cache == null) {
                    cache = createL2CacheSpringCache(cacheConfig.getCacheType(), name);
                    this.cacheMap.put(name, cache);
                }
            }
        }
        return cache;
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }

    /**
     * Create a new L2CacheSpringCache instance for the specified cache name.
     *
     * @param cacheName the name of the cache
     * @return the Spring L2CacheSpringCache adapter (or a decorator thereof)
     */
    protected Cache createL2CacheSpringCache(String cacheType, String cacheName) {
        com.coy.l2cache.cache.Cache cache = this.getL2CacheInstance(cacheType, cacheName);
        return new L2CacheSpringCache(cacheName, cacheConfig, cache);
    }

    /**
     * get or create l2cache
     */
    private com.coy.l2cache.cache.Cache getL2CacheInstance(String cacheType, String cacheName) {
        com.coy.l2cache.cache.Cache cache = CacheSupport.getCache(cacheType, cacheName);
        if (null != cache) {
            return cache;
        }
        // 基于SPI机制构建CacheBuilder
        CacheBuilder cacheBuilder = ServiceLoader.load(CacheBuilder.class, cacheType);
        cacheBuilder.setCacheConfig(this.cacheConfig);
        cacheBuilder.setExpiredListener(this.expiredListener);
        cacheBuilder.setCacheSyncPolicy(this.cacheSyncPolicy);
        cacheBuilder.setActualCacheClient(this.actualCacheClient);

        return CacheSupport.getCache(cacheType, cacheName, cacheBuilder);
    }

    public CacheExpiredListener getExpiredListener() {
        return expiredListener;
    }

    public void setExpiredListener(CacheExpiredListener expiredListener) {
        this.expiredListener = expiredListener;
    }

    public CacheSyncPolicy getCacheSyncPolicy() {
        return cacheSyncPolicy;
    }

    public void setCacheSyncPolicy(CacheSyncPolicy cacheSyncPolicy) {
        this.cacheSyncPolicy = cacheSyncPolicy;
    }

    public Object getActualCacheClient() {
        return actualCacheClient;
    }

    public void setActualCacheClient(Object actualCacheClient) {
        this.actualCacheClient = actualCacheClient;
    }
}