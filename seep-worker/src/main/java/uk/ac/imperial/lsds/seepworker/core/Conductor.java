package uk.ac.imperial.lsds.seepworker.core;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.ConnectionType;
import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.SeepTask;
import uk.ac.imperial.lsds.seep.api.StatefulSeepTask;
import uk.ac.imperial.lsds.seep.api.operator.LogicalOperator;
import uk.ac.imperial.lsds.seep.api.operator.SeepLogicalQuery;
import uk.ac.imperial.lsds.seep.api.operator.UpstreamConnection;
import uk.ac.imperial.lsds.seep.api.state.SeepState;
import uk.ac.imperial.lsds.seep.comm.Connection;
import uk.ac.imperial.lsds.seep.core.DataStoreSelector;
import uk.ac.imperial.lsds.seep.core.EventAPI;
import uk.ac.imperial.lsds.seep.infrastructure.EndPoint;
import uk.ac.imperial.lsds.seep.scheduler.ScheduleDescription;
import uk.ac.imperial.lsds.seep.scheduler.Stage;
import uk.ac.imperial.lsds.seepworker.WorkerConfig;
import uk.ac.imperial.lsds.seepworker.comm.WorkerMasterAPIImplementation;
import uk.ac.imperial.lsds.seepworker.core.input.CoreInput;
import uk.ac.imperial.lsds.seepworker.core.input.CoreInputFactory;
import uk.ac.imperial.lsds.seepworker.core.output.CoreOutput;
import uk.ac.imperial.lsds.seepworker.core.output.CoreOutputFactory;

public class Conductor {

	final private Logger LOG = LoggerFactory.getLogger(Conductor.class.getName());
	
	private WorkerConfig wc;
	private InetAddress myIp;
	private WorkerMasterAPIImplementation masterApi;
	private Connection masterConn;
	private int id;
	private SeepLogicalQuery query;
	private Map<Integer, EndPoint> mapping;
	// TODO: these two are only specific to materialise tasks
	private Map<Integer, Map<Integer, Set<DataReference>>> inputs;
	private Map<Integer, Map<Integer, Set<DataReference>>> outputs;
	
	private List<DataStoreSelector> dataStoreSelectors;
	
	private CoreInput coreInput;
	private CoreOutput coreOutput;
	private ProcessingEngine engine;
	private DataReferenceManager drm;
	
	// Keep stage - scheduleTask
	private Map<Stage, ScheduleTask> scheduleTasks;
	private ScheduleDescription sd;
	
	public Conductor(InetAddress myIp, WorkerMasterAPIImplementation masterApi, DataReferenceManager drm, Connection masterConn, WorkerConfig wc){
		this.myIp = myIp;
		this.masterApi = masterApi;
		this.masterConn = masterConn;
		this.wc = wc;
		this.scheduleTasks = new HashMap<>();
		this.drm = drm;
	}
	
	public void setQuery(int id, SeepLogicalQuery query, Map<Integer, EndPoint> mapping, Map<Integer, Map<Integer, Set<DataReference>>> inputs, Map<Integer, Map<Integer, Set<DataReference>>> outputs) {
		this.id = id;
		this.query = query;
		this.mapping = mapping;
		this.inputs = inputs;
		this.outputs = outputs;
	}
	
	public void materializeAndConfigureTask() {
		int opId = getOpIdLivingInThisEU(id);
		LogicalOperator o = query.getOperatorWithId(opId);
		LOG.info("Found LogicalOperator: {} mapped to this executionUnit: {} stateful: {}", o.getOperatorName(), 
				id, o.isStateful());
		
		SeepTask task = o.getSeepTask();
		LOG.info("Configuring local task: {}", task.toString());
		// set up state if any
		SeepState state = o.getState();
		if(o.isStateful()){
			LOG.info("Configuring state of local task: {}", state.toString());
			((StatefulSeepTask)task).setState(state);
		}
		// This creates one inputAdapter per upstream stream Id
		Map<Integer, Set<DataReference>> input = inputs.get(o.getOperatorId());
		Map<Integer, Set<DataReference>> output = outputs.get(o.getOperatorId());
		Map<Integer, ConnectionType> connTypeInformation = getInputConnectionType(o);
		coreInput = CoreInputFactory.buildCoreInputFor(wc, input, connTypeInformation);
		// This creates one outputAdapter per downstream stream Id
		coreOutput = CoreOutputFactory.buildCoreOutputForOperator(wc, o, mapping);
		coreOutput = CoreOutputFactory.buildCoreOutputFor(wc, drm, output);
		
		dataStoreSelectors = DataStoreSelectorFactory.buildDataStoreSelector(coreInput, 
				coreOutput, wc, o, myIp, wc.getInt(WorkerConfig.DATA_PORT));

		// FIXME: this is ugly, why ns is special?
		for(DataStoreSelector dss : dataStoreSelectors) {
			if(dss instanceof EventAPI) coreOutput.setEventAPI((EventAPI)dss);
		}

		int id = o.getOperatorId();
		
		engine = ProcessingEngineFactory.buildSingleTaskProcessingEngine(wc, id, task, state, coreInput, coreOutput, new ConductorCallback(true));
		
		// Initialize system
		LOG.info("Setting up task...");
		task.setUp(); // setup method of task
		LOG.info("Setting up task...OK");
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.initSelector();
		}
	}

	public void configureScheduleTasks(int id, ScheduleDescription sd, SeepLogicalQuery slq) {
		this.id = id;
		this.query = slq;
		this.sd = sd;
		// Create ScheduleTask for every stage
		for(Stage s : sd.getStages()) {
			ScheduleTask st = ScheduleTask.buildTaskFor(id, s, slq);
			scheduleTasks.put(s, st);
		}
		
//		dataStoreSelectors = DataStoreSelectorFactory.buildDataStoreSelector(coreInput, 
//		coreOutput, wc, o, myIp, wc.getInt(WorkerConfig.DATA_PORT));
	}
	
	public void scheduleTask(int stageId, Map<Integer, Set<DataReference>> input, Map<Integer, Set<DataReference>> output) {
		Stage s = sd.getStageWithId(stageId);
		ScheduleTask task = this.scheduleTasks.get(s);
		
		coreInput = CoreInputFactory.buildCoreInputForStage(wc, input);
		coreOutput = CoreOutputFactory.buildCoreOutputForStage(wc, output);

		SeepState state = null;
		
		ProcessingEngine engine = ProcessingEngineFactory.buildComposedTaskProcessingEngine(wc, s.getStageId(), task, state, coreInput, coreOutput, new ConductorCallback(false));
		engine.start();
	}
	
	public void startProcessing(){
		LOG.info("Starting processing engine...");
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.startSelector();
		}
		engine.start();
	}
	
	public void stopProcessing(){
		LOG.info("Stopping processing engine...");
		engine.stop();
		for(DataStoreSelector dss : dataStoreSelectors) {
			dss.stopSelector();
		}
		LOG.info("Stopping processing engine...OK");
	}
	
	private Map<Integer, ConnectionType> getInputConnectionType(LogicalOperator o) {
		Map<Integer, ConnectionType> ct = new HashMap<>();
		for(UpstreamConnection uc : o.upstreamConnections()) {
			ct.put(uc.getStreamId(), uc.getConnectionType());
		}
		return ct;
	}

	private int getOpIdLivingInThisEU(int id) {
		for(Entry<Integer, EndPoint> entry : mapping.entrySet()) {
			if(entry.getValue().getId() == id) return entry.getKey();
		}
		return -1;
	}
	
	class ConductorCallback {

		private boolean continuousTask;
		
		public ConductorCallback(boolean continuousTask) {
			this.continuousTask = continuousTask;
		}
		
		public boolean isContinuousTask() {
			return continuousTask;
		}

		public void notifyOk() {
//			masterApi.scheduleTaskStatus(masterConn, stageId, euId, StageStatusCommand.Status.OK, producedOutput);
			
		}
		
	}
		
}
