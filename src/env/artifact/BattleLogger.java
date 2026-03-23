package artifact;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import model.Position;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central battle logger for the LLM-vs-MAS experiment.
 *
 * Writes three files per run under logs/:
 *   battle_{runId}_events.csv   – timestamped event stream
 *   battle_{runId}_llm.jsonl    – one JSON object per LLM call
 *   battle_{runId}_summary.json – final summary with all statistics
 *
 * Designed to support the article question:
 *   "When does the LLM correctly identify danger? When does it get overconfident?
 *    Does it develop a strategy across the battle?"
 */
public class BattleLogger {

    private static BattleLogger instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    // ── Identity ───────────────────────────────────────────────────────────────
    private final String runId;
    private final long startMs;

    // ── Writers ────────────────────────────────────────────────────────────────
    private PrintWriter eventWriter;
    private PrintWriter llmWriter;

    // ── Counters ───────────────────────────────────────────────────────────────
    private int sentinelsKilled       = 0;
    private int counterAttacksRecvd   = 0;
    private int totalDamageTaken      = 0;
    private int totalLlmCalls         = 0;
    private int safeChoices           = 0;   // LLM targeted isolated sentinel
    private int dangerousChoices      = 0;   // LLM targeted crowded position
    private int fallbackDecisions     = 0;   // Gemini unavailable, used heuristic
    private int initialSentinels      = 0;

    // ── Reasoning history (for temporal analysis) ──────────────────────────────
    private final List<String> reasoningLog = new ArrayList<>();

    // ── Decision safety history (first/second half comparison) ─────────────────
    private final List<Boolean> safetyTimeline = new ArrayList<>();

    private BattleLogger() {
        runId  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        startMs = System.currentTimeMillis();

        try {
            Files.createDirectories(Paths.get("logs"));

            eventWriter = new PrintWriter(new FileWriter("logs/battle_" + runId + "_events.csv"));
            eventWriter.println("elapsed_ms,event_type,wasp_health,wasp_x,wasp_y,sentinel_count,detail");

            llmWriter = new PrintWriter(new FileWriter("logs/battle_" + runId + "_llm.jsonl"));

        } catch (IOException e) {
            System.err.println("[BattleLogger] Could not open log files: " + e.getMessage());
        }

        // Flush logs on JVM shutdown even if battle ends abnormally
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));

        System.out.println("[BattleLogger] ── New run: " + runId
                         + " | label: " + RunConfig.getInstance().getRunLabel()
                         + " ──");
        System.out.println("[BattleLogger] Logs → logs/battle_" + runId + "_*");
    }

    public static synchronized BattleLogger getInstance() {
        if (instance == null) instance = new BattleLogger();
        return instance;
    }

    public String getRunId() { return runId; }

    // ══════════════════════════════════════════════════════════════════════════
    // Public logging API
    // ══════════════════════════════════════════════════════════════════════════

    /** Call once when the battle loop is about to start. */
    public synchronized void logBattleStart(int initialSentinelCount, int waspHealth) {
        this.initialSentinels = initialSentinelCount;
        writeEvent("BATTLE_START", waspHealth, 0, 0, initialSentinelCount,
                   "config=" + GSON_COMPACT.toJson(RunConfig.getInstance().asMap()));
        System.out.println("[BattleLogger] BATTLE START – sentinels=" + initialSentinelCount
                         + "  wasp_hp=" + waspHealth);
    }

    /**
     * Call after every LLM (or fallback) strategy decision.
     *
     * @param waspPos           current wasp position
     * @param sentinelPositions all sentinel positions at decision time
     * @param rawResponse       full JSON string returned by Gemini API (null for fallback)
     * @param targetX           chosen target X
     * @param targetY           chosen target Y
     * @param reasoning         short reasoning string extracted from LLM response
     * @param isFallback        true if Gemini was unavailable and heuristic was used
     */
    public synchronized void logLlmDecision(
            Position waspPos,
            List<Position> sentinelPositions,
            String rawResponse,
            int targetX, int targetY,
            String reasoning,
            boolean isFallback) {

        totalLlmCalls++;
        if (isFallback) fallbackDecisions++;
        if (reasoning != null && !reasoning.isBlank()) reasoningLog.add(reasoning);

        // ── Decision quality analysis ─────────────────────────────────────────
        int near50  = countWithinRadius(sentinelPositions, targetX, targetY, 50);
        int near100 = countWithinRadius(sentinelPositions, targetX, targetY, 100);
        // "Safe" = at most 2 to kill within 50px, AND fewer than 2 total within 100px
        boolean isSafe = near50 <= 2 && near100 < RunConfig.getInstance().getCounterAttackThreshold();
        safetyTimeline.add(isSafe);
        if (isSafe) safeChoices++; else dangerousChoices++;

        // ── Console summary ───────────────────────────────────────────────────
        System.out.printf("[BattleLogger] LLM #%d %s → target(%d,%d) near50=%d near100=%d safe=%s | %s%n",
                totalLlmCalls,
                isFallback ? "[FALLBACK]" : "[GEMINI]",
                targetX, targetY, near50, near100, isSafe,
                reasoning != null ? reasoning : "(no reasoning)");

        if (!isSafe)
            System.out.println("[BattleLogger]   ⚠ OVERCONFIDENT CHOICE – counter-attack risk!");

        // ── Write JSONL record ────────────────────────────────────────────────
        if (llmWriter == null) return;

        JsonObject entry = new JsonObject();
        entry.addProperty("run_id",      runId);
        entry.addProperty("call_index",  totalLlmCalls);
        entry.addProperty("elapsed_ms",  elapsed());
        entry.addProperty("is_fallback", isFallback);

        JsonObject wp = new JsonObject();
        wp.addProperty("x", waspPos.getX()); wp.addProperty("y", waspPos.getY());
        entry.add("wasp_pos", wp);

        entry.addProperty("sentinel_count", sentinelPositions.size());

        JsonArray sentArr = new JsonArray();
        for (Position p : sentinelPositions) {
            JsonObject sp = new JsonObject();
            sp.addProperty("x", p.getX()); sp.addProperty("y", p.getY());
            sentArr.add(sp);
        }
        entry.add("sentinel_positions", sentArr);

        JsonObject tgt = new JsonObject();
        tgt.addProperty("x", targetX); tgt.addProperty("y", targetY);
        entry.add("chosen_target", tgt);

        entry.addProperty("sentinels_near_target_50px",  near50);
        entry.addProperty("sentinels_near_target_100px", near100);
        entry.addProperty("is_safe_choice",   isSafe);
        entry.addProperty("reasoning",        reasoning != null ? reasoning : "");
        entry.addProperty("raw_response",     rawResponse != null ? rawResponse : "");

        llmWriter.println(GSON_COMPACT.toJson(entry));
        llmWriter.flush();

        // Also write compact event row
        String detail = "call=" + totalLlmCalls
                      + " target=(" + targetX + "," + targetY + ")"
                      + " near50=" + near50
                      + " near100=" + near100
                      + " safe=" + isSafe
                      + " fallback=" + isFallback;
        writeEvent("LLM_DECISION", -1, waspPos.getX(), waspPos.getY(),
                   sentinelPositions.size(), detail);
    }

    /** Call when wasp kills one or more sentinels. */
    public synchronized void logKills(List<String> killedIds,
                                      int waspHealth, int waspX, int waspY,
                                      int remainingSentinels) {
        sentinelsKilled += killedIds.size();
        writeEvent("SENTINEL_KILLED", waspHealth, waspX, waspY, remainingSentinels,
                   "killed=" + killedIds + " total_killed=" + sentinelsKilled);
        System.out.println("[BattleLogger] KILL ×" + killedIds.size()
                         + " → remaining=" + remainingSentinels
                         + "  wasp_hp=" + waspHealth);
    }

    /** Call when sentinels successfully counter-attack the wasp. */
    public synchronized void logCounterAttack(int damage, int waspHealthAfter,
                                              int waspX, int waspY,
                                              int sentinelCount, int attackerCount) {
        counterAttacksRecvd++;
        totalDamageTaken += damage;
        writeEvent("COUNTER_ATTACK", waspHealthAfter, waspX, waspY, sentinelCount,
                   "damage=" + damage + " attackers=" + attackerCount
                   + " hp_after=" + waspHealthAfter);
        System.out.println("[BattleLogger] COUNTER-ATTACK ×" + attackerCount
                         + "  damage=" + damage + "  wasp_hp=" + waspHealthAfter);
    }

    /** Call at the very end of the battle. Writes summary JSON. */
    public synchronized void logBattleEnd(boolean waspWon,
                                          int waspFinalHealth,
                                          int remainingSentinels) {
        String outcome = waspWon ? "WASP_WIN" : "SENTINEL_WIN";
        writeEvent(outcome, waspFinalHealth, 0, 0, remainingSentinels,
                   "duration_ms=" + elapsed());

        writeSummary(outcome, waspFinalHealth, remainingSentinels);

        System.out.println("[BattleLogger] ══ BATTLE END: " + outcome + " ══");
        System.out.println("[BattleLogger]   Duration      : " + elapsed() + " ms");
        System.out.println("[BattleLogger]   Kills         : " + sentinelsKilled);
        System.out.println("[BattleLogger]   LLM calls     : " + totalLlmCalls
                         + "  (safe=" + safeChoices + "  dangerous=" + dangerousChoices
                         + "  fallback=" + fallbackDecisions + ")");
        System.out.println("[BattleLogger]   Damage taken  : " + totalDamageTaken
                         + "  (" + counterAttacksRecvd + " counter-attacks)");
        System.out.println("[BattleLogger]   Logs written  : logs/battle_" + runId + "_*");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private long elapsed() { return System.currentTimeMillis() - startMs; }

    private synchronized void writeEvent(String type,
                                         int waspHealth, int waspX, int waspY,
                                         int sentinelCount, String detail) {
        if (eventWriter == null) return;
        // Escape commas/quotes in detail
        String safe = "\"" + detail.replace("\"", "'") + "\"";
        eventWriter.printf("%d,%s,%d,%d,%d,%d,%s%n",
                elapsed(), type, waspHealth, waspX, waspY, sentinelCount, safe);
        eventWriter.flush();
    }

    private void writeSummary(String outcome, int waspFinalHealth, int remainingSentinels) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("run_id",   runId);
            root.addProperty("outcome",  outcome);
            root.addProperty("battle_duration_ms", elapsed());

            root.add("config", GSON_COMPACT.toJsonTree(RunConfig.getInstance().asMap()));

            // ── Statistics ────────────────────────────────────────────────────
            JsonObject stats = new JsonObject();
            stats.addProperty("initial_sentinels",       initialSentinels);
            stats.addProperty("sentinels_killed",        sentinelsKilled);
            stats.addProperty("remaining_sentinels",     remainingSentinels);
            stats.addProperty("wasp_final_health",       waspFinalHealth);
            stats.addProperty("wasp_max_health",         RunConfig.getInstance().getWaspHealth());
            stats.addProperty("counter_attacks_received",counterAttacksRecvd);
            stats.addProperty("total_damage_taken",      totalDamageTaken);
            stats.addProperty("total_llm_calls",         totalLlmCalls);
            stats.addProperty("llm_safe_choices",        safeChoices);
            stats.addProperty("llm_dangerous_choices",   dangerousChoices);
            stats.addProperty("llm_fallback_decisions",  fallbackDecisions);
            double safePct = totalLlmCalls > 0 ? 100.0 * safeChoices / totalLlmCalls : 0.0;
            stats.addProperty("llm_safe_choice_pct",     Math.round(safePct * 10.0) / 10.0);
            root.add("stats", stats);

            // ── Temporal analysis ─────────────────────────────────────────────
            JsonObject temporal = new JsonObject();
            if (safetyTimeline.size() >= 4) {
                int half = safetyTimeline.size() / 2;
                long firstSafe  = safetyTimeline.subList(0, half).stream().filter(b -> b).count();
                long secondSafe = safetyTimeline.subList(half, safetyTimeline.size()).stream().filter(b -> b).count();
                temporal.addProperty("first_half_safe",  firstSafe);
                temporal.addProperty("first_half_total", half);
                temporal.addProperty("second_half_safe", secondSafe);
                temporal.addProperty("second_half_total",safetyTimeline.size() - half);
                String trend = secondSafe > firstSafe ? "IMPROVING"
                             : secondSafe < firstSafe ? "DEGRADING" : "STABLE";
                temporal.addProperty("strategy_trend", trend);
            }
            root.add("temporal_analysis", temporal);

            // ── Reasoning log ─────────────────────────────────────────────────
            JsonArray rArr = new JsonArray();
            for (String r : reasoningLog) rArr.add(r);
            root.add("llm_reasoning_log", rArr);

            try (PrintWriter w = new PrintWriter(
                    new FileWriter("logs/battle_" + runId + "_summary.json"))) {
                w.println(GSON.toJson(root));
            }
        } catch (IOException e) {
            System.err.println("[BattleLogger] Failed to write summary: " + e.getMessage());
        }
    }

    private int countWithinRadius(List<Position> positions, int cx, int cy, int r) {
        int n = 0;
        for (Position p : positions) {
            double d = Math.sqrt(Math.pow(p.getX() - cx, 2) + Math.pow(p.getY() - cy, 2));
            if (d <= r) n++;
        }
        return n;
    }

    private void close() {
        if (eventWriter != null) { eventWriter.flush(); eventWriter.close(); }
        if (llmWriter  != null) { llmWriter.flush();  llmWriter.close();  }
    }
}
