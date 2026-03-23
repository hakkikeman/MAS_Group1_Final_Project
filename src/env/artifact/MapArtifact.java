// CArtAgO artifact code for project melissa

package artifact;

import java.util.List;

import cartago.Artifact;
import cartago.INTERNAL_OPERATION;
import cartago.OPERATION;
import cartago.ObsProperty;
import graphic.Environment;
import model.PollenField;
import model.Position;
import model.enumeration.Direction;
import model.exception.CannotCollectOnThisPositionException;
import model.exception.InvalidMovimentException;
import model.exception.MovimentOutOfBoundsException;
import model.exception.NoLongerPollenFieldException;
import model.exception.PollenIsOverException;

public class MapArtifact extends Artifact {
	void init() {
		List<PollenField> pollenFields = Parameters.makePollenFields();

		Environment.getInstance().launchGraphicApplication(800, 600, pollenFields);

		defineObsProperty("day", 0);
		defineObsProperty("extTemperature", Parameters.EXTERNAL_TEMPERATURE_START);

		// x, y, width, height
		defineObsProperty("hive", Parameters.HIVE_X, Parameters.HIVE_Y, Parameters.HIVE_WIDTH, Parameters.HIVE_HEIGHT);

		for (PollenField pollenField : pollenFields) {
			defineObsProperty("pollenField", pollenField.getStatus().toString(), pollenField.getPosition().getX(),
					pollenField.getPosition().getY(), pollenField.getWidth(), pollenField.getHeight());
		}

		execInternalOp("dayChange");
		execInternalOp("temperatureChange");
	}

	@OPERATION
	void registerBee(String role) {
		Environment.getInstance().registerBee(getCurrentOpAgentId().getAgentName(), role);
	}

	@OPERATION
	void changeRole(String newRole) {
		Environment.getInstance().changeRole(getCurrentOpAgentId().getAgentName(), newRole);
	}

	@OPERATION
	void unRegisterBee() {
		Environment.getInstance().unRegisterBee(getCurrentOpAgentId().getAgentName());
	}

	@OPERATION
	void move(String direction) {
		try {
			Environment.getInstance().moveBee(getCurrentOpAgentId().getAgentName(),
					Direction.valueOf(direction.toUpperCase()));
		} catch (MovimentOutOfBoundsException | InvalidMovimentException e) {
			failed(e.getMessage());
		} catch (NullPointerException e) {
			// Silently ignore - bee was likely killed
		}
		await_time(Parameters.DELAY_MOVE_OPERATION);
	}

	@OPERATION
	void move(int x, int y) {
		try {
			Environment.getInstance().moveBee(getCurrentOpAgentId().getAgentName(), x, y);
		} catch (MovimentOutOfBoundsException | InvalidMovimentException e) {
			failed(e.getMessage());
		} catch (NullPointerException e) {
			// Silently ignore - bee was likely killed
		}
		await_time(Parameters.DELAY_MOVE_OPERATION);
	}

	@OPERATION
	void flyTo(int x, int y) {
		String beeId = getCurrentOpAgentId().getAgentName();
		Position beePos;
		while (true) {
			beePos = Environment.getInstance().getBeePos(beeId);
			if (beePos == null) return;
			int i = beePos.getX();
			int j = beePos.getY();
			if (x == i && y == j) break;
			if (Math.abs(y - j) > Math.abs(x - i)) {
				move(i, y - j > 0 ? j + 1 : j - 1);
			} else {
				move(x - i > 0 ? i + 1 : i - 1, j);
			}
		}
	}

	@OPERATION
	void collect(String pollenFieldId) {
		try {
			Environment.getInstance().collect(pollenFieldId, getCurrentOpAgentId().getAgentName());
		} catch (PollenIsOverException | NoLongerPollenFieldException | CannotCollectOnThisPositionException e) {
			failed(e.getMessage());
		}
	}

	@OPERATION
	void collect() {
		try {
			String beeId = getCurrentOpAgentId().getAgentName();
			Position beePos = getPosition(beeId);
			// Silently return if bee is dead or position is null
			if (beePos == null) {
				return;
			}
			String pollenFieldId = Environment.getInstance().getMatchingPollenFieldId(beePos);
			Environment.getInstance().collect(pollenFieldId, beeId);
		} catch (PollenIsOverException | NoLongerPollenFieldException | CannotCollectOnThisPositionException e) {
			failed(e.getMessage());
		} catch (NullPointerException e) {
			// Silently ignore - bee was likely killed
		}
	}

	@OPERATION
	public void setPosition(int x, int y) {
		Environment.getInstance().setPosition(getCurrentOpAgentId().getAgentName(), x, y);
	}

	@INTERNAL_OPERATION
	public Position getPosition(String beeId) {
		return Environment.getInstance().getBeePos(beeId);
	}

	@INTERNAL_OPERATION
	void dayChange() {
		while (true) {
			await_time(Parameters.DELAY_CHANGE_DAY);
			ObsProperty day = getObsProperty("day");
			int newDay = day.intValue() + 1;

			Environment.getInstance().changeDay(newDay);
			day.updateValue(newDay);
			execInternalOp("updatePollenFields");
		}
	}

	@INTERNAL_OPERATION
	void temperatureChange() {
		while (true) {
			await_time(Parameters.DELAY_CHANGE_DAY);
			ObsProperty day = getObsProperty("day");
			int today = day.intValue();

			int month = today % 12; // for seasons of the year
			int newTemperature = (int) (Parameters.AVERAGE_TEMPERATURE
					+ Parameters.TERMIC_AMPLITUDE * Math.sin(2 * Math.PI * (month / 12.)));

			Environment.getInstance().changeExtTemp(newTemperature);

			ObsProperty temperature = getObsProperty("extTemperature");
			temperature.updateValue(newTemperature);
		}
	}

	@INTERNAL_OPERATION
	void updatePollenFields() {
		Environment.getInstance().incrementPollenFields();
	}
}
