package uk.ac.imperial.lsds.seep.scheduler;

public enum StageType {
	
	UNIQUE_STAGE,			// Both source and sink
	SOURCE_STAGE,			// First in a schedule
	INTERMEDIATE_STAGE,		// Non-first and non-last in a schedule
	SINK_STAGE,				// Last in a schedule
	CHOOSE_STAGE;			// Used for MDF
}
