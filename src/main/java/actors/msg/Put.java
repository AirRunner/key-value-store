package actors.msg;

public class Put extends Operation {
	public final int proposal;
	
	public Put(int p) {
		this.proposal = p;
	}
}
