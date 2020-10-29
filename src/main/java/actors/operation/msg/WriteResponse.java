package actors.operation.msg;

import java.util.UUID;

public class WriteResponse {
	public final UUID seqNumber;
	public final int key;
	
	public WriteResponse(UUID seqN, int k) {
		this.seqNumber = seqN;
		this.key = k;
	}
}
