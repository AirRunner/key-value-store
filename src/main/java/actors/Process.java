package actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;

import actors.operation.Fail;
import actors.operation.Get;
import actors.operation.Operation;
import actors.operation.Put;
import actors.operation.msg.ReadRequest;
import actors.operation.msg.ReadResponse;
import actors.operation.msg.WriteRequest;
import actors.operation.msg.WriteResponse;
import actors.utils.Logger;
import actors.utils.RestTimer;

public class Process extends UntypedAbstractActor {
	private Logger log;								// Logger attached to actor
	private final int N;							// Number of processes
	private final int id;							// ID of current process
	private Members processes;						// Other processes' references
	private Queue<Operation> mailbox;				// Mailbox for pending message
	private HashMap<Integer, Integer> values;		// key-value map
	private HashMap<Integer, Integer> timestamps;	// key-timestamp map
	private ArrayList<UUID> currSeqNumbers;			// Sequence number of current message
	private State state;							// Current process' state
	private int ackNumber;							// Number of acknowledgements for current message
	private int proposal;							// Current PUT request proposal
	private Integer gotValue;						// Value got by GET request
	private Integer gotTimestamp;					// Timestamp got by GET request
	private Thread restTimer;						// Timer for process' rest time
	private long chrono;							// Timer for process' operations

	public Process(int ID, int nb, boolean logAll) {
		this.log = new Logger(logAll);
		this.N = nb;
		this.id = ID;
		this.mailbox = new LinkedList<>();
		this.values = new HashMap<>();
		this.timestamps = new HashMap<>();
		this.currSeqNumbers = new ArrayList<>();
		this.state = State.NONE;		
		this.ackNumber = 0;
		this.gotValue = null;
		this.gotTimestamp = null;
		this.restTimer = new Thread(new RestTimer(this));
	}

	/** Static actor creation **/
	public static Props createActor(int ID, int nb, boolean logAll) {
		return Props.create(Process.class, () -> {
			return new Process(ID, nb, logAll);
		});
	}

	public String toString() {
		return "Process {" + "id: " + this.id
			+ ", state: " + this.state
			+ ", values: " + this.values
			+ ", timestamps: " + this.timestamps + "}";
	}
	
	/** On message reception **/
	public void onReceive(Object message) throws Throwable {
		if (this.state != State.FAULTY) {
			/** Save the members of the system **/
			if (message instanceof Members && this.state == State.NONE) {
				Members msg = (Members) message;
				this.processes = msg;
				this.log.info("p" + self().path().name() + " received processes info");
			}
			/** Become faulty **/
			else if (message instanceof Fail) {
				this.state = State.FAULTY;
				this.log.info("p" + self().path().name() + " became faulty");
			}
			/** Add message to the mailbox **/
			else if (message instanceof Operation && this.state != State.NONE) {
				this.mailbox.add((Operation) message);
			}
			/** Process operations **/
			else if (message instanceof Get) {
				getReceived((Get) message);
			}
			else if (message instanceof Put) {
				putReceived((Put) message);
			}
			else if (message instanceof ReadRequest) {
				readReqReceived((ReadRequest) message);
			}
			else if (message instanceof ReadResponse && (this.state == State.GET || this.state == State.PUT)) {
				readRespReceived((ReadResponse) message);
			}
			else if (message instanceof WriteRequest) {
				writeReqReceived((WriteRequest) message);
			}
			else if (message instanceof WriteResponse && this.state == State.WAIT_WRITE) {
				writeRespReceived((WriteResponse) message);
			}
		}
		/** The process is faulty **/
		else {
			this.log.info("p" + self().path().name() + " received a message from p"+ sender().path().name() + " but is faulty");
		}
	}
	

	/** Enumerations **/
	public enum State {
		PUT, GET, WAIT_WRITE, FAULTY, NONE;
	}
	
	public enum Request {
		WRITE, READ;
	}


	/** Public methods **/
	public State getState() {
		return this.state;
	}

	public void terminate() {
		this.log.info("p" + self().path().name() + " is terminating...");
		context().system().stop(self());
	}


	/** Private methods **/
	// Process GET operation
	private void getReceived(Get msg) throws Throwable {
		this.state = State.GET;
		this.log.important("p" + self().path().name() + " is launching a get request with key " + msg.key + "...");
		this.chrono = System.nanoTime();
		sendRequests(Request.READ, msg.key);
		this.log.info("p" + self().path().name() + " launched a get request with key '" + msg.key);
	}

	// Process PUT operation
	private void putReceived(Put msg) throws Throwable {
		
		this.state = State.PUT;
		this.proposal = msg.proposal;
		
		this.log.important("p" + self().path().name() + " is launching a put request with key " + msg.key + " and proposal " + msg.proposal + "...");
		this.chrono = System.nanoTime();
		sendRequests(Request.READ, msg.key);
		this.log.info("p" + self().path().name() + " launched a put request with key " + msg.key + " and proposal " + msg.proposal);
	}

	// Process read request
	private void readReqReceived(ReadRequest msg) {
		ReadResponse rs;
		if (this.values.containsKey(msg.key)) {
			int localValue = this.values.get(msg.key);
			int localTimestamp = this.timestamps.get(msg.key);
			rs = new ReadResponse(msg.seqNumber, msg.key, localValue, localTimestamp);
		}
		else {
			rs = new ReadResponse(msg.seqNumber, msg.key, null, null);
		}
		sender().tell(rs, self());
		this.log.info("p" + self().path().name() + " responded to a read request from p" + sender().path().name());
	}

	// Process read response
	private void readRespReceived(ReadResponse msg) throws Throwable {
		if (this.currSeqNumbers.contains(msg.seqNumber)) {
			this.ackNumber++;
			if (msg.value != null && (this.gotValue == null ||
				msg.timestamp > this.gotTimestamp || (msg.timestamp == this.gotTimestamp && msg.value > this.gotValue))) {
				this.gotValue = msg.value;
				this.gotTimestamp = msg.timestamp;
			}
			// Majority
			if (this.ackNumber >= this.N/2) {
				this.ackNumber = 0;
				if (this.state == State.GET) {
					if (this.gotValue != null) {
						this.chrono = System.nanoTime() - this.chrono;
						this.log.important("p" + self().path().name() + " got the value [" + this.gotValue + "] with key [" +
							msg.key + "] and timestamp [" + this.gotTimestamp + "] in " + this.chrono / 1000 + "μs");
						this.gotValue = this.gotTimestamp = null;
					}
					else {
						this.log.important("p" + self().path().name() + " failed to get a value for key " + msg.key);
					}
					this.state = State.NONE;
					nextOperation();
				}
				else if (this.state == State.PUT) {
					Integer putTimestamp = this.gotTimestamp;
					if (putTimestamp == null) {
						putTimestamp = this.timestamps.containsKey(msg.key) ? this.timestamps.get(msg.key) : 0;
					}
					putProposal(msg.key, this.proposal, putTimestamp + 1);
					this.state = State.WAIT_WRITE;
					sendRequests(Request.WRITE, msg.key);
				}
			}
		}
	}

	// Process write request
	private void writeReqReceived(WriteRequest msg) {
		if (!this.values.containsKey(msg.key) || msg.timestamp > this.timestamps.get(msg.key) ||
			(msg.timestamp == this.timestamps.get(msg.key) && msg.proposal > this.values.get(msg.key))) {
			putProposal(msg.key, msg.proposal, msg.timestamp);
		}
		WriteResponse ws = new WriteResponse(msg.seqNumber, msg.key);
		sender().tell(ws, self());
		this.log.info("p" + self().path().name() + " responded to a write request from p" + sender().path().name());
	}

	// Process write response
	private void writeRespReceived(WriteResponse msg) throws Throwable {
		if (this.currSeqNumbers.contains(msg.seqNumber)) {
			this.ackNumber++;
			// Majority
			if (this.ackNumber >= this.N/2) {
				this.chrono = System.nanoTime() - this.chrono;
				this.ackNumber = 0;
				this.state = State.NONE;
				this.log.important("p" + self().path().name() + " put the value [" + this.proposal + "] with key [" + msg.key +
					"] and timestamp [" + this.timestamps.get(msg.key) + "] in " + this.chrono / 1000 + "μs");
				nextOperation();
			}
		}
	}

	// Launch next operation
	private void nextOperation() throws Throwable {
		if (!this.mailbox.isEmpty()) {
			this.onReceive(this.mailbox.remove());
		}
		else {
			this.log.info("p" + self().path().name() + " is waiting for a new job...");
			restTimer.interrupt();
			restTimer.start();
		}
	}

	// Send request to everyone
	private void sendRequests(Request type, int key) throws InterruptedException {
		Thread.sleep(1);
		Object request;
		UUID newSeqNumber;
		this.currSeqNumbers.clear();
		for (ActorRef actor : this.processes.references) {
			if (actor != self()) {
				newSeqNumber = UUID.randomUUID();
				this.currSeqNumbers.add(newSeqNumber);
				if (type == Request.WRITE) {
					request = (WriteRequest) new WriteRequest(newSeqNumber, key, this.values.get(key), this.timestamps.get(key));
				}
				else {
					request = (ReadRequest) new ReadRequest(newSeqNumber, key);
				}
				actor.tell(request, self());
			}
		}
	}

	// Ensure that both values and timestamp are put
	private void putProposal(int key, int proposal, int timestamp) {
		this.values.put(key, proposal);
		this.timestamps.put(key, timestamp);
	}
}