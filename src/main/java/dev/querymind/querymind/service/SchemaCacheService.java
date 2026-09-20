package dev.querymind.querymind.service;

import dev.querymind.querymind.dto.TableInfo;
import dev.querymind.querymind.model.SchemaCache;
import dev.querymind.querymind.repository.SchemaCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Caches the list of tables for a connection so we don't hit information_schema
 * on every single question. A cached copy is kept for 30 minutes (schemas
 * rarely change), stored as JSON in the schema_cache table.
 *
 * getTables() is the whole thing: return the cached list if it is still fresh,
 * otherwise load a fresh one (via the supplied loader), save it, and return it.
 */
@Service
public class SchemaCacheService {

    // How long a cached schema is considered fresh.
    private static final Duration TTL = Duration.ofMinutes(30);

    private final SchemaCacheRepository cacheRepository;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public SchemaCacheService(SchemaCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    public List<TableInfo> getTables(UUID connectionId, Supplier<List<TableInfo>> loader) {
        Optional<SchemaCache> existing = cacheRepository.findByConnectionId(connectionId);

        if (existing.isPresent() && isFresh(existing.get())) {
            List<TableInfo> cached = tryRead(existing.get().getSchemaJson());
            if (cached != null) {
                return cached;
            }
        }

        // Cache missing, stale or unreadable -> load fresh and save it.
        List<TableInfo> fresh = loader.get();
        store(connectionId, existing.orElse(null), fresh);
        return fresh;
    }

    // Drop the cached schema for a connection (used when it is deleted).
    public void evict(UUID connectionId) {
        cacheRepository.findByConnectionId(connectionId).ifPresent(cacheRepository::delete);
    }

    private boolean isFresh(SchemaCache cache) {
        return cache.getCachedAt().isAfter(Instant.now().minus(TTL));
    }

    private List<TableInfo> tryRead(String json) {
        try {
            return Arrays.asList(mapper.readValue(json, TableInfo[].class));
        } catch (Exception e) {
            return null; // bad JSON -> treat as a cache miss
        }
    }

    private void store(UUID connectionId, SchemaCache existing, List<TableInfo> tables) {
        try {
            SchemaCache row = (existing != null) ? existing : new SchemaCache();
            row.setConnectionId(connectionId);
            row.setSchemaJson(mapper.writeValueAsString(tables));
            row.setCachedAt(Instant.now());
            cacheRepository.save(row);
        } catch (Exception e) {
            // Caching is best-effort; if it fails we just don't cache this time.
        }
    }
}
