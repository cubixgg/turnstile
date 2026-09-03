package dev.tobifrosch.turnstile.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobifrosch.turnstile.cache.TurnstileCache;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GateServiceTest {

    @Test
    void allowsAnyServerWhenNoPrefixConfigured() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of(), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.ALLOWED, gate.check("lobby", perm -> false));
    }

    @Test
    void deniesServerNotMatchingPrefix() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of(), "smp_");
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.DENIED_PREFIX, gate.check("lobby", perm -> true));
    }

    @Test
    void prefixCheckIsCaseInsensitive() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of(), "SMP_");
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.ALLOWED, gate.check("smp_building-1", perm -> true));
    }

    @Test
    void deniesWhenTaskMatchesAndPlayerLacksPermission() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of("building", "task.building"), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.DENIED_PERMISSION, gate.check("building-1", perm -> false));
    }

    @Test
    void allowsWhenTaskMatchesAndPlayerHasPermission() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of("building", "task.building"), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.ALLOWED,
            gate.check("building-1", "task.building"::equals));
    }

    @Test
    void allowsWhenNoTaskMatchesServer() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of("building", "task.building"), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.ALLOWED, gate.check("survival-1", perm -> false));
    }

    @Test
    void taskMatchingHasNoSeparatorRequirement() {
        // Deliberate per spec §3: "building" also matches "buildingXYZ", not just "building-1".
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of("building", "task.building"), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.DENIED_PERMISSION, gate.check("buildingXYZ", perm -> false));
    }

    @Test
    void longestMatchingTaskWinsOnOverlap() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of(
            "build", "task.build",
            "building", "task.building"
        ), null);
        GateService gate = new GateService(cache);

        // Only holds the broader "build" permission — the more specific "building" task
        // requirement must be the one enforced.
        assertEquals(GateService.GateResult.DENIED_PERMISSION,
            gate.check("building-1", "task.build"::equals));
        assertEquals(GateService.GateResult.ALLOWED,
            gate.check("building-1", "task.building"::equals));
    }

    @Test
    void taskMatchingIsCaseInsensitive() {
        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(Map.of("Building", "task.building"), null);
        GateService gate = new GateService(cache);

        assertEquals(GateService.GateResult.DENIED_PERMISSION, gate.check("BUILDING-1", perm -> false));
    }
}
