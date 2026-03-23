package graphic;

import javafx.application.Platform;

public class JavaFXConcurrent {
	private static JavaFXConcurrent instance;

	private JavaFXConcurrent() {
	}

	public void addUpdate(Runnable runnable) {
		Platform.runLater(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				System.err.println("[JavaFXConcurrent] Error during UI update: " + e.getMessage());
			}
		});
	}

	public static JavaFXConcurrent getInstance() {
		if (instance == null)
			instance = new JavaFXConcurrent();
		return instance;
	}
}
