package actors.operation;

public class Put extends Operation {
	public final int key;
	public final int proposal;
	
	public Put(int k, int p) {
		this.key = k;
		this.proposal = p;
	}
}
