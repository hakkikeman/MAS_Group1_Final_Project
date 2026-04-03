#!/bin/bash
# Parameter sweep — bash version for non-interactive execution
set -e

CD="$(cd "$(dirname "$0")" && pwd)"
cd "$CD"

export JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

run_config() {
    local label="$1"
    shift
    echo "========================================"
    echo "  Running: $label"
    echo "========================================"

    # Write config
    cat > run-config.properties <<EOF
$@
EOF
    echo "Config written:"
    cat run-config.properties
    echo ""

    # Run simulation
    ./gradlew run --no-daemon 2>&1
    echo ""
    echo "  $label — DONE"
    echo ""
}

echo "========================================"
echo "  Multi-Agent Bee Colony — 5-Config Sweep"
echo "  Started: $(date)"
echo "========================================"
echo ""

# Run 1: Baseline
run_config "1/5 BASELINE" \
"run.label=baseline
wasp.health=200
wasp.attack.radius=50
wasp.max.kills=2
wasp.speed=3
sentinel.counter.radius=100
sentinel.counter.damage=20
sentinel.counter.threshold=2
llm.enabled=true
llm.rate.limit.ms=3000
auto.exit=true"

# Run 2: Strong Wasp
run_config "2/5 STRONG WASP" \
"run.label=strong_wasp
wasp.health=350
wasp.attack.radius=70
wasp.max.kills=3
wasp.speed=5
sentinel.counter.radius=100
sentinel.counter.damage=20
sentinel.counter.threshold=2
llm.enabled=true
llm.rate.limit.ms=3000
auto.exit=true"

# Run 3: Weak Wasp
run_config "3/5 WEAK WASP" \
"run.label=weak_wasp
wasp.health=100
wasp.attack.radius=35
wasp.max.kills=1
wasp.speed=3
sentinel.counter.radius=100
sentinel.counter.damage=20
sentinel.counter.threshold=2
llm.enabled=true
llm.rate.limit.ms=3000
auto.exit=true"

# Run 4: Strong Sentinels
run_config "4/5 STRONG SENTINELS" \
"run.label=strong_sentinels
wasp.health=200
wasp.attack.radius=50
wasp.max.kills=2
wasp.speed=3
sentinel.counter.radius=130
sentinel.counter.damage=35
sentinel.counter.threshold=2
llm.enabled=true
llm.rate.limit.ms=3000
auto.exit=true"

# Run 5: No LLM
run_config "5/5 NO LLM" \
"run.label=no_llm
wasp.health=200
wasp.attack.radius=50
wasp.max.kills=2
wasp.speed=3
sentinel.counter.radius=100
sentinel.counter.damage=20
sentinel.counter.threshold=2
llm.enabled=false
llm.rate.limit.ms=3000
auto.exit=true"

# Restore baseline (no auto-exit for manual runs)
cat > run-config.properties <<EOF
run.label=baseline
wasp.health=200
wasp.attack.radius=50
wasp.max.kills=2
wasp.speed=3
sentinel.counter.radius=100
sentinel.counter.damage=20
sentinel.counter.threshold=2
llm.enabled=true
llm.rate.limit.ms=3000
auto.exit=false
EOF

echo "========================================"
echo "  ALL 5 RUNS COMPLETE"
echo "  Finished: $(date)"
echo "  Analyse: python analyze_runs.py --charts"
echo "========================================"
