import java.util.List;

import uk.ac.imperial.lsds.seep.api.API;
import uk.ac.imperial.lsds.seep.api.SeepTask;
import uk.ac.imperial.lsds.seep.api.data.ITuple;
import uk.ac.imperial.lsds.seep.api.data.OTuple;
import uk.ac.imperial.lsds.seep.api.data.Schema;
import uk.ac.imperial.lsds.seep.api.data.Type;
import uk.ac.imperial.lsds.seep.api.data.Schema.SchemaBuilder;


public class Processor implements SeepTask {

	//private Schema schema = SchemaBuilder.getInstance().newField(Type.INT, "param1").newField(Type.INT, "param2").build();
	private Schema schema = SchemaBuilder.getInstance().newField(Type.STRING, "record").build();

	@Override
	public void processData(ITuple data, API api) {
		//int param1 = data.getInt("param1");
		//int param2 = data.getInt("param2");
		String record = data.getString("record");
		
		//param1 = param1 * 3;
		//param2 = param2 * 3;
		record = record.toUpperCase();
		//byte[] d = OTuple.create(schema, new String[]{"param1", "param2"}, new Object[]{param1, param2});
		byte[] d = OTuple.create(schema, new String[]{"record"}, new Object[]{record});
		System.out.println("[Processor] data send ("+d.length+"): " + record);
		api.send(d);
		
//		waitHere(10);
	}
	
	private void waitHere(int time){
		try {
			Thread.sleep(time);
		} 
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void setUp() {
		System.out.println("I am a Processor!!");
	}

	@Override
	public void processDataGroup(List<ITuple> arg0, API arg1) {
		// TODO Auto-generated method stub
		
	}
}