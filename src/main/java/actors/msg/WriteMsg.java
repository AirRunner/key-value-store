package actors.msg;

public class WriteMsg {
	public final int value;
	public final int timestamp;
	
	public WriteMsg(int v, int t) {
		this.value = v;
		this.timestamp = t;
	}
}
