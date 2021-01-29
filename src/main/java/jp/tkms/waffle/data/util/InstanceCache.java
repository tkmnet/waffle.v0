package jp.tkms.waffle.data.util;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class InstanceCache<T> {
  private Cache<String, T> cacheStore;

  public InstanceCache(Class type, int size) {
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);

    cacheStore = cacheManager.createCache(type.getName(),
      CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, type,
        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(size, MemoryUnit.MB).build())
        .withExpiry(ExpiryPolicyBuilder.noExpiration()).build());
  }

  public InstanceCache(Class type, int size, int sec) {
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);

    cacheStore = cacheManager.createCache(type.getName(),
      CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, type,
        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(size, MemoryUnit.MB).build())
        .withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofSeconds(sec))).build());
  }

  public Cache<String, T> getCacheStore() {
    return cacheStore;
  }
}
