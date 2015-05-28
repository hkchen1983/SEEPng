package uk.ac.imperial.lsds.seep.api.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.api.ConnectionType;
import uk.ac.imperial.lsds.seep.api.DataStore;
import uk.ac.imperial.lsds.seep.api.DataStoreType;
import uk.ac.imperial.lsds.seep.api.data.Schema;

public class DownstreamConnection {

	final private Logger LOG = LoggerFactory.getLogger(DownstreamConnection.class);
	
	private Operator downstreamOperator;
	private int streamId;
	private ConnectionType connectionType;
	private DataStore dataStore;

	public DownstreamConnection(Operator lo, int streamId, DataStore dataStore, ConnectionType connectionType) {
		this.downstreamOperator = lo;
		this.streamId = streamId;
		this.dataStore = dataStore;
		this.connectionType = connectionType;
	}

	public Operator getDownstreamOperator() {
		return downstreamOperator;
	}

	public int getStreamId() {
		return streamId;
	}

	public Schema getSchema(){
		return dataStore.getSchema();
	}

	public ConnectionType getConnectionType(){
		return connectionType;
	}

	public DataStoreType getExpectedDataStoreTypeOfDownstream(){
		return dataStore.type();
	}
	
	public DataStore getExpectedDataStoreOfDownstream(){
		return dataStore;
	}
}
