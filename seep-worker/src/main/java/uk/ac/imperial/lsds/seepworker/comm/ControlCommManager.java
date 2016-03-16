package uk.ac.imperial.lsds.seepworker.comm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import uk.ac.imperial.lsds.seep.api.DataReference;
import uk.ac.imperial.lsds.seep.api.operator.SeepLogicalQuery;
import uk.ac.imperial.lsds.seep.comm.protocol.CodeCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.Command;
import uk.ac.imperial.lsds.seep.comm.protocol.CommandFamilyType;
import uk.ac.imperial.lsds.seep.comm.protocol.MasterWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.MasterWorkerProtocolAPI;
import uk.ac.imperial.lsds.seep.comm.protocol.MaterializeTaskCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.RequestDataReferenceCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.ScheduleDeployCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.ScheduleStageCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.StartQueryCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.StopQueryCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.WorkerWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.WorkerWorkerProtocolAPI;
import uk.ac.imperial.lsds.seep.comm.serialization.KryoFactory;
import uk.ac.imperial.lsds.seep.infrastructure.ControlEndPoint;
import uk.ac.imperial.lsds.seep.infrastructure.DataEndPoint;
import uk.ac.imperial.lsds.seep.scheduler.ScheduleDescription;
import uk.ac.imperial.lsds.seep.util.RuntimeClassLoader;
import uk.ac.imperial.lsds.seep.util.Utils;
import uk.ac.imperial.lsds.seepworker.WorkerConfig;
import uk.ac.imperial.lsds.seepworker.core.Conductor;

public class ControlCommManager {

	final private Logger LOG = LoggerFactory.getLogger(ControlCommManager.class.getName());
	
	private ServerSocket serverSocket;
	private Kryo k;
	
	private Thread listener;
	private boolean working = false;
	private RuntimeClassLoader rcl;
	private Conductor c;
	
	private InetAddress myIp;
	private int myPort;
	
	// Query specific parameters. FIXME: refactor somewhere
	private String pathToQueryJar;
	private String definitionClass;
	private String[] queryArgs;
	private String methodName;
	private short queryType;
	
	public ControlCommManager(InetAddress myIp, int port, WorkerConfig wc, RuntimeClassLoader rcl, Conductor c) {
		this.c = c;
		this.myIp = myIp;
		this.myPort = wc.getInt(WorkerConfig.CONTROL_PORT);
		this.rcl = rcl;
		this.k = KryoFactory.buildKryoForMasterWorkerProtocol(rcl);
		try {
			serverSocket = new ServerSocket(port, Utils.SERVER_SOCKET_BACKLOG, myIp);
			LOG.info(" Listening on {}:{}", myIp, port);
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		listener = new Thread(new CommandReceiver());
//		dispatcher = new Thread(new CommandDispatcher());
		listener.setName(CommandReceiver.class.getSimpleName());
//		dispatcher.setName(CommandDispatcher.class.getSimpleName());
//		this.signal = new WaitNotify();
//		this.commandQueue = new ArrayBlockingQueue<>(10);
	}
	
	public void start() {
		this.working = true;
		this.listener.start();
//		this.dispatcher.start();
	}
	
	public void stop() {
		//TODO: do some other cleaning work here
		this.working = false;
	}
	
	class Task implements Runnable {
		
		private Socket incomingSocket;
		
		public Task(Socket incomingSocket) {
			this.incomingSocket = incomingSocket;
		}
		
		public void run() {
			try {
				InputStream is = incomingSocket.getInputStream();
				PrintWriter out = new PrintWriter(incomingSocket.getOutputStream(), true);
				Input i = new Input(is, 1000000);
				Command sc = k.readObject(i, Command.class);
				short familyType = sc.familyType();
				if(familyType == CommandFamilyType.MASTERCOMMAND.ofType()) {
					handleMasterCommand(((MasterWorkerCommand)sc.getCommand()), out);
				}
				else if(familyType == CommandFamilyType.WORKERCOMMAND.ofType()) {
					handleWorkerCommand(((WorkerWorkerCommand)sc.getCommand()), out);
				}
			}
			catch(IOException io) {
				io.printStackTrace();
			}
			finally {
				if (incomingSocket != null) {
					try {
						incomingSocket.close();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			}	
		}
	}
	
	class CommandReceiver implements Runnable {
		@Override
		public void run() {
			while(working) {
				Socket incomingSocket = null;
				// Blocking call
				try {
					incomingSocket = serverSocket.accept();
				} 
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				new Thread(new Task(incomingSocket)).start();
			}		
		}	
	}	
	
	private void handleMasterCommand(MasterWorkerCommand c, PrintWriter out) {
		short cType = c.type();
		LOG.debug("RX command with type: {}", cType);
		// CODE command
		if(cType == MasterWorkerProtocolAPI.CODE.type()) {
			LOG.info("RX Code command");
			CodeCommand cc = c.getCodeCommand();
			short queryType = cc.getQueryType();
			byte[] file = cc.getData();
			LOG.info("Received query file with size: {}", file.length);
			if(cc.getDataSize() != file.length){
				// sanity check
				// TODO: throw error
			}
			// TODO: get filename from properties file
			String pathToQueryJar = "query.jar";
			File f = Utils.writeDataToFile(file, pathToQueryJar);
			out.println("ack");
//			signal.n();
			loadCodeToRuntime(f);
			// Instantiate Seep Logical Query
			handleQueryInstantiation(queryType, pathToQueryJar, cc.getBaseClassName(), cc.getQueryConfig(), cc.getMethodName());
		}
		// MATERIALIZED_TASK command
		else if(cType == MasterWorkerProtocolAPI.MATERIALIZE_TASK.type()) {
			LOG.info("RX MATERIALIZED_TASK command");
			MaterializeTaskCommand mtc = c.getMaterializeTaskCommand();
			out.println("ack");
//			signal.n();
			handleMaterializeTask(mtc);
		}
		// SCHEDULE_TASKS command
		else if(cType == MasterWorkerProtocolAPI.SCHEDULE_TASKS.type()) {
			LOG.info("RX SCHEDULE_TASKS command");
			ScheduleDeployCommand sdc = c.getScheduleDeployCommand();
			out.println("ack");
//			signal.n();
			handleScheduleDeploy(sdc);
		}
		// SCHEDULE_STAGE command
		else if(cType == MasterWorkerProtocolAPI.SCHEDULE_STAGE.type()) {
			LOG.info("RX SCHEDULE_STAGE command");
			ScheduleStageCommand esc = c.getScheduleStageCommand();
			out.println("ack");
//			signal.n();
			handleScheduleStage(esc);
		}
		// STARTQUERY command
		else if(cType == MasterWorkerProtocolAPI.STARTQUERY.type()) {
			LOG.info("RX STARTQUERY command");
			StartQueryCommand sqc = c.getStartQueryCommand();
			out.println("ack");
//			signal.n();
			handleStartQuery(sqc);
		}
		// STOPQUERY command
		else if(cType == MasterWorkerProtocolAPI.STOPQUERY.type()) {
			LOG.info("RX STOPQUERY command");
			StopQueryCommand sqc = c.getStopQueryCommand();
			out.println("ack");
//			signal.n();
			handleStopQuery(sqc);
		}
		LOG.debug("Served command of type: {}", cType);
	}
	
	private void handleWorkerCommand(WorkerWorkerCommand c, PrintWriter out) {
		short type = c.type();
		LOG.debug("RX WORKER-COMMAND of type: {}", type);
		
		if(type == WorkerWorkerProtocolAPI.ACK.type()){
			LOG.info("RX-> ACK command");
			
			out.println("ack");
//			signal.n();
		}
		else if(type == WorkerWorkerProtocolAPI.CRASH.type()){
			LOG.info("RX-> Crash command");
			
			out.println("ack");
//			signal.n();
		}
		else if(type == WorkerWorkerProtocolAPI.REQUEST_DATAREF.type()) {
			LOG.info("RX -> RequestDataReferenceCommand");
			out.println("ack");
//			signal.n();
			handleRequestDataReferenceCommand(c.getRequestDataReferenceCommand());
		}
		LOG.debug("Served WORKER-COMMAND of type: {}", type);
	}
	
	public void handleRequestDataReferenceCommand(RequestDataReferenceCommand requestDataReferenceCommand) {
		int dataRefId = requestDataReferenceCommand.getDataReferenceId();
		String ip = requestDataReferenceCommand.getIp();
		int rxPort = requestDataReferenceCommand.getReceivingDataPort();
		
		// Create target endPoint
		int id = Utils.computeIdFromIpAndPort(ip, rxPort);
		
		DataEndPoint dep = new DataEndPoint(id, ip, rxPort);
		LOG.info("Request to serve data to: {}", dep.toString());
		c.serveData(dataRefId, dep);
	}

	public void handleQueryInstantiation(short queryType, String pathToQueryJar, String definitionClass, String[] queryArgs, String methodName) {
		this.queryType = queryType;
		this.pathToQueryJar = pathToQueryJar;
		this.definitionClass = definitionClass;
		this.queryArgs = queryArgs;
		this.methodName = methodName;
	}
	
	public void handleMaterializeTask(MaterializeTaskCommand mtc) {
		// Instantiate logical query
		LOG.info("Composing query and loading to class loader...");
		SeepLogicalQuery slq = Utils.executeComposeFromQuery(pathToQueryJar, definitionClass, queryArgs, methodName);
		LOG.info("Composing query and loading to class loader...OK");
		// Get physical info from command
		Map<Integer, ControlEndPoint> mapping = mtc.getMapping();
		Map<Integer, Map<Integer, Set<DataReference>>> inputs = mtc.getInputs();
		Map<Integer, Map<Integer, Set<DataReference>>> outputs = mtc.getOutputs();
 		int myOwnId = Utils.computeIdFromIpAndPort(myIp, myPort);
 		LOG.info("Computed ID: {}", myOwnId);
//		c.setQuery(myOwnId, slq, mapping, inputs, outputs);
		c.materializeAndConfigureTask(myOwnId, slq, mapping, inputs, outputs);
	}
	
	public void handleScheduleDeploy(ScheduleDeployCommand sdc) {
		// TODO: this requires further testing. Try both queryTypes and see what happens
		
		// Get schedule description
		ScheduleDescription sd = sdc.getSchedule();
		
		// Get schedule description by loading to runtime
//		ScheduleDescription sd = Utils.executeComposeFromQuery(pathToQueryJar, definitionClass, queryArgs, methodName);
		int myOwnId = Utils.computeIdFromIpAndPort(myIp, myPort);
		c.configureScheduleTasks(myOwnId, sd);
		LOG.info("Scheduled deploy is done. Waiting for master commands...");
	}

	public void handleStartQuery(StartQueryCommand sqc) {
		c.startProcessing();
	}
	
	public void handleStopQuery(StopQueryCommand sqc) {
		c.stopProcessing();
	}

	public void handleScheduleStage(ScheduleStageCommand esc) {
		int stageId = esc.getStageId();
		Map<Integer, Set<DataReference>> input = esc.getInputDataReferences();
		Map<Integer, Set<DataReference>> output = esc.getOutputDataReference();
		List<Integer> rankedDatasets = esc.getRankedDatasets();
		c.scheduleTask(stageId, input, output, rankedDatasets);
	}
	
	private void loadCodeToRuntime(File pathToCode){
		URL urlToCode = null;
		try {
			urlToCode = pathToCode.toURI().toURL();
			System.out.println("Loading into class loader: "+urlToCode.toString());
			URL[] urls = new URL[1];
			urls[0] = urlToCode;
			rcl.addURL(urlToCode);
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
