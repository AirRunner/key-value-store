package actors.operation.msg;

import java.util.UUID;

public class ReadRequest {
	public final UUID seqNumber;
	public int key;
	
	public ReadRequest(UUID seqN, int k) {
		this.seqNumber = seqN;
		this.key = k;
	}
}
