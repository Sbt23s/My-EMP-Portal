package com.pixous.hrportal.modules.org;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.config.CacheConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the cache is actually doing, and a way to empty it.
 *
 * <p>A cache that cannot be inspected is a cache nobody trusts: the reason this
 * exists is that Redis ran for months holding nothing, and there was no way to
 * tell from outside. This endpoint answers whether Redis is being used at all,
 * how many keys this application has put there, and what each cache holds.
 *
 * <p>The clear is for the one case a TTL handles too slowly — somebody changed
 * master data directly in the database, and the portal is still showing the old
 * list. It empties only this application's own keys.
 */
@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache", description = "Cache status and clearing")
public class CacheController {

    private final CacheManager cacheManager;
    private final RedisConnectionFactory redisConnectionFactory;

    public CacheController(CacheManager cacheManager, RedisConnectionFactory redisConnectionFactory) {
        this.cacheManager = cacheManager;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE')")
    @Operation(summary = "Whether Redis is in use, and what it is holding")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backend", CacheConfig.usingRedis() ? "redis" : "in-memory");
        out.put("manager", cacheManager.getClass().getSimpleName());
        out.put("caches", cacheManager.getCacheNames());

        Map<String, String> ttls = new LinkedHashMap<>();
        CacheConfig.ttls().forEach((name, ttl) -> ttls.put(name, ttl.toString()));
        out.put("expiresAfter", ttls);

        if (CacheConfig.usingRedis()) {
            try (RedisConnection c = redisConnectionFactory.getConnection()) {
                out.put("reachable", true);
                // Counted per cache so it is obvious which ones are being used and
                // which are sitting empty because nothing has asked for them yet.
                Map<String, Integer> keys = new LinkedHashMap<>();
                int total = 0;
                for (String name : CacheConfig.ttls().keySet()) {
                    int n = 0;
                    try (var cursor = c.keyCommands().scan(
                            org.springframework.data.redis.core.ScanOptions.scanOptions()
                                    .match("hrportal:" + name + "*").count(500).build())) {
                        while (cursor.hasNext()) {
                            cursor.next();
                            n++;
                        }
                    }
                    keys.put(name, n);
                    total += n;
                }
                out.put("keys", keys);
                out.put("totalKeys", total);
                try {
                    var info = c.serverCommands().info("memory");
                    if (info != null) {
                        out.put("redisMemoryUsed", info.getProperty("used_memory_human"));
                    }
                } catch (Exception ignored) {
                    // Managed Redis often forbids INFO. The key counts above are the
                    // part that matters, so losing this must not lose those.
                }
            } catch (Exception e) {
                // Reported rather than thrown: this endpoint exists to diagnose Redis,
                // so it must still answer when Redis is the thing that is broken.
                out.put("reachable", false);
                out.put("error", e.getMessage());
            }
        } else {
            out.put("reachable", false);
            out.put("note", "No Redis at this REDIS_HOST/REDIS_PORT, so each instance "
                    + "caches in its own memory. Correct, but not shared.");
        }
        return ApiResponse.ok(out);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ORG_MANAGE')")
    @Operation(summary = "Empty every cache (the next read comes from the database)")
    public ApiResponse<Void> clear() {
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
        return ApiResponse.message("Cache cleared — the next read of each list comes from the database.");
    }
}
