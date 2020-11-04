package actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
// import akka.event.Logging;
// import akka.event.LoggingAdapter;

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
	// private final LoggingAdapter log;	// Logger attached to actor
	private Logger log;
	private final int N;					// Number of processes
	private final int id;					// ID of current process
	private Members processes;				// other processes' references
	private Queue<Operation> mailbox;
	private HashMap<Integer, Integer> values;
	private HashMap<Integer, Integer> timestamps;
	private State state;
	private Thread restTimer;
	private long chrono;
	private int proposal;
	private ArrayList<UUID> currSeqNumbers;
	private int ackNumber;

	public Process(int ID, int nb) {
		// this.log = Logging.getLogger(context().system(), this);
		this.log = new Logger();
		this.N = nb;
		this.id = ID;
		this.mailbox = new LinkedList<>();
		this.values = new HashMap<>();
		this.timestamps = new HashMap<>();
		this.state = State.NONE;
		this.restTimer = new Thread(new RestTimer(this));
		this.currSeqNumbers = new ArrayList<>();
		this.ackNumber = 0;
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
			if (message instanceof Members) {
				Members m = (Members) message;
				this.processes = m;
				// this.log.info("p" + self().path().name() + " received processes info");
			}
			/** Become faulty **/
			else if (message instanceof Fail) {
				this.state = State.FAULTY;
				// this.log.info("p" + self().path().name() + " became faulty");
			}
			/** Add message to the mailbox **/
			else if (message instanceof Operation && this.state != State.NONE) {
				this.mailbox.add((Operation) message);
			}
			/** Process GET operation **/
			else if (message instanceof Get) {
				getReceived((Get) message);
			}
			/** Process PUT operation **/
			else if (message instanceof Put) {
				putReceived((Put) message);
			}
			/** Process read request **/
			else if (message instanceof ReadRequest) {
				readReqReceived((ReadRequest) message);
			}
			/** Process read response **/
			else if (message instanceof ReadResponse && (this.state == State.GET || this.state == State.PUT)) {
				readRespReceived((ReadResponse) message);
			}
			/** Process write request **/
			else if (message instanceof WriteRequest) {
				writeReqReceived((WriteRequest) message);
			}
			/** Process write response **/
			else if (message instanceof WriteResponse && this.state == State.WAIT_WRITE) {
				writeRespReceived((WriteResponse) message);
			}
		}
		/** The process is faulty **/
		else {
			// this.log.info("p" + self().path().name() + " received a message from p"+ sender().path().name() + " but is faulty");
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
		// this.log.info("p" + self().path().name() + " is terminating...");
		context().system().stop(self());
	}


	/** Private methods **/
	private void getReceived(Get msg) throws Throwable {
		this.chrono = System.nanoTime();
		this.state = State.GET;
		this.log.info("p" + self().path().name() + " is launching a get request with key " + msg.key + "...");
		sendRequests(Request.READ, msg.key);
		// this.log.info("p" + self().path().name() + " launched a get request with key '" + get.key);
	}

	private void putReceived(Put msg) throws Throwable {
		this.chrono = System.nanoTime();
		this.state = State.PUT;
		this.proposal = msg.proposal;
		this.log.info("p" + self().path().name() + " is launching a put request with key " + msg.key + " and proposal " + msg.proposal + "...");
		sendRequests(Request.READ, msg.key);
		// this.log.info("p" + self().path().name() + " launched a put request with key " + message.key + " and proposal " + message.proposal);
	}

	private void readReqReceived(ReadRequest msg) {
		int readValue = this.values.containsKey(msg.key) ? this.values.get(msg.key) : 0;
		int readTimestamp = this.timestamps.containsKey(msg.key) ? this.timestamps.get(msg.key) : 0;
		ReadResponse rs = new ReadResponse(msg.seqNumber, msg.key, readValue, readTimestamp);
		sender().tell(rs, self());
		// this.log.info("p" + self().path().name() + " responded to a read request from p" + sender().path().name());
	}
	
	private void readRespReceived(ReadResponse msg) throws Throwable {
		if (this.currSeqNumbers.contains(msg.seqNumber)) {
			this.ackNumber++;
			int readTimestamp = this.timestamps.containsKey(msg.key) ? this.timestamps.get(msg.key) : 0;
			int readValue = this.values.containsKey(msg.key) ? this.values.get(msg.key) : 0;
			if (msg.timestamp > readTimestamp || (msg.timestamp == readTimestamp && msg.value > readValue)) {
				this.timestamps.put(msg.key, msg.timestamp);
				this.values.put(msg.key, msg.value);
			}
			/** Majority **/
			if (this.ackNumber >= this.N/2) {
				this.ackNumber = 0;
				if (this.state == State.GET) {
					this.state = State.NONE;
					this.chrono = System.nanoTime() - this.chrono;
					this.log.info("p" + self().path().name() + " got the value [" + this.values.get(msg.key) + "] with key [" +
						msg.key + "] and timestamp [" + this.timestamps.get(msg.key) + "] in " + this.chrono / 1000 + "μs");
					nextOperation();
				}
				else if (this.state == State.PUT) {
					readTimestamp = this.timestamps.containsKey(msg.key) ? this.timestamps.get(msg.key) : 0;
					this.timestamps.put(msg.key, readTimestamp + 1);
					this.values.put(msg.key, this.proposal);
					this.state = State.WAIT_WRITE;
					sendRequests(Request.WRITE, msg.key);
				}
			}
		}
	}

	private void writeReqReceived(WriteRequest msg) {
		int putTimestamp = this.timestamps.containsKey(msg.key) ? this.timestamps.get(msg.key) : 0;

		if (msg.timestamp > putTimestamp || (msg.timestamp == putTimestamp && msg.proposal > this.values.get(msg.key))) {
			this.timestamps.put(msg.key, msg.timestamp);
			this.values.put(msg.key, msg.proposal);
		}
		WriteResponse ws = new WriteResponse(msg.seqNumber, msg.key);
		sender().tell(ws, self());
		// this.log.info("p" + self().path().name() + " responded to a write request from p" + sender().path().name());
	}

	private void writeRespReceived(WriteResponse msg) throws Throwable {
		if (this.currSeqNumbers.contains(msg.seqNumber)) {
			this.ackNumber++;
			/** Majority **/
			if (this.ackNumber >= this.N/2) {
				this.ackNumber = 0;
				this.state = State.NONE;
				this.chrono = System.nanoTime() - this.chrono;
				this.log.info("p" + self().path().name() + " put the value [" + this.proposal + "] with key [" + msg.key +
					"] and timestamp [" + this.timestamps.get(msg.key) + "] in " + this.chrono / 1000 + "μs");
				nextOperation();
			}
		}
	}

	private void nextOperation() throws Throwable {
		if (!this.mailbox.isEmpty()) {
			this.onReceive(this.mailbox.remove());
		}
		else {
			// this.log.info("p" + self().path().name() + " is waiting for a new job...");
			restTimer.start();
		}
	}

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

	/** Static actor creation **/
	public static Props createActor(int ID, int nb) {
		return Props.create(Process.class, () -> {
			return new Process(ID, nb);
		});
	}
}