package com.pixous.hrportal.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caching, backed by Redis.
 *
 * <p>Redis was already running and already on the classpath, and nothing in the
 * application had ever put a value in it — every page load re-read the same
 * unchanging lists (departments, teams, shifts, offices, holidays, settings) from
 * the database. That is the cost this removes. The live database is a shared host
 * that allows twenty connections in total, so a read that does not have to reach
 * it is worth more here than it would be on a database of our own.
 *
 * <p>Two things matter more than the speed:
 *
 * <p><b>Redis going away must never take the portal with it.</b> A cache is an
 * optimisation, so every failure to reach it is swallowed by
 * {@link #errorHandler()} and the request carries on to the database. If Redis is
 * not reachable at startup at all — which is the normal state on Windows hosting,
 * where there is no Redis to install — the manager falls back to an in-memory one
 * and the application behaves exactly as it did before this class existed.
 *
 * <p><b>Only data that does not go stale in a way anybody would notice is
 * cached.</b> Master data and settings, nothing else. Attendance, payroll, leave
 * and chat are read straight from the database every time, because a stale punch
 * or a stale payslip is a wrong answer, not a fast one. Each cache carries a TTL,
 * so even a missed eviction corrects itself within the minute counts below.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Master-data lists: departments, teams, shifts, sites, offices, dropdowns. */
    public static final String MASTERS = "masters";
    /** The holiday calendar, by year. */
    public static final String HOLIDAYS = "holidays";
    /** System settings read on nearly every page. */
    public static final String SETTINGS = "settings";

    /**
     * How long each cache may serve a value nobody has evicted. These are the
     * backstop, not the mechanism: every write path evicts what it changed, so a
     * TTL is only reached when something changed the data behind the
     * application's back — a manual UPDATE, or another instance.
     */
    private static final Map<String, Duration> TTLS = new LinkedHashMap<>();
    static {
        TTLS.put(MASTERS, Duration.ofMinutes(30));
        TTLS.put(HOLIDAYS, Duration.ofHours(6));
        TTLS.put(SETTINGS, Duration.ofMinutes(15));
    }

    /** Every key this application writes starts with this, so it shares a Redis safely. */
    private static final String KEY_PREFIX = "hrportal:";

    /** Set once the manager is built, so /api/cache/status can report the truth. */
    private static final AtomicBoolean USING_REDIS = new AtomicBoolean(false);

    public static boolean usingRedis() {
        return USING_REDIS.get();
    }

    public static Map<String, Duration> ttls() {
        return java.util.Collections.unmodifiableMap(TTLS);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${app.cache.enabled:true}") boolean enabled) {
        String[] names = TTLS.keySet().toArray(new String[0]);

        if (!enabled) {
            log.info("Caching is switched off (app.cache.enabled=false) — every read goes to the database.");
            return new ConcurrentMapCacheManager(names);
        }

        if (!reachable(connectionFactory)) {
            // Not an error. There is no Redis on Windows hosting, and the portal is
            // expected to run there. One instance caching in its own heap is still
            // faster than no cache, and correct as long as it is the only instance.
            //
            // Written without an em dash on purpose: the Windows console reads this
            // log as ANSI and turns one into a question mark.
            log.warn("Redis is not reachable - caching in this process's memory instead. "
                    + "Set REDIS_HOST/REDIS_PORT to use a shared cache.");
            return new ConcurrentMapCacheManager(names);
        }

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper())))
                // A cached null would answer "this team does not exist" long after
                // somebody created it. Better to miss and ask the database.
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        TTLS.forEach((name, ttl) -> perCache.put(name, base.entryTtl(ttl)));

        USING_REDIS.set(true);
        log.info("Caching in Redis. Caches: {}", TTLS);
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(perCache)
                // Without this a write path's eviction would be queued until its
                // transaction commits, and a read inside the same transaction would
                // still see the old value.
                .transactionAware()
                .build();
    }

    /**
     * A mapper for cached values only.
     *
     * <p>Deliberately not a bean: Spring Boot's own {@code ObjectMapper} decides
     * what every API response looks like, and replacing it to suit the cache would
     * change the shape of the entire API.
     *
     * <p>Type information is written into each value because a cache reads back
     * {@code Object} and has to know what it was. The validator restricts that to
     * this application's own classes and the JDK, so a value planted in Redis by
     * anything else cannot name an arbitrary class to instantiate.
     */
    private static ObjectMapper cacheMapper() {
        ObjectMapper m = new ObjectMapper();
        // LocalDate/LocalDateTime as ISO strings rather than as arrays of numbers.
        m.registerModule(new JavaTimeModule());
        m.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Entities are read back through their fields; not every one has a setter.
        m.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        m.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.pixous.hrportal.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.math.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return m;
    }

    /**
     * Is there a Redis at the configured address?
     *
     * <p>Asked more than once, because the answer decides the cache for the whole
     * lifetime of the process and a single attempt gets it wrong. The first attempt
     * happens while the application is at its busiest — Hibernate is still building
     * its metamodel — and on a loaded machine a one-second connect can time out
     * against a Redis that is running perfectly well on the same host. That
     * downgrade is then permanent and silent, which is worse than the delay of
     * asking again.
     */
    private static boolean reachable(RedisConnectionFactory factory) {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (RedisConnection c = factory.getConnection()) {
                c.ping();
                return true;
            } catch (Exception e) {
                last = e;
                log.debug("Redis ping attempt {} of 3 failed: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(400L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last != null) log.debug("Redis unreachable after 3 attempts: {}", last.getMessage());
        return false;
    }

    /**
     * Every cache failure is logged and ignored.
     *
     * <p>This is the whole reason adding a cache is safe. Redis restarting, its
     * disk filling, a value written by an older version of a class — none of it can
     * turn into a failed login or a failed punch, because a cache that cannot
     * answer is indistinguishable from a cache that has nothing, and the
     * application already knows how to handle that: it asks the database.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed ({} / {}) — reading from the database. {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed ({} / {}) — the answer is still correct. {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                // The one case worth a louder line: a failed eviction is the only way
                // a stale value can survive, and it survives only until the TTL.
                log.warn("Cache eviction failed ({} / {}) — a stale value may be served "
                        + "until its TTL expires. {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed ({}). {}", cache.getName(), e.getMessage());
            }
        };
    }
}
