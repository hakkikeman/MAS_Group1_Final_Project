// GeminiService.java - LLM Integration for Wasp Agent Strategy
package artifact;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import model.Position;

/**
 * Service class for communicating with Gemini LLM API.
 * Provides strategic attack decisions for the Wasp agent.
 */
public class GeminiService {
    private static GeminiService instance;
    private String apiKey;
    private long lastCallTime = 0;
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private final Gson gson = new Gson();

    // Async prefetch support
    private volatile AttackDecision prefetchedDecision = null;
    private volatile boolean prefetchInProgress = false;
    private AttackDecision lastValidDecision = null;

    private GeminiService() {
        loadApiKey();
    }

    public static synchronized GeminiService getInstance() {
        if (instance == null) {
            instance = new GeminiService();
        }
        return instance;
    }

    private void loadApiKey() {
        try {
            Properties props = new Properties();
            // Try multiple paths for the config file
            String[] paths = {
                    "src/env/artifact/gemini-config.properties",
                    "gemini-config.properties"
            };

            for (String path : paths) {
                try {
                    props.load(new FileInputStream(path));
                    apiKey = props.getProperty("gemini.api.key");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        System.out.println("[GeminiService] API key loaded successfully from: " + path);
                        return;
                    }
                } catch (Exception e) {
                    // Try next path
                }
            }

            System.err.println("[GeminiService] WARNING: Could not load API key from config file!");
        } catch (Exception e) {
            System.err.println("[GeminiService] Error loading API key: " + e.getMessage());
        }
    }

    /**
     * Represents an attack decision, either from the LLM or the fallback heuristic.
     * rawResponse holds the full Gemini JSON string for logging/analysis.
     * isFallback is true when Gemini was unavailable and the heuristic was used.
     */
    public static class AttackDecision {
        public int targetX;
        public int targetY;
        public String reasoning;
        public String rawResponse;  // full Gemini JSON (null for fallback)
        public boolean isFallback;  // true when heuristic was used

        public AttackDecision(int x, int y, String reasoning) {
            this.targetX = x;
            this.targetY = y;
            this.reasoning = reasoning;
        }
    }

    /**
     * Check if API is currently rate-limited (internal rate limit)
     */
    public boolean isInternallyRateLimited() {
        return System.currentTimeMillis() - lastCallTime < RunConfig.getInstance().getLlmRateLimitMs();
    }

    /**
     * Query the LLM for optimal attack strategy based on sentinel positions
     */
    public AttackDecision getAttackStrategy(List<Position> sentinelPositions, Position waspPosition, int mapWidth,
            int mapHeight) {
        // Rate limiting check
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCallTime < RunConfig.getInstance().getLlmRateLimitMs()) {
            System.out.println("[GeminiService] Rate limit active, using last cached decision");
            return getDefaultDecision(sentinelPositions, waspPosition);
        }
        lastCallTime = currentTime;

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("[GeminiService] No API key, using fallback strategy");
            return getDefaultDecision(sentinelPositions, waspPosition);
        }

        try {
            String prompt = buildPrompt(sentinelPositions, waspPosition, mapWidth, mapHeight);
            String response = callGeminiAPI(prompt);
            return parseResponse(response, sentinelPositions, waspPosition);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // Shorten 429 rate limit errors
            if (errorMsg != null && errorMsg.contains("429")) {
                // Extract retry time if present
                String retryInfo = "";
                if (errorMsg.contains("retry in")) {
                    int idx = errorMsg.indexOf("retry in");
                    int endIdx = errorMsg.indexOf("s", idx);
                    if (endIdx > idx && endIdx - idx < 30) {
                        retryInfo = " (" + errorMsg.substring(idx, endIdx + 1) + ")";
                    }
                }
                System.out.println("[GeminiService] Rate limited" + retryInfo + ". Using fallback.");
            } else {
                System.err.println("[GeminiService] API error: "
                        + (errorMsg != null && errorMsg.length() > 100 ? errorMsg.substring(0, 100) + "..."
                                : errorMsg));
            }
            return getDefaultDecision(sentinelPositions, waspPosition);
        }
    }

    /**
     * Start prefetching next strategy in background thread.
     * Call this while Wasp is moving to eliminate wait time.
     */
    public void prefetchNextStrategy(List<Position> sentinelPositions, Position waspPosition,
            int mapWidth, int mapHeight) {
        // Don't start new prefetch if one is already in progress
        if (prefetchInProgress) {
            return;
        }

        // Check rate limit - if not enough time passed, don't prefetch
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCallTime < RunConfig.getInstance().getLlmRateLimitMs() - 1000) { // Start 1s before rate limit expires
            return;
        }

        prefetchInProgress = true;
        System.out.println("[GeminiService] Starting prefetch for next target...");

        Thread prefetchThread = new Thread(() -> {
            try {
                AttackDecision decision = getAttackStrategy(sentinelPositions, waspPosition, mapWidth, mapHeight);
                prefetchedDecision = decision;
                lastValidDecision = decision;
                System.out.println(
                        "[GeminiService] Prefetch complete: (" + decision.targetX + ", " + decision.targetY + ")");
            } catch (Exception e) {
                System.err.println("[GeminiService] Prefetch error: " + e.getMessage());
            } finally {
                prefetchInProgress = false;
            }
        });
        prefetchThread.setDaemon(true);
        prefetchThread.start();
    }

    /**
     * Check if a prefetched decision is available (without consuming it)
     */
    public boolean hasPrefetchedDecision() {
        return prefetchedDecision != null;
    }

    /**
     * Get prefetched decision if available.
     * Returns null if no prefetch is ready.
     * NOTE: This consumes the prefetched decision (sets it to null)
     */
    public AttackDecision getPrefetchedDecision() {
        if (prefetchedDecision != null) {
            AttackDecision result = prefetchedDecision;
            prefetchedDecision = null;
            return result;
        }
        return null;
    }

    /**
     * Check if prefetch is currently in progress
     */
    public boolean isPrefetchInProgress() {
        return prefetchInProgress;
    }

    /**
     * Get last valid decision (fallback when prefetch not ready)
     */
    public AttackDecision getLastValidDecision() {
        return lastValidDecision;
    }

    private String buildPrompt(List<Position> sentinelPositions, Position waspPosition, int mapWidth, int mapHeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI controlling a wasp predator in a beehive simulation game. ");
        sb.append("Your goal is to hunt sentinel bees efficiently.\n\n");
        sb.append("GAME RULES:\n");
        sb.append("- ATTACK: You can kill 1 or 2 sentinels if they are within 50px of you\n");
        sb.append("- DANGER: If 2+ sentinels are within 100px, they will counter-attack and damage you\n");
        sb.append("- STRATEGY: Target isolated sentinels (exactly 1 alone), avoid groups of 2+\n");
        sb.append("- Map size: " + mapWidth + "x" + mapHeight + "\n");
        sb.append("- Hive location is at bottom-right area (around 649-799x, 449-599y) - avoid entering!\n\n");

        sb.append("CURRENT STATE:\n");
        sb.append("- Your position: (" + waspPosition.getX() + ", " + waspPosition.getY() + ")\n");
        sb.append("- Sentinel positions:\n");

        for (int i = 0; i < sentinelPositions.size(); i++) {
            Position p = sentinelPositions.get(i);
            sb.append("  Sentinel " + (i + 1) + ": (" + p.getX() + ", " + p.getY() + ")\n");
        }

        sb.append("\nANALYZE and choose the BEST attack position that:\n");
        sb.append("1. Has exactly 1 sentinel within 50px (safe kill)\n");
        sb.append("2. Has fewer than 2 sentinels within 100px (avoid counter-attack)\n");
        sb.append("3. Targets sentinels OUTSIDE the hive (they cannot retreat)\n\n");

        sb.append("RESPOND IN THIS EXACT FORMAT:\n");
        sb.append("TARGET_X: <number>\n");
        sb.append("TARGET_Y: <number>\n");
        sb.append("REASONING: <brief explanation in one line>\n");

        return sb.toString();
    }

    private String callGeminiAPI(String prompt) throws Exception {
        URL url = java.net.URI.create(API_URL + "?key=" + apiKey).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        // Build request body
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Disable thinking mode for faster responses (gemini-2.5-flash defaults to thinking)
        JsonObject generationConfig = new JsonObject();
        JsonObject thinkingConfig = new JsonObject();
        thinkingConfig.addProperty("thinkingBudget", 0);
        generationConfig.add("thinkingConfig", thinkingConfig);
        requestBody.add("generationConfig", generationConfig);

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = gson.toJson(requestBody).getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println("[GeminiService] API call successful");
            return response.toString();
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                error.append(line);
            }
            throw new Exception("API error " + responseCode + ": " + error.toString());
        }
    }

    private AttackDecision parseResponse(String jsonResponse, List<Position> sentinelPositions, Position waspPosition) {
        try {
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray candidates = response.getAsJsonArray("candidates");
            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject contentObj = candidate.getAsJsonObject("content");
                JsonArray partsArr = contentObj.getAsJsonArray("parts");
                if (partsArr != null && partsArr.size() > 0) {
                    String text = partsArr.get(0).getAsJsonObject().get("text").getAsString();

                    int targetX = -1, targetY = -1;
                    String reasoning = "LLM decision";

                    for (String line : text.split("\n")) {
                        line = line.trim();
                        if (line.startsWith("TARGET_X:")) {
                            targetX = extractNumber(line);
                        } else if (line.startsWith("TARGET_Y:")) {
                            targetY = extractNumber(line);
                        } else if (line.startsWith("REASONING:")) {
                            reasoning = line.substring("REASONING:".length()).trim();
                        }
                    }

                    if (targetX >= 0 && targetY >= 0) {
                        System.out.println("[GeminiService] LLM Strategy → Target:(" + targetX + "," + targetY + ") " + reasoning);
                        AttackDecision d = new AttackDecision(targetX, targetY, reasoning);
                        d.rawResponse = jsonResponse;
                        d.isFallback  = false;
                        return d;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GeminiService] Parse error: " + e.getMessage());
        }

        AttackDecision fb = getDefaultDecision(sentinelPositions, waspPosition);
        fb.rawResponse = jsonResponse; // keep raw even when parsing failed
        return fb;
    }

    private int extractNumber(String line) {
        try {
            // Extract the first integer token after the colon
            String afterColon = line.contains(":") ? line.substring(line.indexOf(':') + 1).trim() : line.trim();
            String numStr = afterColon.replaceAll("[^0-9]", "");
            if (numStr.isEmpty()) return -1;
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Fallback strategy when LLM is unavailable - find closest cluster of sentinels
     */
    private AttackDecision getDefaultDecision(List<Position> sentinelPositions, Position waspPosition) {
        if (sentinelPositions.isEmpty()) {
            return new AttackDecision(waspPosition.getX(), waspPosition.getY(), "No sentinels found");
        }

        // Find the position with most sentinels in a 100-pixel radius
        int bestX = 0, bestY = 0;
        int bestCount = 0;

        for (Position p : sentinelPositions) {
            int count = 0;
            for (Position other : sentinelPositions) {
                double dist = Math.sqrt(Math.pow(p.getX() - other.getX(), 2) + Math.pow(p.getY() - other.getY(), 2));
                if (dist <= 100) {
                    count++;
                }
            }
            // Prefer clusters of 2-3 (avoid 4+ to minimize counter-attack risk)
            if ((count >= 1 && count <= 3 && count > bestCount) ||
                    (bestCount == 0 || (bestCount > 3 && count <= 3))) {
                bestCount = count;
                bestX = p.getX();
                bestY = p.getY();
            }
        }

        AttackDecision d = new AttackDecision(bestX, bestY, "Fallback: targeting cluster of " + bestCount);
        d.isFallback = true;
        return d;
    }
}
