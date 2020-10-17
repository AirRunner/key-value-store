package actors.msg;

import java.util.UUID;

public class WriteResponse {
	public final UUID seqNumber;
	
	public WriteResponse(UUID seqN) {
		this.seqNumber = seqN;
	}
}
