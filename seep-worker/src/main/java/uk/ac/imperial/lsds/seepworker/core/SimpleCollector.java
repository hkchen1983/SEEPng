package uk.ac.imperial.lsds.seepworker.core;

import java.util.ArrayList;
import java.util.List;

import uk.ac.imperial.lsds.seep.api.API;
import uk.ac.imperial.lsds.seep.api.RuntimeEvent;
import uk.ac.imperial.lsds.seep.api.RuntimeEventFactory;

public class SimpleCollector implements API {

	private byte[] mem;
	
	// Attributes for RuntimeEvent
	private List<RuntimeEvent> rEvents;
	
	public SimpleCollector() { 
		this.rEvents = new ArrayList<>();
	}
	
	@Override
	public int id() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void send(byte[] o) {
		this.mem = o;
	}

	@Override
	public void sendAll(byte[] o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendKey(byte[] o, int key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendKey(byte[] o, String key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendToStreamId(int streamId, byte[] o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendToAllInStreamId(int streamId, byte[] o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendStreamidKey(int streamId, byte[] o, int key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendStreamidKey(int streamId, byte[] o, String key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send_index(int index, byte[] o) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send_opid(int opId, byte[] o) {
		// TODO Auto-generated method stub
		
	}
	
	public byte[] collect() {
		return mem;
	}

	@Override
	public void exception(String message) {
		try {
			throw new Exception();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void datasetSpilledToDisk(int datasetId) {
		RuntimeEvent re = RuntimeEventFactory.makeSpillToDiskRuntimeEvent(datasetId);
		this.rEvents.add(re);
	}

	@Override
	public void failure() {
		try {
			throw new Exception();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public List<RuntimeEvent> getRuntimeEvents() {
		return rEvents;
	}

	@Override
	public void notifyEndOfLoop() {
		RuntimeEvent re = RuntimeEventFactory.makeNotifyEndOfLoop();
		this.rEvents.add(re);
	}
	
	@Override
	public void storeEvaluateResults(Object obj) {
		RuntimeEvent re = RuntimeEventFactory.makeEvaluateResults(obj);
		this.rEvents.add(re);
		
	}

}
