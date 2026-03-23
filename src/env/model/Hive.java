package model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import artifact.Parameters;
import javafx.scene.shape.Rectangle;
import model.enumeration.BeeRole;
import model.enumeration.HoneySupply;
import model.exception.InsufficientHoneyException;
import model.exception.InsufficientPollenException;

public class Hive {
	private static Hive instance;
	private static int maxHoney = Parameters.MAX_HIVE_HONEY;

	private int temperature;

	private List<Bee> queens = new ArrayList<>();
	private List<Bee> feeders = new ArrayList<>();
	private List<Bee> sentinels = new ArrayList<>();
	private List<Bee> workers = new ArrayList<>();
	private List<Larva> larvas = new ArrayList<>();
	private Queue<Larva> feedOrder = new LinkedList<>();

	private int honey;
	private int pollen;

	private Rectangle hive;

	private Hive() {
	}

	public HoneySupply getStatus() {
		if (honey < maxHoney * Parameters.HIVE_SUPPLY_EMPTY)
			return HoneySupply.EMPTY;
		else if (honey < maxHoney * Parameters.HIVE_SUPPLY_LOW)
			return HoneySupply.LOW;
		else if (honey < maxHoney * Parameters.HIVE_SUPPLY_MEDIUM)
			return HoneySupply.MEDIUM;
		else if (honey < maxHoney * Parameters.HIVE_SUPPLY_HIGH)
			return HoneySupply.HIGH;
		else
			return HoneySupply.FULL;
	}

	public static Hive getInstance() {
		if (instance == null)
			instance = new Hive();

		return instance;
	}

	public Rectangle getHiveRect() {
		return hive;
	}

	public void setHiveRect(Rectangle rect) {
		hive = rect;
	}

	public void setHiveRect(int x, int y, int width, int heigth) {
		hive = new Rectangle(x, y, width, heigth);
	}

	public int getTemperature() {
		return temperature;
	}

	public void setTemperature(int temperature) {
		this.temperature = temperature;
	}

	public List<Bee> getFeeders() {
		return feeders;
	}

	public List<Larva> getLarvas() {
		return larvas;
	}

	public int getHoney() {
		return honey;
	}

	public void setHoney(int honey) {
		this.honey = honey;
	}

	public int getPollen() {
		return pollen;
	}

	public void setPollen(int pollen) {
		this.pollen = pollen;
	}

	public List<Bee> getQueens() {
		return queens;
	}

	public List<Bee> getSentinels() {
		return sentinels;
	}

	public List<Bee> getWorkers() {
		return workers;
	}

	public void createLarva() {
		Larva larva = new Larva();
		larvas.add(larva);
		getFeedOrder().add(larva);
	}

	public void addHoney(int ammount) {
		honey = honey + ammount;
	}

	public void subHoney(int ammount) throws InsufficientHoneyException {
		if (honey >= ammount) {
			honey = honey - ammount;
		} else
			throw new InsufficientHoneyException("The honey is gone.");
	}

	public void addPollen(int ammount) {
		pollen = pollen + ammount;
	}

	public void subPollen(int ammount) throws InsufficientPollenException {
		if (pollen >= ammount) {
			pollen = pollen - ammount;
		} else
			throw new InsufficientPollenException("The pollen is gone.");
	}

	public Bee createBee(String beeId, String role) {
		BeeRole beeRole = BeeRole.valueOf(role);
		Bee bee = new Bee(beeId, beeRole);
		addBeeRole(beeRole, bee);
		return bee;
	}

	private void addBeeRole(BeeRole role, Bee bee) {
		switch (role) {
			case sentinel:
				sentinels.add(bee);
				break;
			case nurse:
				feeders.add(bee);
				break;
			case monarch:
				queens.add(bee);
				break;
			default:
				workers.add(bee);
				break;
		}
	}

	public void removeBeeRole(BeeRole role, Bee bee) {
		switch (role) {
			case sentinel:
				sentinels.remove(bee);
				break;
			case nurse:
				feeders.remove(bee);
				break;
			case monarch:
				queens.remove(bee);
				break;
			default:
				workers.remove(bee);
				break;
		}
	}

	public void changeRole(Bee bee, String role) {
		removeBeeRole(bee.getRole(), bee);

		BeeRole newRole = BeeRole.valueOf(role);
		addBeeRole(newRole, bee);
		bee.setRole(newRole);
	}

	public Larva feedLarva() throws InsufficientHoneyException {
		if (getFeedOrder().isEmpty())
			return null;

		int honey = Parameters.LARVA_AMMOUNT_HONEY_FEED;

		subHoney(honey);
		Larva larva = getFeedOrder().poll();
		larva.feed(honey);

		if (!larva.isEvolving()) {
			getFeedOrder().add(larva);
			return null;
		}

		return larva;
	}

	synchronized public Queue<Larva> getFeedOrder() {
		return feedOrder;
	}

	public void removeLarva(Larva larva) {
		larvas.remove(larva);
	}
}