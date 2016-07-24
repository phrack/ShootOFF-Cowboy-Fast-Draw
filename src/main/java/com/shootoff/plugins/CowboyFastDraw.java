package com.shootoff.plugins;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.shootoff.camera.Shot;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.util.NamedThreadFactory;

import javafx.geometry.Dimension2D;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;

public class CowboyFastDraw extends ProjectorTrainingExerciseBase implements TrainingExercise {
	private static final int START_DELAY = 10; // s
	private static final int RESUME_DELAY = 5; // s
	private static final int BLINK_DELAY = 700; // ms
	private static final int MIN_DELAY = 2; // s
	private static final int MAX_DELAY = 5; // s
	private static final int CORE_POOL_SIZE = 4;
	private static final String PAUSE = "Pause";
	private static final Color LIGHT_ON_COLOR = Color.CRIMSON;
	private boolean repeatExercise = true;
	private boolean coloredRows = false;
	private Button pauseResumeButton;

	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
			new NamedThreadFactory("CowboyFastDrawExercise"));

	private Target threeB;
	private Shape light;
	private Color lightOffColor = Color.GRAY;
	private int blinkCount = 0;
	private long roundStartTime = 0;

	public CowboyFastDraw() {}

	public CowboyFastDraw(List<Target> targets) {
		super(targets);
	}

	@Override
	public void init() {
		initUI();
		setShotTimerRowColor(Color.LIGHTGRAY);
		addThreeBTarget();

		pauseShotDetection(true);
		executorService.schedule(new BlinkLight(), START_DELAY, TimeUnit.SECONDS);
	}

	protected void initUI() {
		this.pauseResumeButton = addShootOFFButton(PAUSE, (event) -> {
			Button pauseResumeButton = (Button) event.getSource();
			if (PAUSE.equals(pauseResumeButton.getText())) {
				executorService.shutdownNow();
				pauseResumeButton.setText("Resume");
				repeatExercise = false;
				pauseShotDetection(true);
				blinkCount = 0;
				light.setFill(lightOffColor);
			} else {
				executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
						new NamedThreadFactory("CowboyFastDrawExercise"));
				pauseResumeButton.setText(PAUSE);
				repeatExercise = true;
				executorService.schedule(new BlinkLight(), RESUME_DELAY, TimeUnit.SECONDS);
			}
		});
	}

	public void addThreeBTarget() {
		final File cfdaThreeBFile = new File("@target/cfda_3b.target");

		Optional<Target> cdfaThreeBTarget = addTarget(cfdaThreeBFile, 10, 10);

		if (cdfaThreeBTarget.isPresent()) {
			threeB = cdfaThreeBTarget.get();
			
			// Center the target
			Dimension2D d = threeB.getDimension();
			final double x = (getArenaWidth() / 2) - d.getWidth();
			final double y = (getArenaHeight() / 2) - d.getHeight();
			threeB.setPosition(x, y);

			// Find the light
			for (TargetRegion r : threeB.getRegions()) {
				if (r.tagExists("subtarget") && "light".equals(r.getTag("subtarget"))) {
					light = (Shape) r;
					lightOffColor = (Color) light.getFill();
					break;
				}
			}
		}
	}

	private class BlinkLight implements Runnable {
		@Override
		public void run() {
			if (!repeatExercise) return;

			if (lightOffColor.equals(light.getFill())) {
				// Light on
				light.setFill(LIGHT_ON_COLOR);

				executorService.schedule(new BlinkLight(), BLINK_DELAY, TimeUnit.MILLISECONDS);
			} else {
				// Light off
				light.setFill(lightOffColor);

				blinkCount++;

				if (blinkCount == 3) {
					blinkCount = 0;
					int randomDelay = new Random().nextInt((MAX_DELAY - MIN_DELAY) + 1) + MIN_DELAY;
					executorService.schedule(() -> doRound(), randomDelay, TimeUnit.SECONDS);
				} else {
					executorService.schedule(new BlinkLight(), BLINK_DELAY, TimeUnit.MILLISECONDS);
				}
			}
		}
	}

	protected void doRound() {
		light.setFill(LIGHT_ON_COLOR);
		pauseShotDetection(false);
		roundStartTime = System.nanoTime();
	}

	@Override
	public void targetUpdate(Target target, TargetChange change) {
		if (repeatExercise && target.equals(threeB) && TargetChange.REMOVED.equals(change)) {
			addThreeBTarget();
		}
	}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Cowboy Fast Draw", "1.0", "phrack",
				"Simulates Cowboy Fast Draw #3B targets and timers in practice mode.");
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (hit.isPresent()) {
			long shotTime = System.nanoTime() - roundStartTime;
			String time = String.format("%d ms", shotTime / 1000000);

			showTextOnFeed(time, 25, 25, Color.BLACK, Color.RED, new Font(Font.getDefault().getFamily(), 40));

			if (coloredRows) {
				setShotTimerRowColor(Color.LIGHTGRAY);
			} else {
				setShotTimerRowColor(null);
			}

			coloredRows = !coloredRows;
			
			light.setFill(lightOffColor);
			
			pauseShotDetection(true);
			
			executorService.schedule(new BlinkLight(), RESUME_DELAY, TimeUnit.SECONDS);
		}
	}

	@Override
	public void reset(List<Target> targets) {
		repeatExercise = false;
		pauseShotDetection(true);
		blinkCount = 0;
		light.setFill(lightOffColor);
		executorService.shutdownNow();
		pauseResumeButton.setText(PAUSE);
		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
				new NamedThreadFactory("CowboyFastDrawExercise"));
		executorService.schedule(new BlinkLight(), START_DELAY, TimeUnit.SECONDS);
	}

	@Override
	public void destroy() {
		repeatExercise = false;
		executorService.shutdownNow();
		super.destroy();
	}
}
