package uk.ac.imperial.lsds.seepworker.comm;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.protocol.WorkerWorkerCommand;
import uk.ac.imperial.lsds.seep.comm.protocol.WorkerWorkerProtocolAPI;
import uk.ac.imperial.lsds.seep.comm.serialization.KryoFactory;
import uk.ac.imperial.lsds.seep.util.Utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;


public class WorkerWorkerCommManager {

	final private Logger LOG = LoggerFactory.getLogger(WorkerWorkerCommManager.class.getName());
	
	private ServerSocket serverSocket;
	private Kryo k;
	private Thread listener;
	private boolean working = false;
	private WorkerWorkerAPIImplementation api;
	
	public WorkerWorkerCommManager(InetAddress myIp, int port, WorkerWorkerAPIImplementation api){
		this.api = api;
		this.k = KryoFactory.buildKryoForWorkerWorkerProtocol();
		try {
			serverSocket = new ServerSocket(port, Utils.SERVER_SOCKET_BACKLOG, myIp);
			LOG.info(" Listening on {}:{}", myIp.toString(), port);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		this.listener = new Thread(new CommWorkerWorker());
		listener.setName(CommWorkerWorker.class.getName());
		// TODO: set uncaughtexceptionhandler
	}
	
	public void start(){
		working = true;
		this.listener.start();
	}
	
	public void stop(){
		// TODO: cleaning
		working = false;
	}
	
	class CommWorkerWorker implements Runnable{

		@Override
		public void run() {
			while(working){
				Input i = null;
				PrintWriter out = null;
				Socket incomingSocket = null;
				try{
					// Blocking call
					incomingSocket = serverSocket.accept();
					InputStream is = incomingSocket.getInputStream();
					out = new PrintWriter(incomingSocket.getOutputStream(), true);
					i = new Input(is);
					WorkerWorkerCommand command = k.readObject(i, WorkerWorkerCommand.class);
					short type = command.type();
					LOG.debug("RX WORKER-COMMAND of type: {}", type);
					
					if(type == WorkerWorkerProtocolAPI.ACK.type()){
						LOG.info("RX-> ACK command");
						
						out.println("ack");
					}
					else if(type == WorkerWorkerProtocolAPI.CRASH.type()){
						LOG.info("RX-> Crash command");
						
						out.println("ack");
					}
					else if(type == WorkerWorkerProtocolAPI.REQUEST_DATAREF.type()) {
						LOG.info("RX -> RequestDataReferenceCommand");
						out.println("ack");
						api.handleRequestDataReferenceCommand(command.getRequestDataReferenceCommand());
					}
					LOG.debug("Served WORKER-COMMAND of type: {}", type);
				}
				catch(IOException io){
					io.printStackTrace();
				}
				finally {
					if (incomingSocket != null){
						try {
							i.close();
							incomingSocket.close();
						}
						catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		
	}
	
}