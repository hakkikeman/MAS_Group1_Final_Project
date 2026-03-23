package graphic;

import java.util.ArrayList;
import java.util.List;

import graphic.model.HiveGraphic;
import graphic.model.PollenFieldGraphic;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;

import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import model.Hive;
import model.PollenField;
import model.enumeration.HoneySupply;

public class EnvironmentApplication extends Application {
	private static EnvironmentApplication instance = null;
	private int width;
	private int height;
	private Group ground;

	private Text labelNumberTemp;
	private Text labelNumberBeeQueen;
	private Text labelNumberBeeFeeder;
	private Text labelNumberBeeSentinel;
	private Text labelNumberBeeWorker;
	private Text labelNumberBeeLarva;
	private Rectangle rectangleHoneyGreen;
	private Rectangle rectangleHoneyYellow;
	private Rectangle rectangleHoneyOrange;
	private Rectangle rectangleHoneyRed;

	private Color colorWhite = Color.web("white", 1);
	private Color colorBlack = Color.web("black", 1);
	private Color colorRed = Color.web("red", 1);
	private Color colorOrange = Color.web("rgb(255,126,38)", 1);
	private Color colorYellowStrong = Color.web("rgb(255,202,14)", 1);
	private Color colorGreen = Color.web("green", 1);
	private Color colorYellow = Color.web("yellow", 1);

	private Font font = new Font(14);
	private Text time;
	private Text day;
	private HiveGraphic hiveGraphic;

	private Text extTemp;
	private BooleanProperty stop = new SimpleBooleanProperty(false);
	private int second = 0;
	private int minute = 0;
	public static Color colorBeeFeeder;
	public static Color colorBeeSentinel;
	public static Color colorBeeWorker;
	public static Color colorBeeQueen;

	public EnvironmentApplication() {
		System.out.println("Creating BeeEnvironment");
		setStartUpTest(this);
	}

	public static EnvironmentApplication getInstance() {
		return instance;
	}

	public static void setStartUpTest(EnvironmentApplication newInstance) {
		instance = newInstance;
	}

	@Override
	public void start(Stage stage) throws Exception {
		System.out.println("Starting graphics");

		Group root = new Group();
		this.width = Integer.valueOf(getParameters().getRaw().get(0));
		this.height = Integer.valueOf(getParameters().getRaw().get(1));
		Scene scene = new Scene(root, width + 230, height, Color.BLACK);
		stage.setScene(scene);

		root.getChildren().add(createHive());
		root.getChildren().add(createPollenFields(Environment.getInstance().getPollenFields()));

		Text labelTime = new Text(690, 16, "Time: ");
		labelTime.setFill(colorWhite);
		labelTime.setFont(font);
		root.getChildren().add(labelTime);

		time = new Text(740, 16, "0");
		time.setFill(colorWhite);
		time.setFont(font);
		root.getChildren().add(time);

		Text labelDay = new Text(690, 30, "Day: ");
		labelDay.setFill(colorWhite);
		labelDay.setFont(font);
		root.getChildren().add(labelDay);

		day = new Text(780, 30, "0");
		day.setFill(colorWhite);
		day.setFont(font);
		root.getChildren().add(day);

		Text labelExtTemp = new Text(690, 44, "Temperature: ");
		labelExtTemp.setFill(colorWhite);
		labelExtTemp.setFont(font);
		root.getChildren().add(labelExtTemp);

		extTemp = new Text(780, 44, "25℃");
		extTemp.setFill(colorWhite);
		extTemp.setFont(font);
		root.getChildren().add(extTemp);

		Rectangle spaceLabel = createRectangle(20, 600, colorWhite, 810, 0);
		root.getChildren().add(spaceLabel);

		root.getChildren().add(createLabels());

		ground = new Group();
		root.getChildren().add(ground);

		stage.show();

		startTimer();
	}

	private Group createLabels() {
		Group group = new Group();
		int startX = 831;
		int startY = 30;
		int lineHeight = 20;
		int currentY = startY;

		// Bee type labels (including Wasp predator)
		String[] beeLabels = { "Queen Bee", "Nurse Bee", "Worker Bee", "Sentinel Bee", "Larva", "Wasp (LLM)" };
		Color[] beeColors = { colorBeeQueen, colorBeeFeeder, colorBeeSentinel, colorBeeWorker,
				Color.web("rgb(193,193,193)", 1), Color.web("rgb(180,0,0)", 1) }; // Wasp is dark red

		for (int i = 0; i < beeLabels.length; i++) {
			// Special handling for Wasp - white circle with black "LLM" text
			if (beeLabels[i].equals("Wasp (LLM)")) {
				Circle circle = new Circle(8, Color.WHITE); // Larger white circle
				circle.setStroke(Color.BLACK);
				circle.setStrokeWidth(1);
				circle.setLayoutX(startX + 5);
				circle.setLayoutY(currentY);
				group.getChildren().add(circle);

				// Bold black "LLM" text inside circle
				Text llmText = new Text(startX - 4, currentY + 3, "LLM");
				llmText.setFill(Color.BLACK);
				llmText.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 7));
				group.getChildren().add(llmText);
			} else {
				Circle circle = new Circle(4, beeColors[i]);
				circle.setLayoutX(startX + 5);
				circle.setLayoutY(currentY);
				group.getChildren().add(circle);
			}

			Text label = new Text(startX + 15, currentY + 4, beeLabels[i]);
			label.setFill(colorWhite);
			label.setFont(font);
			group.getChildren().add(label);
			currentY += lineHeight;
		}

		currentY += 10;

		// Field type labels
		String[] fieldLabels = { "Hive", "Empty Field", "Low Pollen", "Medium Pollen", "Full Pollen" };
		Color[] fieldColors = { colorYellow, colorWhite, Color.web("rgb(255,200,200)", 1),
				Color.web("rgb(255,150,150)", 1), colorRed };

		for (int i = 0; i < fieldLabels.length; i++) {
			Rectangle rect = new Rectangle(10, 10, fieldColors[i]);
			rect.setLayoutX(startX);
			rect.setLayoutY(currentY - 5);
			group.getChildren().add(rect);

			Text label = new Text(startX + 15, currentY + 4, fieldLabels[i]);
			label.setFill(colorWhite);
			label.setFont(font);
			group.getChildren().add(label);
			currentY += lineHeight;
		}

		currentY += 10;

		// Honey stock label
		Text honeyLabel = new Text(startX, currentY + 4, "Honey Stock");
		honeyLabel.setFill(colorWhite);
		honeyLabel.setFont(font);
		group.getChildren().add(honeyLabel);

		return group;
	}

	private Group createPollenFields(List<PollenField> pollenFields) {
		Group group = new Group();
		PollenFieldResolver pollenFieldResolver = Environment.getInstance().getPollenFieldResolver();
		List<PollenFieldGraphic> pollenFieldGraphics = new ArrayList<>();

		for (PollenField pollenField : pollenFields) {
			Rectangle foodSource1 = createRectangle(pollenField.getWidth(), pollenField.getHeight(),
					pollenField.getStatus().getColor(), pollenField.getPosition().getX(),
					pollenField.getPosition().getY());
			group.getChildren().add(foodSource1);

			PollenFieldGraphic pollenFieldGraphic = new PollenFieldGraphic(foodSource1, pollenField);
			pollenFieldResolver.createPollenField(pollenField.getId(), pollenFieldGraphic);
			pollenFieldGraphics.add(pollenFieldGraphic);

		}
		Environment.getInstance().getMapResolver().addContainers(hiveGraphic, pollenFieldGraphics);

		return group;
	}

	private Group createHive() {
		Group group = new Group();
		Rectangle hive = createRectangle(artifact.Parameters.HIVE_WIDTH, artifact.Parameters.HIVE_HEIGHT, colorYellow,
				artifact.Parameters.HIVE_X, artifact.Parameters.HIVE_Y);
		group.getChildren().add(hive);
		hiveGraphic = new HiveGraphic(hive, "hive");

		group.getChildren().add(createHiveInformation());
		return group;
	}

	private Group createHiveInformation() {
		Group group = new Group();
		// Rectangle rectangleInfo = createRectangle(70, 130, colorBlack, 726, 460);
		// group.getChildren().add(rectangleInfo);
		int newX = 185;

		Text labelTemp = new Text(731 + newX, 473, "Temp: ");
		labelTemp.setFill(colorWhite);
		labelTemp.setFont(font);
		labelNumberTemp = new Text(771 + newX, 473, "0");
		labelNumberTemp.setFill(colorWhite);
		labelNumberTemp.setFont(font);
		Text labelTemp2 = new Text(785 + newX, 473, "℃");
		labelTemp2.setFill(colorWhite);
		labelTemp2.setFont(font);
		group.getChildren().add(labelTemp);
		group.getChildren().add(labelNumberTemp);
		group.getChildren().add(labelTemp2);

		colorBeeQueen = Color.web("rgb(158,76,158)", 1);
		Circle circleBeeQueen = new Circle(4, colorBeeQueen);
		circleBeeQueen.setLayoutX(736 + newX);
		circleBeeQueen.setLayoutY(486);
		group.getChildren().add(circleBeeQueen);
		labelNumberBeeQueen = new Text(746 + newX, 490, "0");
		labelNumberBeeQueen.setFill(colorWhite);
		labelNumberBeeQueen.setFont(font);
		group.getChildren().add(labelNumberBeeQueen);

		colorBeeFeeder = Color.web("rgb(156,207,224)", 1);
		Circle circleBeeFeeder = new Circle(4, colorBeeFeeder);
		circleBeeFeeder.setLayoutX(736 + newX);
		circleBeeFeeder.setLayoutY(506);
		group.getChildren().add(circleBeeFeeder);
		labelNumberBeeFeeder = new Text(746 + newX, 510, "0");
		labelNumberBeeFeeder.setFill(colorWhite);
		labelNumberBeeFeeder.setFont(font);
		group.getChildren().add(labelNumberBeeFeeder);

		colorBeeSentinel = Color.web("rgb(60,61,205)", 1);
		Circle circleBeeSentinel = new Circle(4, colorBeeSentinel);
		circleBeeSentinel.setLayoutX(736 + newX);
		circleBeeSentinel.setLayoutY(526);
		group.getChildren().add(circleBeeSentinel);
		labelNumberBeeSentinel = new Text(746 + newX, 530, "0");
		labelNumberBeeSentinel.setFill(colorWhite);
		labelNumberBeeSentinel.setFont(font);
		group.getChildren().add(labelNumberBeeSentinel);

		colorBeeWorker = Color.web("rgb(255,128,35)", 1);
		Circle circleBeeWorker = new Circle(4, colorBeeWorker);
		circleBeeWorker.setLayoutX(736 + newX);
		circleBeeWorker.setLayoutY(546);
		group.getChildren().add(circleBeeWorker);
		labelNumberBeeWorker = new Text(746 + newX, 550, "0");
		labelNumberBeeWorker.setFill(colorWhite);
		labelNumberBeeWorker.setFont(font);
		group.getChildren().add(labelNumberBeeWorker);

		Circle circleBeeLarva = new Circle(4, Color.web("rgb(193,193,193)", 1));
		circleBeeLarva.setLayoutX(736 + newX);
		circleBeeLarva.setLayoutY(566);
		group.getChildren().add(circleBeeLarva);
		labelNumberBeeLarva = new Text(746 + newX, 570, "0");
		labelNumberBeeLarva.setFill(colorWhite);
		labelNumberBeeLarva.setFont(font);
		group.getChildren().add(labelNumberBeeLarva);

		Rectangle rectangleHoney = createRectangle(25, 48, colorBlack, 666 + newX, 460);
		rectangleHoney.setStrokeType(StrokeType.OUTSIDE);
		Color greyBorder = Color.web("rgb(137,137,137)", 1);
		rectangleHoney.setStroke(greyBorder);
		rectangleHoney.setStrokeWidth(1);
		group.getChildren().add(rectangleHoney);

		Line line1Honey = new Line(666 + newX, 472, 691 + newX, 472);
		line1Honey.setStrokeType(StrokeType.OUTSIDE);
		line1Honey.setStroke(greyBorder);
		line1Honey.setStrokeWidth(1);
		group.getChildren().add(line1Honey);

		Line line2Honey = new Line(666 + newX, 484, 691 + newX, 484);
		line2Honey.setStrokeType(StrokeType.OUTSIDE);
		line2Honey.setStroke(greyBorder);
		line2Honey.setStrokeWidth(1);
		group.getChildren().add(line2Honey);

		Line line3Honey = new Line(666 + newX, 496, 691 + newX, 496);
		line3Honey.setStrokeType(StrokeType.OUTSIDE);
		line3Honey.setStroke(greyBorder);
		line3Honey.setStrokeWidth(1);
		group.getChildren().add(line3Honey);

		rectangleHoneyGreen = createRectangle(25, 12, colorBlack, 666 + newX, 460);
		group.getChildren().add(rectangleHoneyGreen);

		rectangleHoneyYellow = createRectangle(25, 10, colorBlack, 666 + newX, 473);
		group.getChildren().add(rectangleHoneyYellow);

		rectangleHoneyOrange = createRectangle(25, 10, colorBlack, 666 + newX, 485);
		group.getChildren().add(rectangleHoneyOrange);

		rectangleHoneyRed = createRectangle(25, 11, colorBlack, 666 + newX, 497);
		group.getChildren().add(rectangleHoneyRed);

		return group;
	}

	private Rectangle createRectangle(int width, int height, Color color, int x, int y) {
		Rectangle rectangle = new Rectangle(width, height, color);
		rectangle.setLayoutX(x);
		rectangle.setLayoutY(y);
		return rectangle;
	}

	public void run(String args[]) {
		launch(args);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void updateIntTemperature(int ammount) {
		labelNumberTemp.setText(ammount + "℃");
	}

	public void updateLarvaCount() {
		labelNumberBeeLarva.setText(Hive.getInstance().getLarvas().size() + "");
	}

	public void updateBeeCount() {
		labelNumberBeeQueen.setText(Hive.getInstance().getQueens().size() + "");
		labelNumberBeeFeeder.setText(Hive.getInstance().getFeeders().size() + "");
		labelNumberBeeSentinel.setText(Hive.getInstance().getSentinels().size() + "");
		labelNumberBeeWorker.setText(Hive.getInstance().getWorkers().size() + "");
	}

	public void updateHoneyStatus(HoneySupply newStatus) {

		switch (newStatus) {
			case EMPTY:
				rectangleHoneyRed.setFill(colorBlack);
				rectangleHoneyOrange.setFill(colorBlack);
				rectangleHoneyYellow.setFill(colorBlack);
				rectangleHoneyGreen.setFill(colorBlack);
				break;
			case LOW:
				rectangleHoneyRed.setFill(colorRed);
				rectangleHoneyOrange.setFill(colorBlack);
				rectangleHoneyYellow.setFill(colorBlack);
				rectangleHoneyGreen.setFill(colorBlack);
				break;
			case MEDIUM:
				rectangleHoneyRed.setFill(colorRed);
				rectangleHoneyOrange.setFill(colorOrange);
				rectangleHoneyYellow.setFill(colorBlack);
				rectangleHoneyGreen.setFill(colorBlack);
				break;
			case HIGH:
				rectangleHoneyRed.setFill(colorRed);
				rectangleHoneyOrange.setFill(colorOrange);
				rectangleHoneyYellow.setFill(colorYellowStrong);
				rectangleHoneyGreen.setFill(colorBlack);
				break;
			case FULL:
				rectangleHoneyRed.setFill(colorRed);
				rectangleHoneyOrange.setFill(colorOrange);
				rectangleHoneyYellow.setFill(colorYellowStrong);
				rectangleHoneyGreen.setFill(colorGreen);
				break;
		}
	}

	private void startTimer() {
		Task<Void> t = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				while (!stop.get()) {
					second++;
					if (second == 60) {
						minute++;
						second = 0;
					}
					String min = minute <= 9 ? "0" + minute : minute + "";
					String sec = second <= 9 ? "0" + second : second + "";

					JavaFXConcurrent.getInstance().addUpdate(new Runnable() {
						@Override
						public void run() {
							time.setText(min + ":" + sec);
						}
					});
					Thread.sleep(1000);
				}
				return null;
			}
		};
		new Thread(t).start();
	}

	public void updateDay(int newDay) {
		day.setText(newDay + "");
	}

	public void updateExtTemp(int newTemp) {
		extTemp.setText(newTemp + "℃");
	}

	public void removeBee(Node node) {
		try {
			ground.getChildren().remove(node);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addBee(Node node) {
		ground.getChildren().add(node);
	}

	public void updatePollenFieldStatus(String pollenFieldId) {
		PollenFieldGraphic pollenField = Environment.getInstance().getPollenFieldResolver()
				.getPollenField(pollenFieldId);
		pollenField.getRectangle().setFill(pollenField.getPollenField().getStatus().getColor());
	}

	/* ========== WASP BATTLE VISUALIZATION ========== */

	private Circle waspCircle;
	private Text waspLLMText; // LLM text inside wasp circle
	private Rectangle waspHealthBar;
	private Rectangle waspHealthBarBg;
	private Text waspLabel;
	private Group victoryOverlay;
	private static final int WASP_SIZE = 14; // Slightly larger for LLM text
	private static final Color WASP_COLOR = Color.WHITE; // White circle

	/**
	 * Add wasp visual representation to the scene
	 * White circle with black bold "LLM" text inside
	 */
	public void addWasp(model.Wasp wasp) {
		// Create wasp circle (WHITE with black border)
		waspCircle = new Circle(WASP_SIZE, WASP_COLOR);
		waspCircle.setLayoutX(wasp.getPosition().getX());
		waspCircle.setLayoutY(wasp.getPosition().getY());
		waspCircle.setStroke(Color.BLACK);
		waspCircle.setStrokeWidth(2);

		// Create bold black "LLM" text inside the circle
		waspLLMText = new Text("LLM");
		waspLLMText.setFill(Color.BLACK);
		waspLLMText.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 9));
		waspLLMText.setLayoutX(wasp.getPosition().getX() - 10);
		waspLLMText.setLayoutY(wasp.getPosition().getY() + 4);

		// Create health bar background
		waspHealthBarBg = new Rectangle(30, 6, Color.DARKGRAY);
		waspHealthBarBg.setLayoutX(wasp.getPosition().getX() - 15);
		waspHealthBarBg.setLayoutY(wasp.getPosition().getY() - 25);
		waspHealthBarBg.setStroke(Color.BLACK);
		waspHealthBarBg.setStrokeWidth(1);

		// Create health bar (green)
		waspHealthBar = new Rectangle(30, 6, colorGreen);
		waspHealthBar.setLayoutX(wasp.getPosition().getX() - 15);
		waspHealthBar.setLayoutY(wasp.getPosition().getY() - 25);

		// Create label - Bold, centered, closer to health bar
		waspLabel = new Text("WASP (LLM)");
		waspLabel.setFill(Color.WHITE);
		waspLabel.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10));
		// Center the label above the health bar
		waspLabel.setLayoutX(wasp.getPosition().getX() - 25);
		waspLabel.setLayoutY(wasp.getPosition().getY() - 28); // Closer to health bar

		ground.getChildren().addAll(waspHealthBarBg, waspHealthBar, waspCircle, waspLLMText, waspLabel);

		System.out.println("[UI] Wasp added to display at (" +
				wasp.getPosition().getX() + ", " + wasp.getPosition().getY() + ")");
	}

	/**
	 * Update wasp position on screen
	 */
	public void updateWaspPosition(model.Position newPosition) {
		if (waspCircle != null) {
			waspCircle.setLayoutX(newPosition.getX());
			waspCircle.setLayoutY(newPosition.getY());

			if (waspHealthBarBg != null) {
				waspHealthBarBg.setLayoutX(newPosition.getX() - 15);
				waspHealthBarBg.setLayoutY(newPosition.getY() - 25);
			}

			if (waspHealthBar != null) {
				waspHealthBar.setLayoutX(newPosition.getX() - 15);
				waspHealthBar.setLayoutY(newPosition.getY() - 25);
			}

			if (waspLabel != null) {
				waspLabel.setLayoutX(newPosition.getX() - 20);
				waspLabel.setLayoutY(newPosition.getY() - 30);
			}

			// Update LLM text position (inside circle)
			if (waspLLMText != null) {
				waspLLMText.setLayoutX(newPosition.getX() - 10);
				waspLLMText.setLayoutY(newPosition.getY() + 4);
			}
		}
	}

	/**
	 * Update wasp health bar
	 */
	public void updateWaspHealth(int health, int maxHealth) {
		if (waspHealthBar != null) {
			double healthPercent = (double) health / maxHealth;
			waspHealthBar.setWidth(30 * healthPercent);

			// Change color based on health
			if (healthPercent > 0.6) {
				waspHealthBar.setFill(colorGreen);
			} else if (healthPercent > 0.3) {
				waspHealthBar.setFill(colorYellowStrong);
			} else {
				waspHealthBar.setFill(colorRed);
			}
		}
	}

	/**
	 * Show victory/defeat screen
	 * 
	 * @param waspWon true if wasp won, false if sentinels won
	 */
	public void showVictoryScreen(boolean waspWon) {
		victoryOverlay = new Group();

		// Semi-transparent background
		Rectangle bg = new Rectangle(width + 230, height, Color.web("rgba(0,0,0,0.7)"));
		victoryOverlay.getChildren().add(bg);

		// Victory text
		String victoryText;
		Color textColor;
		if (waspWon) {
			victoryText = "🐝 LLM-based Wasp Agent WINNER! 🐝";
			textColor = Color.RED;
		} else {
			victoryText = "🔵 JaCaMo-based Sentinels WINNER! 🔵";
			textColor = Color.CYAN;
		}

		Text title = new Text(victoryText);
		title.setFont(new Font("Arial Bold", 32));
		title.setFill(textColor);
		title.setLayoutX((width + 230) / 2 - 220);
		title.setLayoutY(height / 2 - 20);
		victoryOverlay.getChildren().add(title);

		// Subtitle
		String subtitle = waspWon ? "All sentinels have been eliminated!"
				: "The wasp has been defeated by collective defense!";
		Text sub = new Text(subtitle);
		sub.setFont(new Font("Arial", 18));
		sub.setFill(Color.WHITE);
		sub.setLayoutX((width + 230) / 2 - 180);
		sub.setLayoutY(height / 2 + 20);
		victoryOverlay.getChildren().add(sub);

		// Battle summary
		Text summary = new Text("Battle Complete - Multi-Agent System Demonstration");
		summary.setFont(new Font("Arial Italic", 14));
		summary.setFill(Color.LIGHTGRAY);
		summary.setLayoutX((width + 230) / 2 - 160);
		summary.setLayoutY(height / 2 + 60);
		victoryOverlay.getChildren().add(summary);

		ground.getChildren().add(victoryOverlay);

		System.out.println("[UI] Victory screen displayed: " + (waspWon ? "Wasp wins" : "Sentinels win"));
	}
}