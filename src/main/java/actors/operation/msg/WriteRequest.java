package actors.operation.msg;

import java.util.UUID;

public class WriteRequest {
	public final UUID seqNumber;
	public final int key;
	public final int proposal;
	public final int timestamp;
	
	public WriteRequest(UUID seqN, int k, int p, int t) {
		this.seqNumber = seqN;
		this.key = k;
		this.proposal = p;
		this.timestamp = t;
	}
}
