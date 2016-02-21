package uk.ac.imperial.lsds.seep.api.operator;

import java.util.ArrayList;
import java.util.List;

import uk.ac.imperial.lsds.seep.api.ConnectionType;
import uk.ac.imperial.lsds.seep.api.DataStore;
import uk.ac.imperial.lsds.seep.api.SeepTask;
import uk.ac.imperial.lsds.seep.api.operator.sinks.TaggingSink;
import uk.ac.imperial.lsds.seep.api.operator.sources.TaggingSource;
import uk.ac.imperial.lsds.seep.api.state.SeepState;
import uk.ac.imperial.lsds.seep.util.Utils;

public class SeepLogicalOperator implements LogicalOperator {

	private int opId;
	private String name;
	private boolean stateful;
	private SeepTask task;
	private SeepState state;
	/**
	 * Optional Operator priority currently used for Two-level Scheduling
	 */
	private int priority = -1;
	

	private List<DownstreamConnection> downstream = new ArrayList<DownstreamConnection>();
	private List<UpstreamConnection> upstream = new ArrayList<UpstreamConnection>();
	
	private SeepLogicalOperator(int opId, SeepTask task, String name){
		this.opId = opId;
		this.task = task;
		this.name = name;
		this.stateful = false;
	}
	
	private SeepLogicalOperator(int opId, SeepTask task, SeepState state, String name){
		this.opId = opId;
		this.task = task;
		this.name = name;
		this.state = state;
		this.stateful = true;
	}
	
	// Empty constructor for serialization
	public SeepLogicalOperator() { }
	
	public static LogicalOperator newStatelessOperator(int opId, SeepTask task){
		String name = new Integer(opId).toString();
		return SeepLogicalOperator.newStatelessOperator(opId, task, name);
	}
	
	public static LogicalOperator newStatelessOperator(int opId, SeepTask task, String name){
		return new SeepLogicalOperator(opId, task, name);
	}
	
	public static LogicalOperator newStatefulOperator(int opId, SeepTask task, SeepState state){
		String name = new Integer(opId).toString();
		return SeepLogicalOperator.newStatefulOperator(opId, task, state, name);
	}
	
	public static LogicalOperator newStatefulOperator(int opId, SeepTask task, SeepState state, String name){
		return new SeepLogicalOperator(opId, task, state, name);
	}
	
	@Override
	public int getOperatorId() {
		return opId;
	}

	@Override
	public String getOperatorName() {
		return name;
	}

	@Override
	public boolean isStateful() {
		return stateful;
	}
	
	@Override
	public SeepState getState() {
		return state;
	}

	@Override
	public SeepTask getSeepTask() {
		return task;
	}

	@Override
	public List<DownstreamConnection> downstreamConnections() {
		return downstream;
	}

	@Override
	public List<UpstreamConnection> upstreamConnections() {
		return upstream;
	}

	public void connectTo(Operator downstreamOperator, int streamId, DataStore dataStore) {
		this.connectTo(downstreamOperator, streamId, dataStore, ConnectionType.ONE_AT_A_TIME);
	}
	
	public void connectTo(Operator downstreamOperator, int streamId, DataStore dataStore, ConnectionType connectionType){
		// Add downstream to this operator
		this.addDownstream(downstreamOperator, streamId, dataStore, connectionType);
		// Add this as a new upstream to the downstream operator. With the exception of downstream being a 
		// TaggingSink in which case its existence is transient and its purpose of limited scope,
		// like that of all of us. At least until someone figures out how to cure aging, and people start
		// reading more philosophy and less shit, and working hard to figure out why they want to be alive, 
		// instead of simply following their animal instincts of survival. Those lazy bastards.
		if(! (downstreamOperator instanceof TaggingSink)) {
			((SeepLogicalOperator)downstreamOperator).addUpstream(this, streamId, dataStore, connectionType);
		}
	}
	
	public void reverseConnection(TaggingSource ss, int streamId, DataStore dataStore, ConnectionType connectionType) {
		this.addUpstream(null, streamId, dataStore, connectionType);
	}
	
	/* Methods to manage logicalOperator connections */
	
	private void addDownstream(Operator lo, int streamId, DataStore dataStore, ConnectionType connectionType) {
		DownstreamConnection dc = new DownstreamConnection(lo, streamId, dataStore, connectionType);
		this.downstream.add(dc);
	}
	
	private void addUpstream(Operator lo, int streamId, DataStore dataStore, ConnectionType connectionType) {
		UpstreamConnection uc = new UpstreamConnection(lo, streamId, dataStore, connectionType);
		this.upstream.add(uc);
	}
	
	public boolean hasPriority(){
		return this.priority != -1;
	}
	
	/**
	 * @return the priority
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * @param priority the priority to set
	 */
	public void setPriority(int priority) {
		this.priority = priority;
	}
	
	/* Methods to print info about the operator */
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("LogicalOperator");
		sb.append(Utils.NL);
		sb.append("###############");
		sb.append(Utils.NL);
		sb.append("Name: "+this.name);
		sb.append(Utils.NL);
		sb.append("OpId: "+this.opId);
		sb.append(Utils.NL);
		sb.append("Stateful?: "+this.stateful);
		sb.append(Utils.NL);
		sb.append("#Downstream: "+this.downstream.size());
		sb.append(Utils.NL);
		for(int i = 0; i<this.downstream.size(); i++){
			DownstreamConnection down = downstream.get(i);
			sb.append("  Down-conn-"+i+"-> StreamId: "+down.getStreamId()+" to opId: "
					+ ""+down.getDownstreamOperator().getOperatorId());
			sb.append(Utils.NL);
		}
		sb.append("#Upstream: "+this.upstream.size());
		sb.append(Utils.NL);
		for(int i = 0; i<this.upstream.size(); i++){
			UpstreamConnection up = upstream.get(i);
			if(up.getUpstreamOperator() != null) {
				sb.append("  Up-conn-"+i+"-> StreamId: "+up.getStreamId()+" to opId: "
					+ ""+up.getUpstreamOperator().getOperatorId()+""
							+ " with connType: "+up.getConnectionType()+" and dataOrigin: "+up.getDataStoreType());
			}
			else {
				sb.append("  Up-conn-"+i+"-> StreamId: "+up.getStreamId()+" to opId: "
						+ ""+up.getDataStoreType().toString()+""
								+ " with connType: "+up.getConnectionType()+" and dataOrigin: "+up.getDataStoreType());
			}
			sb.append(Utils.NL);
		}
		return sb.toString();
	}

}
