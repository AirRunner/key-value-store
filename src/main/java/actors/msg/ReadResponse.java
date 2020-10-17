package actors.msg;

import java.util.UUID;

public class ReadResponse {
	public final UUID seqNumber;
	public final int value;
	public final int timestamp;
	
	public ReadResponse(UUID seqN, int v, int t) {
		this.seqNumber = seqN;
		this.value = v;
		this.timestamp = t;
	}
}
