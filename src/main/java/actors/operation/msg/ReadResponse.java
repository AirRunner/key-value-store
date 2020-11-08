package actors.operation.msg;

import java.util.UUID;

public class ReadResponse {
	public final UUID seqNumber;
	public final int key;
	public final Integer value;
	public final Integer timestamp;
	
	public ReadResponse(UUID seqN, int k, Integer v, Integer t) {
		this.seqNumber = seqN;
		this.key = k;
		this.value = v;
		this.timestamp = t;
	}
}
