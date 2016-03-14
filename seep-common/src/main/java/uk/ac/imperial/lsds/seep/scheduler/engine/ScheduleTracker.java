package uk.ac.imperial.lsds.seep.scheduler.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.scheduler.Stage;
import uk.ac.imperial.lsds.seep.scheduler.StageStatus;
import uk.ac.imperial.lsds.seep.scheduler.StageType;

public class ScheduleTracker {

	final private Logger LOG = LoggerFactory.getLogger(ScheduleTracker.class);
	
	private Set<Stage> stages;
	private ScheduleStatus status;
	private Stage sink;
	private Map<Stage, StageStatus> stageMonitor;
	private StageTracker currentStageTracker;
	
	public ScheduleTracker(Set<Stage> stages) {
		this.stages = stages;
		status = ScheduleStatus.NON_INITIALIZED;
		// Keep track of overall schedule
		stageMonitor = new HashMap<>();
		for(Stage stage : stages) {
			if(stage.getStageId() == 0) {
				// sanity check
				if(! stage.getStageType().equals(StageType.SINK_STAGE)){
					LOG.error("Stage 0 is not SINK_STAGE !!");
					// FIXME: throw a proper exception instead
					System.exit(0);
				}
				sink = stage;
			}
			stageMonitor.put(stage, StageStatus.WAITING);
		}
	}
	
	public void addNewStage(Stage stage){
		this.stageMonitor.put(stage, StageStatus.WAITING);
	}
	
	public boolean isScheduledFinished() {
		return this.status == ScheduleStatus.FINISHED;
	}
	
	public Stage getHead(){
		return sink;
	}
	
	public Map<Stage, StageStatus> stages() {
		return stageMonitor;
	}
	
	public boolean setReady(Stage stage) {
		this.stageMonitor.put(stage, StageStatus.READY);
		return true;
	}
	
	public Set<Stage> getReadySet() {
		Set<Stage> toReturn = new HashSet<>();
		for(Stage stage : stages) {
			if(this.isStageReady(stage)) {
				toReturn.add(stage);
			}
		}
		return toReturn;
	}
	
	public boolean setFinished(Stage stage, Map<Integer, Set<DataReference>> results) {
		LOG.info("[FINISH] SCHEDULING Stage {}", stage.getStageId());
		// Set finish
		this.stageMonitor.put(stage, StageStatus.FINISHED);
		if(stage.getStageType().equals(StageType.SINK_STAGE)) {
			// Finished schedule
			this.status = ScheduleStatus.FINISHED;
			LOG.info("[FINISHED-JOB]");
			// TODO: what to do with results in this case
			LOG.warn("TODO: what to do with results in this case");
		}
		// Check whether the new stage makes ready new stages, and propagate results
		if(results != null ){
			for(Stage downstream : stage.getDependants()) {
				Set<DataReference> resultsForThisStage = results.get(downstream.getStageId());
				downstream.addInputDataReference(stage.getStageId(), resultsForThisStage);
				if(isStageReadyToRun(downstream)) {
					this.stageMonitor.put(downstream, StageStatus.READY);
				}
			}
		}
		return true;
	}
	
	public boolean isStageReady(Stage stage) {
		return this.stageMonitor.get(stage).equals(StageStatus.READY);
	}
	
	public boolean isStageWaiting(Stage stage) {
		return this.stageMonitor.get(stage).equals(StageStatus.WAITING);
	}
	
	public boolean isStageFinished(Stage stage) {
		return this.stageMonitor.get(stage).equals(StageStatus.FINISHED);
	}
	
	public boolean resetAllStagesTo(StageStatus newStatus) {
		for(Stage st : this.stageMonitor.keySet()) {
			this.stageMonitor.put(st, newStatus);
		}
		this.status = ScheduleStatus.READY;
		return true;
	}
	
	private boolean isStageReadyToRun(Stage stage) {
		for(Stage st : stage.getDependencies()) {
			if( (stageMonitor.get(st) == null) || (!stageMonitor.get(st).equals(StageStatus.FINISHED))) {
				return false;
			}
		}
		return true;
	}
	
	public void trackWorkersAndBlock(Stage stage, Set<Integer> euInvolved) {
		currentStageTracker = new StageTracker(stage.getStageId(), euInvolved);
		//currentStageTracker.waitForStageToFinish();
	}
	
	public void waitForFinishedStageAndCompleteBookeeping(Stage stage) {
		currentStageTracker.waitForStageToFinish();
		// Check status of the stage
		if(currentStageTracker.finishedSuccessfully()) {
			Map<Integer, Set<DataReference>> results = currentStageTracker.getStageResults();
			setFinished(stage, results);
		}
		else{
			LOG.warn("Not successful stage... CHECK, non-FT fully-fledged implementation yet :|");
			System.exit(-1);
		}
	}

	public void finishStage(int euId, int stageId, Map<Integer, Set<DataReference>> results) {
		if(currentStageTracker == null)
			LOG.warn("STAGE TRACKER NULL => GS");
		else
			currentStageTracker.notifyOk(euId, stageId, results);
	}
	
}
