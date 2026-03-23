package artifact;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Runtime configuration loaded from run-config.properties in the project root.
 * Edit that file to change parameters between runs without recompiling.
 * All values fall back to sensible defaults if the file is missing or a key is absent.
 */
public class RunConfig {

    private static RunConfig instance;
    private final Properties props = new Properties();

    private RunConfig() {
        String[] candidates = {"run-config.properties", "src/env/artifact/run-config.properties"};
        for (String path : candidates) {
            try (FileInputStream fis = new FileInputStream(path)) {
                props.load(fis);
                System.out.println("[RunConfig] Loaded from: " + path);
                return;
            } catch (IOException ignored) {}
        }
        System.out.println("[RunConfig] No run-config.properties found – using all defaults.");
    }

    public static synchronized RunConfig getInstance() {
        if (instance == null) instance = new RunConfig();
        return instance;
    }

    // ── Wasp ───────────────────────────────────────────────────────────────────
    public int getWaspHealth()       { return getInt("wasp.health", 200); }
    public int getWaspAttackRadius() { return getInt("wasp.attack.radius", 50); }
    public int getWaspMaxKills()     { return getInt("wasp.max.kills", 2); }
    public int getWaspSpeed()        { return getInt("wasp.speed", 3); }

    // ── Sentinel counter-attack ────────────────────────────────────────────────
    public int getSentinelCounterRadius()    { return getInt("sentinel.counter.radius", 100); }
    public int getSentinelCounterDamage()    { return getInt("sentinel.counter.damage", 20); }
    public int getCounterAttackThreshold()   { return getInt("sentinel.counter.threshold", 2); }

    // ── LLM ───────────────────────────────────────────────────────────────────
    public long getLlmRateLimitMs() { return getLong("llm.rate.limit.ms", 5000); }

    // ── Meta ──────────────────────────────────────────────────────────────────
    public String getRunLabel() { return props.getProperty("run.label", "default"); }

    /** Returns all config values as an ordered map suitable for JSON serialisation. */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_label",                getRunLabel());
        m.put("wasp_health",              getWaspHealth());
        m.put("wasp_attack_radius",       getWaspAttackRadius());
        m.put("wasp_max_kills",           getWaspMaxKills());
        m.put("wasp_speed",               getWaspSpeed());
        m.put("sentinel_counter_radius",  getSentinelCounterRadius());
        m.put("sentinel_counter_damage",  getSentinelCounterDamage());
        m.put("counter_attack_threshold", getCounterAttackThreshold());
        m.put("llm_rate_limit_ms",        getLlmRateLimitMs());
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int getInt(String key, int def) {
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private long getLong(String key, long def) {
        try { return Long.parseLong(props.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
