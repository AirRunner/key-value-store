package actors.msg;

import java.util.UUID;

public class ReadRequest {
	public final UUID seqNumber;
	
	public ReadRequest(UUID seqN) {
		this.seqNumber = seqN;
	}
}
