package graphic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import graphic.model.BeeGraphic;
import javafx.scene.shape.Circle;
import model.Bee;

public class BeeResolver {
	private final Map<String, BeeGraphic> beeCircles = new ConcurrentHashMap<>();

	public Circle createBee(Bee bee, int positionX, int positionY) {
		Circle circle = new Circle(4, bee.getColor());
		circle.setLayoutX(positionX);
		circle.setLayoutY(positionY);

		beeCircles.put(bee.getId(), new BeeGraphic(circle, bee));
		return circle;
	}

	public BeeGraphic getBee(String beeId) {
		return beeCircles.get(beeId);
	}

	public void removeBee(String beeId) {
		beeCircles.remove(beeId);
	}

	/**
	 * Get a snapshot of all bee IDs currently tracked.
	 * Returns a copy to prevent ConcurrentModificationException during iteration.
	 */
	public Set<String> getAllBeeIds() {
		return new HashSet<>(beeCircles.keySet());
	}
}
