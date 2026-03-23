// CArtAgO artifact code for project melissa

package artifact;

import cartago.Artifact;
import cartago.INTERNAL_OPERATION;
import cartago.OPERATION;
import cartago.ObsProperty;
import cartago.OpFeedbackParam;
import graphic.Environment;
import model.Hive;
import model.Larva;
import model.exception.CannotDepositOnThisPositionException;
import model.exception.InsufficientHoneyException;
import model.exception.InsufficientPollenException;
import model.exception.NoLongerHiveException;
import model.exception.NoPollenCollectedException;

public class HiveArtifact extends Artifact {
	void init() {
		defineObsProperty("pollen", 0);
		defineObsProperty("honey", 0);
		defineObsProperty("intTemperature", 0);
		defineObsProperty("larvas", 0);
		defineObsProperty("heaters", 0);
		defineObsProperty("coolers", 0);
		defineObsProperty("x", 0);
		defineObsProperty("y", 0);
		defineObsProperty("width", 0);
		defineObsProperty("height", 0);

		execInternalOp("temperatureChange");
	}

	@OPERATION
	void hiveStart() {
		honeyStart(Parameters.HONEY_START);
		pollenStart(Parameters.POLLEN_START);
		tempStart(Parameters.TEMPERATURE_START);
		dimStart(Parameters.HIVE_X, Parameters.HIVE_Y, Parameters.HIVE_WIDTH, Parameters.HIVE_HEIGHT);
	}

	void dimStart(int x, int y, int width, int height) {
		updateObsProperty("x", x);
		updateObsProperty("y", y);
		updateObsProperty("width", width);
		updateObsProperty("height", height);
	}

	@OPERATION
	void heat() {
		ObsProperty h = getObsProperty("heaters");
		h.updateValue(h.intValue() + 1);
		updateTemp();
	}

	@OPERATION
	void cool() {
		ObsProperty c = getObsProperty("coolers");
		c.updateValue(c.intValue() + 1);
		updateTemp();
	}

	@OPERATION
	void stop_heat() {
		ObsProperty h = getObsProperty("heaters");
		h.updateValue(h.intValue() - 1);
		updateTemp();
	}

	@OPERATION
	void stop_cool() {
		ObsProperty c = getObsProperty("coolers");
		c.updateValue(c.intValue() - 1);
		updateTemp();
	}

	@OPERATION
	void processPollen() {
		try {
			Environment.getInstance().processPollen(1);
		} catch (InsufficientPollenException e) {
			failed(e.getMessage());
		}
	}

	@OPERATION
	void pollenStart(int ammount) {
		Environment.getInstance().setPollenStart(ammount);
		updateObsProperty("pollen", ammount);
	}

	@OPERATION
	void honeyStart(int ammount) {
		Environment.getInstance().setHoneyStart(ammount);
		updateObsProperty("honey", ammount);
	}

	@OPERATION
	void eat(int ammount) {
		try {
			Environment.getInstance().eat(ammount);
		} catch (InsufficientHoneyException e) {
			failed(e.getMessage());
		}
	}

	@OPERATION
	void createLarva() {
		Environment.getInstance().createLarva();
		updateObsProperty("larvas", Hive.getInstance().getLarvas().size());
	}

	@OPERATION
	void delivery() {
		try {
			Environment.getInstance().delivery(getCurrentOpAgentId().getAgentName());
		} catch (CannotDepositOnThisPositionException | NoLongerHiveException | NoPollenCollectedException e) {
			failed(e.getMessage());
		} catch (NullPointerException e) {
			// Silently ignore - bee was likely killed by Wasp
		}
	}

	@OPERATION
	void tempStart(int ammount) {
		Environment.getInstance().setIntTemp(ammount);
		updateObsProperty("intTemperature", ammount);
	}

	@OPERATION
	void updateTemp() {
		int extTemp = Environment.getInstance().getExtTemperature();
		ObsProperty intTemp = getObsProperty("intTemperature");
		ObsProperty heaters = getObsProperty("heaters");
		ObsProperty coolers = getObsProperty("coolers");
		int heat = heaters.intValue();
		int cool = coolers.intValue();

		int ammount = extTemp + heat - cool;

		if (ammount != intTemp.intValue()) {
			intTemp.updateValue(ammount);
			Environment.getInstance().setIntTemp(ammount);
		}
	}

	@OPERATION
	void feedLarva(OpFeedbackParam<Boolean> isEvolving) {
		try {
			Larva larvaToEvolve = Environment.getInstance().feedLarva();

			if (larvaToEvolve != null) {
				Environment.getInstance().removeLarva(larvaToEvolve);
				isEvolving.set(true);
				updateObsProperty("larvas", Hive.getInstance().getLarvas().size() - 1);
			} else
				isEvolving.set(false);
		} catch (InsufficientHoneyException e) {
			failed(e.getMessage());
		}
	}

	@INTERNAL_OPERATION
	void temperatureChange() {
		while (true) {
			await_time((int) (Parameters.DELAY_CHANGE_DAY / 10));
			updateTemp();
		}
	}
}