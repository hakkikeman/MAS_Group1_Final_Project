@echo off
REM ═══════════════════════════════════════════════════════════════════════════
REM  Parameter sweep for the Multi-Agent Bee Colony LLM experiment
REM
REM  Runs the simulation with four pre-set configurations in sequence.
REM  After each run, close the JavaFX window and press Enter to continue.
REM  When all runs finish, analyse results with:
REM      python analyze_runs.py
REM ═══════════════════════════════════════════════════════════════════════════

set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

echo ============================================================
echo  Multi-Agent Bee Colony – Parameter Sweep
echo ============================================================
echo.

REM ── Run 1: Baseline ────────────────────────────────────────────────────────
echo [1/4] BASELINE – default parameters
(
echo run.label=baseline
echo wasp.health=200
echo wasp.attack.radius=50
echo wasp.max.kills=2
echo wasp.speed=3
echo sentinel.counter.radius=100
echo sentinel.counter.damage=20
echo sentinel.counter.threshold=2
echo llm.rate.limit.ms=5000
) > run-config.properties
echo Config written. Starting simulation...
call gradlew.bat run
echo.
echo [1/4] Run complete. Press Enter for next configuration...
pause > nul

REM ── Run 2: Strong Wasp ─────────────────────────────────────────────────────
echo [2/4] STRONG WASP – higher HP, wider attack radius, faster speed
(
echo run.label=strong_wasp
echo wasp.health=350
echo wasp.attack.radius=70
echo wasp.max.kills=3
echo wasp.speed=5
echo sentinel.counter.radius=100
echo sentinel.counter.damage=20
echo sentinel.counter.threshold=2
echo llm.rate.limit.ms=5000
) > run-config.properties
echo Config written. Starting simulation...
call gradlew.bat run
echo.
echo [2/4] Run complete. Press Enter for next configuration...
pause > nul

REM ── Run 3: Weak Wasp ───────────────────────────────────────────────────────
echo [3/4] WEAK WASP – lower HP, smaller attack radius
(
echo run.label=weak_wasp
echo wasp.health=100
echo wasp.attack.radius=35
echo wasp.max.kills=1
echo wasp.speed=3
echo sentinel.counter.radius=100
echo sentinel.counter.damage=20
echo sentinel.counter.threshold=2
echo llm.rate.limit.ms=5000
) > run-config.properties
echo Config written. Starting simulation...
call gradlew.bat run
echo.
echo [3/4] Run complete. Press Enter for next configuration...
pause > nul

REM ── Run 4: Strong Sentinels ────────────────────────────────────────────────
echo [4/4] STRONG SENTINELS – higher counter-attack damage and wider counter radius
(
echo run.label=strong_sentinels
echo wasp.health=200
echo wasp.attack.radius=50
echo wasp.max.kills=2
echo wasp.speed=3
echo sentinel.counter.radius=130
echo sentinel.counter.damage=35
echo sentinel.counter.threshold=2
echo llm.rate.limit.ms=5000
) > run-config.properties
echo Config written. Starting simulation...
call gradlew.bat run
echo.
echo [4/4] All runs complete!
echo.

REM ── Restore baseline config ─────────────────────────────────────────────────
(
echo run.label=baseline
echo wasp.health=200
echo wasp.attack.radius=50
echo wasp.max.kills=2
echo wasp.speed=3
echo sentinel.counter.radius=100
echo sentinel.counter.damage=20
echo sentinel.counter.threshold=2
echo llm.rate.limit.ms=5000
) > run-config.properties

echo ============================================================
echo  Analyse results with:
echo      python analyze_runs.py
echo ============================================================
pause
