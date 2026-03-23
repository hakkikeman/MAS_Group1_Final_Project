# Project Roadmap
# Multi-Agent Bee Colony LLM — Improvement Plan
# Created: 2026-03-23

---

## CURRENT STATE SUMMARY

The project is a JaCaMo multi-agent simulation of a honeybee colony under wasp attack.
The wasp's targeting decisions are delegated to Google Gemini 2.5 Flash via a live API call.
A 4-config parameter sweep has been completed and results are logged in ARTICLE_FINDINGS.md.

---

## WHAT IS UNNECESSARY (do not invest time here)

### 1. Non-wasp colony agents are decorative
Queen, nurses, explorers — their pollen/honey/temperature/egg-laying loops run completely
in parallel to the battle and have ZERO effect on the battle outcome. The wasp only reads
sentinel positions from the environment. Everything else is visual flavour.

**Recommendation:** Do not remove them (they make the simulation richer visually and are
relevant for the MAS framing of the article), but do not add features to them either.
They are good enough as-is.

### 2. The Moise doBattle organisational scheme is dead code
organisation.xml defines a full doBattle scheme with goals:
  patrolPerimeter, detectThreats, fleeToSafety, groupAttack, hiveDefense
This scheme is NEVER instantiated. The sentinel patrol and flee-to-hive plans in worker.asl
are also never triggered because `wasp_detected` belief is never asserted.
notifySentinelsAboutWasp() in Environment.java has an empty body.

**Recommendation:** Either wire it up (see Priority 3 below) or leave it. Do not delete it —
it is described in the article as a planned extension, which is legitimate.

### 3. Prompt hardcodes radii that are configurable
The buildPrompt() method in GeminiService.java writes "50px" and "100px" as literal strings,
regardless of the actual RunConfig values. If you run with strong_sentinels (counter_r=130),
the LLM is still told 100px. This directly caused the 0% safe rate in that config.

---

## IMPROVEMENT PRIORITIES

---

### PRIORITY 1 — Add wasp HP to the LLM prompt (HIGH VALUE, ~30 min)

**Problem:**
The LLM has no knowledge of the wasp's current health. The baseline run shows a DEGRADING
strategy trend (75% → 50% safe) but the LLM cannot reason about urgency because it doesn't
know it is losing. This is explicitly named as a finding in the article.

**Change:**
In GeminiService.java, update the buildPrompt() signature to accept waspHealth and waspMaxHealth,
then add to the CURRENT STATE block:
  - Your health: {waspHealth}/{waspMaxHealth} HP ({pct}% remaining)

Update all callers: getAttackStrategy(), prefetchNextStrategy() in GeminiService.java,
and WaspArtifact.java which calls both.

**Expected article result:**
Run a new sweep. If the LLM avoids risk more when HP is low → confirms the grounding
hypothesis. If it doesn't change → equally interesting (LLM ignores numeric state).

**Prompt section to add:**
```
CURRENT STATE:
- Your position: (X, Y)
- Your health: 120/200 HP (60% remaining)  ← NEW
- Sentinels remaining: 14                  ← NEW
- Sentinel positions: ...
```

**Optimisation note:**
Fewer tokens = faster response. Keep the HP line short. "Health: 60%" is enough.
Don't add narrative ("you are critically wounded") — let the LLM interpret the number itself.
That's more scientifically clean: we give data, not emotional framing.

---

### PRIORITY 2 — Make attack/counter radii dynamic in the prompt (HIGH VALUE, ~15 min)

**Problem:**
Hardcoded "50px" and "100px" in the prompt string means the LLM is given wrong rules
whenever a non-baseline config is used. In strong_sentinels (counter_r=130, dmg=35),
the LLM was playing by the wrong rulebook.

**Change:**
In buildPrompt(), replace hardcoded values with RunConfig values:
```java
int attackR  = cfg.getWaspAttackRadius();      // e.g. 50
int counterR = cfg.getSentinelCounterRadius(); // e.g. 130
int maxKills = cfg.getWaspMaxKills();          // e.g. 2
int threshold = cfg.getCounterAttackThreshold(); // e.g. 2
```

Prompt becomes:
```
- ATTACK: You can kill up to {maxKills} sentinels within {attackR}px of you
- DANGER: If {threshold}+ sentinels are within {counterR}px, they counter-attack and damage you
```

**Expected article result:**
strong_sentinels run should show improved LLM awareness — it will know the danger zone
is 130px, not 100px. If safe% increases vs. the current 0%, this is a clean, controllable
experiment showing that prompt grounding matters.

**Optimisation note:**
Also pass `cfg.getSentinelCounterDamage()` so the LLM knows how much each counter-attack
costs. This lets it reason about acceptable risk more precisely.

---

### PRIORITY 3 — No-LLM baseline run for comparison (HIGH VALUE, ~30 min)

**Problem:**
Currently there is no pure-heuristic run. We cannot answer "does the LLM actually add value
over the fallback heuristic?" in the article.

**Change:**
Add a config option to RunConfig: `llm.enabled=true/false`
When false, GeminiService always returns getDefaultDecision() without calling the API.
BattleLogger already logs isFallback correctly, so analysis works unchanged.

Add a 5th sweep config: `no_llm` (baseline params, Gemini disabled).
Compare kills, safe%, and outcome vs. baseline.

**Prompt optimisation note:**
This requires no prompt changes — it bypasses the prompt entirely. Low effort, high payoff.

---

### PRIORITY 4 — Chart output from analyze_runs.py (MEDIUM VALUE, ~1 hour)

**Problem:**
The analysis output is text-only. Academic articles need figures.

**Change:**
Add optional matplotlib output to analyze_runs.py:
```
python analyze_runs.py --charts
```
Generates:
  - Bar chart: Safe% per config (grouped by first/second half for strategy trend)
  - Bar chart: Kills and damage taken per config
  - Line chart: Safe rate over decision index for the winning run (strong_wasp)
    → visually shows the 36% → 100% improvement across 22 decisions

**Optimisation note:**
Keep analysis.py runnable without matplotlib (graceful import error). Some machines
may not have it. Only generate charts when --charts flag is passed.

---

### PRIORITY 5 — Add decision memory to the prompt (MEDIUM VALUE, ~1 hour)

**Problem:**
Each LLM call is stateless. Gemini has no memory of what it chose in previous cycles.
Giving it a short history could accelerate the strategy improvement seen in strong_wasp.

**Change:**
In GeminiService, maintain a rolling list of last N decisions (target coords + outcome label).
Pass to prompt as:
```
RECENT DECISIONS:
- Decision 3: Targeted (450, 280) → SAFE (no counter-attack)
- Decision 4: Targeted (612, 190) → DANGEROUS (took 20 HP counter-attack)
- Decision 5: Targeted (78, 238) → SAFE
```

**Expected article result:**
If safe% improves faster with memory → LLM can learn within a run.
If not → LLM ignores history (also a finding worth reporting).

**Optimisation note:**
Keep history to last 3-5 decisions max. More = more tokens = slower + more expensive.
The outcome label (SAFE/DANGEROUS) must come from the simulation's ground truth after
each decision, not the LLM's own assessment. This makes it honest feedback.

---

### PRIORITY 6 — Wire the existing sentinel defence system (LOWER VALUE, ~2-3 hours)

**Problem:**
The sentinel group-attack and flee-to-hive code is written in worker.asl and organisation.xml
but never fires because `wasp_detected` is never asserted by the environment.

**Change:**
In WaspArtifact.java, when the wasp enters the counter-attack radius of any sentinel,
call Environment.notifySentinelsAboutWasp(waspX, waspY).
Implement that method to assert `wasp_detected(WX, WY)` as a percept for sentinel agents.
Sentinel agents already have plans for this — they just never receive the trigger.

**Expected result:**
Sentinels will now actively flee or coordinate. This makes the colony a genuine reactive
MAS, not just a passive target. The wasp's job becomes harder and more dynamic.

**Risk:**
This is a significant behaviour change. Run it on a separate branch (feature/sentinel-defence).
Do not use this data in the article unless you have time to re-run the full sweep with it.

---

## PROMPT OPTIMISATION ANALYSIS

### Current prompt structure (tokens: ~250-400 depending on sentinel count)
```
[Role framing]            ~20 tokens
[Game rules — 5 lines]    ~60 tokens  ← hardcoded, partially wrong
[Map size + hive hint]    ~25 tokens
[Current state header]    ~5 tokens
[Wasp position]           ~15 tokens
[Sentinel list × N]       ~10 tokens per sentinel (20 sentinels = 200 tokens)
[Analysis instructions]   ~50 tokens
[Response format]         ~25 tokens
```

### Issues with current prompt
1. Rules section hardcodes 50px / 100px — wrong for non-baseline configs
2. No wasp HP — LLM cannot reason about risk vs. urgency
3. Hive coordinates are approximate ("around 649-799x") — could be exact
4. "exactly 1 sentinel within 50px" instruction is overly rigid — with a 70px radius
   (strong_wasp) the LLM may misjudge what counts as "within range"
5. No feedback from previous decisions — stateless

### Optimised prompt structure (after Priorities 1 + 2)
```
[Role framing — keep short]
[Game rules — dynamically use RunConfig values]
[Current state]
  - Position, Health %, Sentinels remaining
  - Sentinel list (keep — essential for spatial reasoning)
[Recent decisions — last 3, if Priority 5 implemented]
[Instructions — updated to match actual radii]
[Response format — unchanged]
```

### Token budget consideration
With 20 sentinels: ~10 tokens × 20 = 200 tokens for positions alone.
As sentinels die, the prompt shrinks naturally — this is why second-half LLM decisions
tend to be better (smaller, simpler prompt as well as sparser battlefield).
Estimated total with HP + dynamic radii: +15 tokens over current. Negligible.

### What NOT to change in the prompt
- Keep the sentinel list as raw coordinates — do not summarise or cluster them.
  The LLM needs raw positions to reason about distances accurately.
- Do not add emotional framing ("you are in danger!") — give numeric data only.
  Scientific experiments need controlled inputs.
- Do not change the response format — TARGET_X / TARGET_Y / REASONING works and
  the parser is built around it.

---

## IMPLEMENTATION ORDER (suggested)

1. Create branch: `feature/article-improvements`
2. Priority 2: Dynamic radii in prompt (15 min, lowest risk)
3. Priority 1: Add wasp HP to prompt (30 min)
4. Priority 3: No-LLM config flag (30 min)
5. Re-run full sweep (4 original configs + 1 no-llm config)
6. Priority 4: Chart output (1 hour)
7. Priority 5: Decision memory (1 hour) — optional, only if time allows
8. Priority 6: Sentinel defence — separate branch, do not merge to article branch

---

## FILES TO MODIFY

| File | Change |
|------|--------|
| `src/env/artifact/GeminiService.java` | buildPrompt() — dynamic radii + HP + optional memory |
| `src/env/artifact/WaspArtifact.java` | Pass waspHealth to getAttackStrategy() / prefetch |
| `src/env/artifact/RunConfig.java` | Add getLlmEnabled() for Priority 3 |
| `run-config.properties` | Add llm.enabled=true |
| `sweep.bat` | Add 5th config: no_llm |
| `analyze_runs.py` | Add --charts flag with matplotlib output |

---

## ARTICLE FILES
- `ARTICLE_FINDINGS.md` — experiment results, tables, key findings, article structure
- `ARTICLE_DRAFT_INTRODUCTION.md` — draft introduction text
- `ROADMAP.md` — this file
- `logs/` — raw experiment data (events.csv, llm.jsonl, summary.json per run)
