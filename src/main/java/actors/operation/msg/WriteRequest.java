package actors.operation.msg;

import java.util.UUID;

public class WriteRequest {
	public final UUID seqNumber;
	public final int proposal;
	public final int timestamp;
	
	public WriteRequest(UUID seqN, int p, int t) {
		this.seqNumber = seqN;
		this.proposal = p;
		this.timestamp = t;
	}
}
