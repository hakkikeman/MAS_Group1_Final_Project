#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys, io
# Force UTF-8 output on Windows (avoids cp1254 UnicodeEncodeError)
if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
"""
analyze_runs.py – Post-run analysis for the Multi-Agent Bee Colony LLM experiment.

Reads all log files produced by BattleLogger and answers three article questions:
  1. When does the LLM correctly identify danger from crowding?
  2. When does it get overconfident?
  3. Does it develop something like a "strategy" across rounds?

Usage:
  python analyze_runs.py              # reads from ./logs/
  python analyze_runs.py path/to/logs
"""

import json
import os
import sys
import glob
from collections import defaultdict

SEP  = "=" * 90
SEP2 = "-" * 90


# ─────────────────────────────────────────────────────────────────────────────
# Loaders
# ─────────────────────────────────────────────────────────────────────────────

def load_summaries(logs_dir):
    summaries = []
    for path in sorted(glob.glob(os.path.join(logs_dir, "*_summary.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                summaries.append(json.load(f))
        except Exception as e:
            print(f"  [warn] Could not load {path}: {e}")
    return summaries


def load_llm_logs(logs_dir):
    """Returns {run_id: [decision_dict, ...]}"""
    result = {}
    for path in sorted(glob.glob(os.path.join(logs_dir, "*_llm.jsonl"))):
        run_id = os.path.basename(path).replace("battle_", "").replace("_llm.jsonl", "")
        decisions = []
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        decisions.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
        result[run_id] = decisions
    return result


# ─────────────────────────────────────────────────────────────────────────────
# Table 1 – Battle summary
# ─────────────────────────────────────────────────────────────────────────────

def print_summary_table(summaries):
    print(f"\n{SEP}")
    print("  TABLE 1 – BATTLE SUMMARY")
    print(SEP)
    if not summaries:
        print("  (no summary files found)")
        return

    hdr = f"{'Run ID':<22} {'Label':<16} {'Outcome':<15} {'Dur(s)':<8} " \
          f"{'Kills':<7} {'WaspHP':<8} {'LLM':<6} {'Safe%':<8} " \
          f"{'Fallbk%':<9} {'CtrAtk':<8} {'DmgTkn':<7}"
    print(hdr)
    print(SEP2)

    for s in summaries:
        st = s.get("stats", {})
        cfg = s.get("config", {})
        calls     = st.get("total_llm_calls", 0)
        safe      = st.get("llm_safe_choices", 0)
        fallback  = st.get("llm_fallback_decisions", 0)
        safe_pct  = f"{100*safe//calls}%"   if calls else "–"
        fb_pct    = f"{100*fallback//calls}%"if calls else "–"
        dur_s     = s.get("battle_duration_ms", 0) / 1000

        print(f"{s.get('run_id','?'):<22} "
              f"{str(cfg.get('run_label','default')):<16} "
              f"{s.get('outcome','?'):<15} "
              f"{dur_s:<8.1f} "
              f"{st.get('sentinels_killed',0):<7} "
              f"{st.get('wasp_final_health',0):<8} "
              f"{calls:<6} "
              f"{safe_pct:<8} "
              f"{fb_pct:<9} "
              f"{st.get('counter_attacks_received',0):<8} "
              f"{st.get('total_damage_taken',0):<7}")


# ─────────────────────────────────────────────────────────────────────────────
# Table 2 – LLM decision quality
# ─────────────────────────────────────────────────────────────────────────────

def print_decision_quality(llm_logs, summaries):
    print(f"\n{SEP}")
    print("  TABLE 2 – LLM DECISION QUALITY ANALYSIS")
    print(SEP)

    # Build outcome lookup
    outcome_by_id = {s["run_id"]: s.get("outcome", "?") for s in summaries}

    for run_id, decisions in llm_logs.items():
        if not decisions:
            continue
        outcome = outcome_by_id.get(run_id, "?")
        safe      = [d for d in decisions if d.get("is_safe_choice")]
        dangerous = [d for d in decisions if not d.get("is_safe_choice")]
        fallback  = [d for d in decisions if d.get("is_fallback")]
        n = len(decisions)

        print(f"\n  Run: {run_id}  →  {outcome}  ({n} LLM decisions)")
        print(SEP2)
        print(f"    Safe choices    : {len(safe)}/{n}  "
              f"({100*len(safe)//n if n else 0}%)")
        print(f"    Dangerous choices (overconfident): {len(dangerous)}/{n}  "
              f"({100*len(dangerous)//n if n else 0}%)")
        print(f"    Fallback (no Gemini): {len(fallback)}/{n}  "
              f"({100*len(fallback)//n if n else 0}%)")

        # Near-50 distribution
        near50_vals = [d.get("sentinels_near_target_50px", 0) for d in decisions]
        avg_near50 = sum(near50_vals) / len(near50_vals) if near50_vals else 0
        print(f"    Avg sentinels in attack zone (50 px): {avg_near50:.2f}")

        # Danger recognition: was the LLM correct when near100 >= 2?
        crowded_safe  = sum(1 for d in decisions
                            if d.get("sentinels_near_target_100px", 0) >= 2
                            and d.get("is_safe_choice"))
        crowded_total = sum(1 for d in decisions
                            if d.get("sentinels_near_target_100px", 0) >= 2)
        if crowded_total:
            print(f"    Crowded situations (≥2 within 100 px): {crowded_total}")
            print(f"      → LLM correctly avoided danger : {crowded_total - crowded_safe}/{crowded_total} "
                  f"({100*(crowded_total-crowded_safe)//crowded_total}%)")
            print(f"      → LLM walked into danger       : {crowded_safe}/{crowded_total} "
                  f"({100*crowded_safe//crowded_total}%)  ← overconfidence events")

        # Sample reasoning
        if safe:
            print(f"\n    Sample SAFE choice reasoning:")
            for d in safe[:2]:
                r = (d.get("reasoning") or "").strip()
                if r:
                    print(f"      ✓  {r[:110]}")

        if dangerous:
            print(f"\n    Sample DANGEROUS / overconfident reasoning:")
            for d in dangerous[:2]:
                r = (d.get("reasoning") or "").strip()
                if r:
                    print(f"      ✗  {r[:110]}")


# ─────────────────────────────────────────────────────────────────────────────
# Table 3 – Strategy evolution within a run
# ─────────────────────────────────────────────────────────────────────────────

def print_strategy_evolution(llm_logs, summaries):
    print(f"\n{SEP}")
    print("  TABLE 3 – STRATEGY EVOLUTION (first half vs second half of each run)")
    print(SEP)

    outcome_by_id = {s["run_id"]: s.get("outcome", "?") for s in summaries}

    for run_id, decisions in llm_logs.items():
        if len(decisions) < 4:
            print(f"\n  Run {run_id}: too few decisions ({len(decisions)}) for trend analysis.")
            continue

        outcome = outcome_by_id.get(run_id, "?")
        print(f"\n  Run: {run_id}  →  {outcome}")

        half = len(decisions) // 2
        first  = decisions[:half]
        second = decisions[half:]

        def pct_safe(lst):
            n = len(lst)
            s = sum(1 for d in lst if d.get("is_safe_choice"))
            return s, n, f"{100*s//n if n else 0}%"

        f_safe, f_n, f_pct = pct_safe(first)
        s_safe, s_n, s_pct = pct_safe(second)

        print(f"    First  half ({f_n} decisions) – safe: {f_safe}/{f_n} ({f_pct})")
        print(f"    Second half ({s_n} decisions) – safe: {s_safe}/{s_n} ({s_pct})")

        if s_safe > f_safe:
            trend = "IMPROVING  – LLM becomes more cautious as sentinel count drops"
        elif s_safe < f_safe:
            trend = "DEGRADING  – LLM grows overconfident / more aggressive later"
        else:
            trend = "STABLE     – consistent strategy throughout"
        print(f"    Trend: {trend}")

        # Avg sentinels at decision time across halves
        def avg_count(lst):
            vals = [d.get("sentinel_count", 0) for d in lst]
            return sum(vals) / len(vals) if vals else 0

        print(f"    Avg sentinels at decision time – first: {avg_count(first):.1f}   "
              f"second: {avg_count(second):.1f}")

        # Peek at reasoning evolution (first vs last)
        non_empty = [d for d in decisions if (d.get("reasoning") or "").strip()]
        if len(non_empty) >= 2:
            print(f"    First reasoning : {non_empty[0]['reasoning'][:100]}")
            print(f"    Last  reasoning : {non_empty[-1]['reasoning'][:100]}")


# ─────────────────────────────────────────────────────────────────────────────
# Table 4 – Config comparison across runs
# ─────────────────────────────────────────────────────────────────────────────

def print_config_comparison(summaries):
    if len(summaries) < 2:
        return
    print(f"\n{SEP}")
    print("  TABLE 4 – CONFIG COMPARISON ACROSS RUNS")
    print(SEP)
    for s in summaries:
        cfg  = s.get("config", {})
        st   = s.get("stats", {})
        calls = st.get("total_llm_calls", 0)
        safe  = st.get("llm_safe_choices", 0)
        print(f"\n  [{cfg.get('run_label','?')}]  →  {s.get('outcome','?')}")
        print(f"    Config : hp={cfg.get('wasp_health')}  "
              f"atk_r={cfg.get('wasp_attack_radius')}  "
              f"max_kills={cfg.get('wasp_max_kills')}  "
              f"speed={cfg.get('wasp_speed')}  "
              f"ctr_r={cfg.get('sentinel_counter_radius')}  "
              f"ctr_dmg={cfg.get('sentinel_counter_damage')}")
        print(f"    Result : kills={st.get('sentinels_killed')}  "
              f"wasp_hp_left={st.get('wasp_final_health')}  "
              f"safe_pct={100*safe//calls if calls else '–'}%  "
              f"llm_calls={calls}")
        ta = s.get("temporal_analysis", {})
        if ta:
            print(f"    Trend  : {ta.get('strategy_trend','?')}  "
                  f"(1st-half safe={ta.get('first_half_safe')}/{ta.get('first_half_total')}  "
                  f"2nd-half safe={ta.get('second_half_safe')}/{ta.get('second_half_total')})")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    logs_dir = sys.argv[1] if len(sys.argv) > 1 else "logs"

    if not os.path.isdir(logs_dir):
        print(f"[error] Logs directory not found: {logs_dir}")
        print("  Run the simulation first:  ./gradlew run")
        sys.exit(1)

    summaries = load_summaries(logs_dir)
    llm_logs  = load_llm_logs(logs_dir)

    print(f"\n  Found {len(summaries)} completed run(s), {len(llm_logs)} LLM log file(s).")
    print(f"  Logs directory: {os.path.abspath(logs_dir)}")

    print_summary_table(summaries)
    print_decision_quality(llm_logs, summaries)
    print_strategy_evolution(llm_logs, summaries)
    print_config_comparison(summaries)

    print(f"\n{SEP}")
    print("  Analysis complete.")
    print(f"  Raw LLM responses are in: logs/*_llm.jsonl")
    print(f"  Full summaries are in   : logs/*_summary.json")
    print(SEP)


if __name__ == "__main__":
    main()
