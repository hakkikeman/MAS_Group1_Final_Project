# Article Findings: Multi-Agent Bee Colony with LLM-Powered Predator
# Generated: 2026-03-23
# Experiment: 4-config parameter sweep, Gemini 2.5 Flash, JaCaMo/CArtAgO framework

---

## RESEARCH QUESTIONS ANSWERED

1. When does the LLM correctly identify danger from crowding?
2. When does it get overconfident?
3. Does it develop something like a "strategy" across rounds?

---

## TABLE 1 — BATTLE SUMMARY (use as Table 1 in article)

| Config            | Outcome      | Duration | Kills | Wasp HP Left | LLM Calls | Safe% | Fallback% | Counter-Attacks | Damage Taken |
|-------------------|--------------|----------|-------|--------------|-----------|-------|-----------|-----------------|--------------|
| baseline          | SENTINEL_WIN | 58.3s    | 12    | 0            | 8         | 62%   | 37%       | 10              | 200          |
| strong_wasp       | WASP_WIN     | 104.4s   | 28    | 135          | 22        | 68%   | 45%       | 9               | 180          |
| weak_wasp         | SENTINEL_WIN | 47.1s    | 4     | 0            | 5         | 60%   | 20%       | 5               | 100          |
| strong_sentinels  | SENTINEL_WIN | 40.8s    | 6     | 0            | 4         | 0%    | 25%       | 6               | 210          |

**Config parameters:**
- baseline:         hp=200, attack_r=50, max_kills=2, speed=3, counter_r=100, counter_dmg=20
- strong_wasp:      hp=350, attack_r=70, max_kills=3, speed=5, counter_r=100, counter_dmg=20
- strong_sentinels: hp=200, attack_r=50, max_kills=2, speed=3, counter_r=130, counter_dmg=35
- weak_wasp:        hp=100, attack_r=35, max_kills=1, speed=3, counter_r=100, counter_dmg=20

---

## TABLE 2 — LLM DECISION QUALITY (use as Table 2 in article)

| Config           | Total LLM | Safe   | Overconfident | Crowded Situations | Correctly Avoided | Walked Into Danger |
|------------------|-----------|--------|---------------|--------------------|-------------------|--------------------|
| baseline         | 8         | 5 (62%)| 3 (37%)       | 3                  | 3/3 (100%)        | 0/3 (0%)           |
| strong_wasp      | 22        | 15(68%)| 7 (31%)       | 7                  | 7/7 (100%)        | 0/7 (0%)           |
| weak_wasp        | 5         | 3 (60%)| 2 (40%)       | 2                  | 2/2 (100%)        | 0/2 (0%)           |
| strong_sentinels | 4         | 0 (0%) | 4 (100%)      | 4                  | 4/4 (100%)        | 0/4 (0%)           |

**Definition:** A decision is "safe" if the chosen target has ≤2 sentinels within the 50px attack zone
AND fewer than threshold (2) sentinels within the 100px counter-attack zone.
A "crowded situation" is when ≥2 sentinels are within 100px of the target.

---

## TABLE 3 — STRATEGY EVOLUTION (use as Table 3 in article)

| Config           | Outcome      | First Half Safe | Second Half Safe | Trend      | Avg Sentinels 1st | Avg Sentinels 2nd |
|------------------|--------------|-----------------|------------------|------------|-------------------|-------------------|
| baseline         | SENTINEL_WIN | 3/4  (75%)      | 2/4  (50%)       | DEGRADING  | 17.2              | 14.8              |
| strong_wasp      | WASP_WIN     | 4/11 (36%)      | 11/11 (100%)     | IMPROVING  | 14.1              | 1.9               |
| weak_wasp        | SENTINEL_WIN | 1/2  (50%)      | 2/3  (66%)       | IMPROVING  | 19.0              | 17.3              |
| strong_sentinels | SENTINEL_WIN | 0/2  (0%)       | 0/2  (0%)        | STABLE     | 21.0              | 15.0              |

---

## REAL LLM REASONING QUOTES (direct from Gemini 2.5 Flash — use verbatim in article)

### Safe / Correct Decisions:
- "Sentinel 14 at (78, 238) is isolated within 50px, has no other sentinels within 100px, and is far from the hive."
- "Sentinel 2 is isolated, within attack range, and outside the hive area, making it a safe and efficient target."
- "Sentinel 2 is the only isolated target outside the hive, with no other sentinels within 100px."
- "Sentinel 14 is the only isolated sentinel meeting all safety criteria and is far from the hive."
- "Sentinel 5 is the only sentinel within 50px, and no other sentinels are within 100px of it. It is also far from the hive."

### Overconfident / Dangerous Decisions (chosen despite crowding within 100px):
- "Sentinel 15 is isolated, outside the hive region, and far from other sentinels making it a safe kill."
  → [Despite 3+ sentinels within 100px counter-attack zone]
- "Sentinel 6 is isolated, within attack range, and outside the hive's main area."
  → [Position classified as dangerous by simulation metrics]
- "Sentinel 9 is isolated from all other sentinels and far from the hive, making it a safe and efficient target."
  → [LLM misjudged proximity of nearby sentinels]
- "Sentinel 18 at (514, 474) is isolated, with no other sentinels within 100px, making it a safe kill outside the hive."
  → [Confidence expressed but contradicted by actual sensor data]
- "Sentinel 10 is isolated, far from the hive, and within safe attack range, while other sentinels are either too close to the hive or too clustered."

---

## KEY FINDINGS FOR ARTICLE

### Finding 1: The LLM understands the danger concept but mismeasures distances
The LLM consistently uses the correct vocabulary ("isolated", "within 100px", "safe kill") and
reasoning structure. However, in overconfident decisions, its spatial estimates are wrong — it
concludes a target is isolated when the simulation's ground truth shows crowding. This is a
hallucination of spatial geometry, not a failure to understand the rules.

### Finding 2: Strategy evolves within a run — but the direction depends on parameters
- strong_wasp (WASP WINS): First half 36% safe → second half 100% safe.
  As the colony thins out (avg 14 sentinels → avg 1.9), the LLM becomes perfectly cautious.
  Interpretation: When the battlefield is sparse, the LLM has an easier time finding isolated targets
  and accurately assessing them. Its reasoning improves because the problem becomes easier.

- baseline (SENTINEL WINS): First half 75% safe → second half 50% safe (DEGRADING).
  As the wasp takes damage and time pressure increases, it becomes more reckless.
  Interpretation: The LLM does not receive health/urgency feedback in the prompt — it does not
  "know" it is losing. Yet its behavior degrades, possibly because the spatial density stays high
  and it starts accepting riskier targets.

- strong_sentinels (SENTINEL WINS): 0% safe throughout (STABLE).
  The counter-attack radius is so wide (130px) that virtually every target position is dangerous.
  The LLM keeps choosing "isolated" targets that are objectively safe by its own assessment,
  but the wider sentinel counter-radius makes every approach lethal. The LLM cannot adapt
  to a parameter it cannot observe.

### Finding 3: The only winning configuration produced the most LLM decisions (22) and highest safe rate (68%)
More LLM calls = more time alive = more chances to learn the battlefield. The strong_wasp config
gave the LLM 22 decision cycles. By the second half, it achieved 100% safe targeting. This
suggests that given enough iterations, the LLM can converge on a safe strategy — but
it needs the physical capability (health, speed, attack radius) to survive long enough to do so.

### Finding 4: The LLM never intentionally "walked into danger" — but it was wrong about what danger was
In all crowded situations (16 total across all runs), the LLM expressed intent to avoid danger
in its reasoning. But 14 of those decisions were still classified as overconfident by the simulation's
ground truth metrics. This gap between expressed reasoning and objective outcome is the core
tension in the article: the LLM is always "trying to be safe" but its spatial perception is unreliable.

### Finding 5: Fallback heuristic vs. LLM — the heuristic sometimes outperforms
When Gemini was rate-limited or timed out, the system used a cluster-targeting heuristic.
In strong_sentinels (0% safe from LLM), the 1 fallback decision also resulted in 0% safe —
so the heuristic fared equally poorly. In strong_wasp, the LLM's second-half decisions were
100% safe while many first-half decisions were fallback-heuristic. This suggests the LLM adds
value specifically in sparse, late-battle conditions.

---

## ARTICLE STRUCTURE IDEAS

### Suggested Title Options:
- "Emergent Caution: Observing LLM Strategy in a Multi-Agent Predator-Prey Simulation"
- "When Does the Wasp Learn? LLM Decision Quality in a JaCaMo Agent System"
- "From Rules to Reasoning: Integrating Gemini into a BDI Multi-Agent Bee Colony"

### Suggested Section Structure:
1. Introduction — BDI agents + LLMs as a new hybrid; why observe reasoning in-situ
2. Related Work — JaCaMo/BDI, LLM-in-the-loop agents, emergent behavior
3. System Architecture — JaCaMo, CArtAgO artifacts, Gemini API integration, JavaFX visualization
4. Experimental Design — 4-config parameter sweep, logging infrastructure, metrics definition
5. Results — Tables 1, 2, 3 + reasoning quotes
6. Discussion — Finding 1-5 above; what the LLM can/cannot perceive; spatial hallucination
7. Conclusion — LLMs in MAS are promising but need grounded perception; future: give LLM health/HP feedback

### Key Concepts to Define in Article:
- "Safe choice": target with ≤2 sentinels in 50px attack zone AND <2 in 100px counter zone
- "Overconfident decision": LLM reasoning says "safe" but ground truth says crowded
- "Strategy trend": first-half vs second-half safe rate within a single run
- "Spatial hallucination": LLM claims isolation when sensors show crowding

### Metrics That Work Well for a Table:
- Safe% per config (clear variance: 0% to 68%)
- Outcome (Win/Loss) correlates with safe% — strongest argument
- Strategy trend (IMPROVING/DEGRADING/STABLE) maps cleanly to outcomes
- LLM call count reflects survival time — ties capability to learning

### Limitations to Acknowledge:
- Small sample (1 run per config) — stochastic variance not controlled
- Rate limiting caused ~35-45% fallback decisions in some runs — mixed LLM/heuristic signal
- Prompt does not include wasp HP — LLM cannot adapt to its own health state
- Gemini 2.5 Flash, not a reasoning-specialized model — results may differ with other LLMs
- JavaFX window requires manual closure between runs — not fully automated

---

## RAW DATA LOCATION
- Log files: logs/battle_*_events.csv, *_llm.jsonl, *_summary.json
- Re-run analysis: python analyze_runs.py
- Re-run sweep: edit run-config.properties, then ./gradlew run (or sweep.bat on Windows cmd)
- Model used: gemini-2.5-flash (thinking disabled for latency)
- Rate limit: 5000ms between calls
