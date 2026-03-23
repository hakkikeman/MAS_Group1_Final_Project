// Wasp.java - Wasp predator entity model
package model;

import javafx.scene.paint.Color;

/**
 * Represents the Wasp predator entity in the simulation.
 * The Wasp is an LLM-powered agent that hunts sentinel bees.
 */
public class Wasp {
    private static Wasp instance;

    private String id;
    private Position position;
    private int health;
    private int maxHealth;
    private boolean alive;
    private double speedMultiplier;
    private int attackRadius;
    private int maxKillsPerAttack;
    private long lastBlockMessageTime = 0;

    // Visual properties
    private static final Color WASP_COLOR = Color.web("rgb(180,0,0)", 1); // Dark red
    private static final int WASP_SIZE = 12; // Larger than bees (4px)

    private Wasp() {
        artifact.RunConfig cfg = artifact.RunConfig.getInstance();
        this.id = "wasp";
        this.maxHealth        = cfg.getWaspHealth();
        this.health           = maxHealth;
        this.alive            = true;
        this.speedMultiplier  = 1.0;
        this.attackRadius     = cfg.getWaspAttackRadius();
        this.maxKillsPerAttack= cfg.getWaspMaxKills();
        this.position         = new Position(10, 10);
        System.out.printf("[Wasp] Initialised – hp=%d  radius=%d  maxKills=%d%n",
                maxHealth, attackRadius, maxKillsPerAttack);
    }

    public static synchronized Wasp getInstance() {
        if (instance == null) {
            instance = new Wasp();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public String getId() {
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(int x, int y) {
        this.position = new Position(x, y);
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public double getHealthPercentage() {
        return (double) health / maxHealth;
    }

    public boolean isAlive() {
        return alive && health > 0;
    }

    /**
     * Take damage from sentinel counter-attack
     * 
     * @param damagePercent Percentage of max health to lose (e.g., 25 for 25%)
     */
    public void takeDamage(int damagePercent) {
        int damage = (maxHealth * damagePercent) / 100;
        this.health = Math.max(0, health - damage);
        System.out.println("[Wasp] Took " + damage + " damage! Health: " + health + "/" + maxHealth);
        if (health <= 0) {
            this.alive = false;
            System.out.println("[Wasp] DEFEATED! Sentinels win!");
        }
    }

    /**
     * Take raw damage amount
     */
    public void takeDamageAmount(int damage) {
        this.health = Math.max(0, health - damage);
        System.out.println("[Wasp] Took " + damage + " damage! Health: " + health + "/" + maxHealth);
        if (health <= 0) {
            this.alive = false;
            System.out.println("[Wasp] DEFEATED! Sentinels win!");
        }
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int getAttackRadius() {
        return attackRadius;
    }

    public int getMaxKillsPerAttack() {
        return maxKillsPerAttack;
    }

    public static Color getWaspColor() {
        return WASP_COLOR;
    }

    public static int getWaspSize() {
        return WASP_SIZE;
    }

    /**
     * Calculate distance to another position
     */
    public double distanceTo(Position other) {
        int dx = position.getX() - other.getX();
        int dy = position.getY() - other.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Move toward a target position (with speed multiplier)
     * Wasp cannot enter the hive area
     * 
     * @return true if reached the target
     */
    public boolean moveToward(int targetX, int targetY, int baseSpeed) {
        int effectiveSpeed = (int) (baseSpeed * speedMultiplier);

        int dx = targetX - position.getX();
        int dy = targetY - position.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance <= effectiveSpeed) {
            // Check if target is inside hive - if so, stop before entering
            if (!isInsideHive(targetX, targetY)) {
                position = new Position(targetX, targetY);
            }
            return true;
        }

        // Move toward target
        int moveX = (int) (dx / distance * effectiveSpeed);
        int moveY = (int) (dy / distance * effectiveSpeed);

        int newX = position.getX() + moveX;
        int newY = position.getY() + moveY;

        // Prevent entering hive
        if (isInsideHive(newX, newY)) {
            // Stop at hive boundary or redirect
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBlockMessageTime > 2000) { // Only log every 2 seconds
                System.out.println("[Wasp] Blocked by hive! Cannot enter protected area.");
                lastBlockMessageTime = currentTime;
            }
            return false;
        }

        // Clamp to map bounds (0-800 x 0-600)
        newX = Math.max(0, Math.min(799, newX));
        newY = Math.max(0, Math.min(599, newY));

        position = new Position(newX, newY);
        return false;
    }

    /**
     * Check if position is inside (or within margin of) the hive area.
     */
    private boolean isInsideHive(int x, int y) {
        final int MARGIN = 10;
        return x >= (artifact.Parameters.HIVE_X - MARGIN)
            && x <= (artifact.Parameters.HIVE_X + artifact.Parameters.HIVE_WIDTH + MARGIN)
            && y >= (artifact.Parameters.HIVE_Y - MARGIN)
            && y <= (artifact.Parameters.HIVE_Y + artifact.Parameters.HIVE_HEIGHT + MARGIN);
    }

    @Override
    public String toString() {
        return "Wasp{" +
                "position=" + position +
                ", health=" + health + "/" + maxHealth +
                ", alive=" + alive +
                '}';
    }
}
