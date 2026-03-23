// WaspArtifact.java - CArtAgO Artifact for Wasp Agent
package artifact;

import java.util.List;

import cartago.Artifact;
import cartago.INTERNAL_OPERATION;
import cartago.OPERATION;
import cartago.ObsProperty;
import graphic.Environment;

import model.Position;
import model.Wasp;

/**
 * CArtAgO Artifact for the LLM-powered Wasp agent.
 *
 * All tunable values (speed, radii, damage) are read from RunConfig so they
 * can be changed via run-config.properties between experiments without recompiling.
 * BattleLogger records every significant event for post-run analysis.
 */
public class WaspArtifact extends Artifact {

    private Wasp wasp;
    private GeminiService geminiService;
    private BattleLogger logger;
    private RunConfig cfg;

    private int targetX = -1;
    private int targetY = -1;
    private String lastReasoning = "";
    private boolean battleActive = true;

    void init() {
        wasp         = Wasp.getInstance();
        geminiService = GeminiService.getInstance();
        logger       = BattleLogger.getInstance();
        cfg          = RunConfig.getInstance();

        defineObsProperty("wasp_position", wasp.getPosition().getX(), wasp.getPosition().getY());
        defineObsProperty("wasp_health",   wasp.getHealth());
        defineObsProperty("wasp_alive",    wasp.isAlive());
        defineObsProperty("attack_target", -1, -1, "none");
        defineObsProperty("sentinel_count", 0);
        defineObsProperty("battle_active", true);

        execInternalOp("delayedInit");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    @INTERNAL_OPERATION
    void delayedInit() {
        int maxWait = 30;
        for (int waited = 0; waited < maxWait; waited++) {
            try {
                if (graphic.EnvironmentApplication.getInstance() != null) {
                    await_time(2000);
                    Environment.getInstance().registerWasp(wasp);
                    execInternalOp("battleLoop");
                    return;
                }
            } catch (Exception ignored) {}
            await_time(1000);
        }
        System.err.println("[WaspArtifact] ERROR: JavaFX did not initialise in time.");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Agent-callable operations (used by wasp.asl)
    // ──────────────────────────────────────────────────────────────────────────

    @OPERATION
    void scanSentinels() {
        List<Position> sentinels = Environment.getInstance().getSentinelPositions();
        getObsProperty("sentinel_count").updateValue(sentinels.size());
        if (sentinels.isEmpty()) {
            endBattle(true);
        }
    }

    @OPERATION
    void requestAttackTarget() {
        if (!wasp.isAlive() || !battleActive) return;

        List<Position> sentinels = Environment.getInstance().getSentinelPositions();
        if (sentinels.isEmpty()) {
            targetX = -1; targetY = -1; lastReasoning = "No targets";
            updateAttackTarget();
            return;
        }

        GeminiService.AttackDecision d = geminiService.getAttackStrategy(
                sentinels, wasp.getPosition(),
                Environment.getInstance().getWidth(),
                Environment.getInstance().getHeight());

        applyDecision(d, sentinels);
    }

    @OPERATION
    void moveToTarget() {
        if (!wasp.isAlive() || targetX < 0 || targetY < 0) return;
        wasp.moveToward(targetX, targetY, cfg.getWaspSpeed());
        Environment.getInstance().updateWaspPosition(wasp.getPosition());
        getObsProperty("wasp_position").updateValues(
                new Object[]{wasp.getPosition().getX(), wasp.getPosition().getY()});
    }

    @OPERATION
    void executeAttack() {
        if (!wasp.isAlive()) return;
        Position pos = wasp.getPosition();
        int attackR  = wasp.getAttackRadius();
        int counterR = cfg.getSentinelCounterRadius();
        int threshold = cfg.getCounterAttackThreshold();
        int damage   = cfg.getSentinelCounterDamage();

        List<String> killed = Environment.getInstance()
                .attackSentinelsInRadius(pos.getX(), pos.getY(), attackR, wasp.getMaxKillsPerAttack());
        if (!killed.isEmpty()) {
            int remaining = Environment.getInstance().getSentinelPositions().size();
            logger.logKills(killed, wasp.getHealth(), pos.getX(), pos.getY(), remaining);
        }

        int nearby = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), counterR);
        if (nearby >= threshold) {
            wasp.takeDamageAmount(damage);
            logger.logCounterAttack(damage, wasp.getHealth(), pos.getX(), pos.getY(),
                    Environment.getInstance().getSentinelPositions().size(), nearby);
            updateWaspHealthUI();
            if (!wasp.isAlive()) endBattle(false);
        }
    }

    @OPERATION
    void checkReachedTarget() {
        if (targetX < 0 || targetY < 0) return;
        if (wasp.distanceTo(new Position(targetX, targetY)) < 10) {
            signal("at_attack_position");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Main battle loop
    // ──────────────────────────────────────────────────────────────────────────

    @INTERNAL_OPERATION
    void battleLoop() {
        // ── Phase 1: wait for sentinels to register ──────────────────────────
        int initialCount = 0;
        for (int i = 0; i < 30; i++) {
            await_time(1000);
            List<Position> s = Environment.getInstance().getSentinelPositions();
            if (!s.isEmpty()) {
                await_time(2000); // extra time for all agents to register
                initialCount = Environment.getInstance().getSentinelPositions().size();
                break;
            }
        }

        if (initialCount == 0) {
            System.err.println("[WaspArtifact] No sentinels found. Battle aborted.");
            return;
        }

        logger.logBattleStart(initialCount, wasp.getHealth());
        System.out.println("[WaspArtifact] ===== BATTLE BEGINS: Wasp vs " + initialCount + " Sentinels =====");

        // ── Phase 2: main battle loop ────────────────────────────────────────
        int attackR  = wasp.getAttackRadius();
        int counterR = cfg.getSentinelCounterRadius();
        int threshold = cfg.getCounterAttackThreshold();
        int damage   = cfg.getSentinelCounterDamage();
        int speed    = cfg.getWaspSpeed();

        while (battleActive && wasp.isAlive()) {

            // Brief idle check (50 ms)
            await_time(50);
            if (!doIdleCheck(attackR, counterR, threshold, damage)) break;
            if (!wasp.isAlive() || !battleActive) break;

            // ── Refresh sentinel list ────────────────────────────────────────
            List<Position> sentinels = Environment.getInstance().getSentinelPositions();
            getObsProperty("sentinel_count").updateValue(sentinels.size());

            if (sentinels.isEmpty()) {
                endBattle(true);
                break;
            }

            // ── LLM / heuristic decision ─────────────────────────────────────
            GeminiService.AttackDecision decision = geminiService.getPrefetchedDecision();
            if (decision == null) {
                decision = geminiService.getAttackStrategy(
                        sentinels, wasp.getPosition(),
                        Environment.getInstance().getWidth(),
                        Environment.getInstance().getHeight());
            } else {
                System.out.println("[WaspArtifact] Using prefetched target – no LLM wait.");
            }

            applyDecision(decision, sentinels);

            // ── Move toward target ────────────────────────────────────────────
            double initDist = wasp.distanceTo(new Position(targetX, targetY));
            boolean prefetchStarted = false;
            int steps = 0;
            int counterCd = 0;
            int attackCd  = 0;

            while (wasp.distanceTo(new Position(targetX, targetY)) > 20
                    && steps < 150 && wasp.isAlive()) {

                wasp.moveToward(targetX, targetY, speed);
                Environment.getInstance().updateWaspPosition(wasp.getPosition());
                getObsProperty("wasp_position").updateValues(
                        new Object[]{wasp.getPosition().getX(), wasp.getPosition().getY()});

                await_time(50);
                steps++;
                counterCd++;
                attackCd++;

                Position pos = wasp.getPosition();
                int near50  = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), attackR);
                int near100 = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), counterR);

                // Wasp attacks isolated or paired sentinels
                if (near50 >= 1 && near50 <= 2 && attackCd >= 10) {
                    attackCd = 0;
                    List<String> killed = Environment.getInstance()
                            .attackSentinelsInRadius(pos.getX(), pos.getY(), attackR, wasp.getMaxKillsPerAttack());
                    if (!killed.isEmpty()) {
                        int remaining = Environment.getInstance().getSentinelPositions().size();
                        logger.logKills(killed, wasp.getHealth(), pos.getX(), pos.getY(), remaining);
                    }
                }

                // Sentinel counter-attack (every ~1 s, cooldown 20 steps × 50 ms)
                if (near100 >= threshold && counterCd >= 20) {
                    counterCd = 0;
                    wasp.takeDamageAmount(damage);
                    logger.logCounterAttack(damage, wasp.getHealth(),
                            pos.getX(), pos.getY(),
                            Environment.getInstance().getSentinelPositions().size(), near100);
                    updateWaspHealthUI();
                    if (!wasp.isAlive()) { endBattle(false); break; }
                }

                // Start prefetching next LLM decision when halfway to target
                double remaining = wasp.distanceTo(new Position(targetX, targetY));
                if (!prefetchStarted && remaining < initDist * 0.5) {
                    geminiService.prefetchNextStrategy(
                            Environment.getInstance().getSentinelPositions(),
                            wasp.getPosition(),
                            Environment.getInstance().getWidth(),
                            Environment.getInstance().getHeight());
                    prefetchStarted = true;
                }
            }

            if (!wasp.isAlive()) break;

            // ── Final attack at target location ───────────────────────────────
            Position pos = wasp.getPosition();
            int nearby = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), attackR);
            if (nearby >= 1) {
                List<String> killed = Environment.getInstance()
                        .attackSentinelsInRadius(pos.getX(), pos.getY(), attackR, wasp.getMaxKillsPerAttack());
                if (!killed.isEmpty()) {
                    int rem = Environment.getInstance().getSentinelPositions().size();
                    logger.logKills(killed, wasp.getHealth(), pos.getX(), pos.getY(), rem);
                }
            }
        }

        System.out.println("[WaspArtifact] Battle loop ended. Wasp alive: " + wasp.isAlive());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Apply a Gemini/fallback decision, log it, update observable properties. */
    private void applyDecision(GeminiService.AttackDecision d, List<Position> sentinels) {
        targetX = d.targetX;
        targetY = d.targetY;
        lastReasoning = d.reasoning;
        updateAttackTarget();

        logger.logLlmDecision(
                wasp.getPosition(), sentinels,
                d.rawResponse, d.targetX, d.targetY, d.reasoning,
                d.isFallback);
    }

    /** Quick idle check between decision cycles. Returns false if battle ended. */
    private boolean doIdleCheck(int attackR, int counterR, int threshold, int damage) {
        Position pos = wasp.getPosition();
        int near50  = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), attackR);
        int near100 = Environment.getInstance().countSentinelsInRadius(pos.getX(), pos.getY(), counterR);

        if (near50 >= 1 && near50 <= 2) {
            List<String> killed = Environment.getInstance()
                    .attackSentinelsInRadius(pos.getX(), pos.getY(), attackR, wasp.getMaxKillsPerAttack());
            if (!killed.isEmpty()) {
                int rem = Environment.getInstance().getSentinelPositions().size();
                logger.logKills(killed, wasp.getHealth(), pos.getX(), pos.getY(), rem);
            }
        }

        if (near100 >= threshold) {
            wasp.takeDamage(10); // percentage-based idle damage
            updateWaspHealthUI();
            if (!wasp.isAlive()) { endBattle(false); return false; }
        }
        return true;
    }

    private void endBattle(boolean waspWon) {
        if (!battleActive) return; // guard against double-call
        battleActive = false;
        getObsProperty("battle_active").updateValue(false);

        int remaining = Environment.getInstance().getSentinelPositions().size();
        logger.logBattleEnd(waspWon, wasp.getHealth(), remaining);

        if (waspWon) {
            System.out.println("[WaspArtifact] All sentinels eliminated! Wasp wins!");
            Environment.getInstance().declareWaspVictory();
        } else {
            Environment.getInstance().declareSentinelVictory();
        }
    }

    private void updateAttackTarget() {
        getObsProperty("attack_target").updateValues(new Object[]{targetX, targetY, lastReasoning});
    }

    private void updateWaspHealthUI() {
        getObsProperty("wasp_health").updateValue(wasp.getHealth());
        getObsProperty("wasp_alive").updateValue(wasp.isAlive());
        Environment.getInstance().updateWaspHealth(wasp.getHealth(), wasp.getMaxHealth());
    }
}
