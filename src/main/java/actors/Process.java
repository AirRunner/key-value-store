package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.lang.System;
// import akka.event.Logging;
// import akka.event.LoggingAdapter;

import actors.operation.*;
import actors.operation.msg.*;
import actors.utils.*;

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
				Get get = (Get) message;
				this.chrono = System.nanoTime();
				this.state = State.GET;
				this.log.info("p" + self().path().name() + " is launching a get request with key " + get.key + "...");
				sendRequests(Request.READ, get.key);
				// this.log.info("p" + self().path().name() + " launched a get request with key '" + get.key);
			}
			/** Process PUT operation **/
			else if (message instanceof Put) {
				Put put = (Put) message;
				this.chrono = System.nanoTime();
				this.state = State.PUT;
				this.proposal = put.proposal;
				this.log.info("p" + self().path().name() + " is launching a put request with key " + put.key + " and proposal " + put.proposal + "...");
				sendRequests(Request.READ, put.key);
				// this.log.info("p" + self().path().name() + " launched a put request with key " + put.key + " and proposal " + put.proposal);
			}
			/** Process read request **/
			else if (message instanceof ReadRequest) {
				ReadRequest rq = (ReadRequest) message;
				int readValue = this.values.containsKey(rq.key) ? this.values.get(rq.key) : 0;
				int readTimestamp = this.timestamps.containsKey(rq.key) ? this.timestamps.get(rq.key) : 0;
				ReadResponse rs = new ReadResponse(rq.seqNumber, rq.key, readValue, readTimestamp);
				sender().tell(rs, self());
				// this.log.info("p" + self().path().name() + " responded to a read request from p" + sender().path().name());
			}
			/** Process read response **/
			else if (message instanceof ReadResponse && (this.state == State.GET || this.state == State.PUT)) {
				ReadResponse rs = (ReadResponse) message;
				this.ackNumber++;
				int readTimestamp = this.timestamps.containsKey(rs.key) ? this.timestamps.get(rs.key) : 0;
				int readValue = this.values.containsKey(rs.key) ? this.values.get(rs.key) : 0;
				if (rs.timestamp > readTimestamp || (rs.timestamp == readTimestamp && rs.value > readValue)) {
					this.timestamps.put(rs.key, rs.timestamp);
					this.values.put(rs.key, rs.value);
				}
				/** Majority **/
				if (this.ackNumber >= this.N/2) {
					this.ackNumber = 0;
					if (this.state == State.GET) {
						this.state = State.NONE;
						this.chrono = System.nanoTime() - this.chrono;
						this.log.info("p" + self().path().name() + " got the value [" + this.values.get(rs.key) + "] with key [" +
							rs.key + "] and timestamp [" + this.timestamps.get(rs.key) + "] in " + this.chrono / 1000 + "μs");
						nextOperation();
					}
					else if (this.state == State.PUT) {
						readTimestamp = this.timestamps.containsKey(rs.key) ? this.timestamps.get(rs.key) : 0;
						this.timestamps.put(rs.key, readTimestamp + 1);
						this.values.put(rs.key, this.proposal);
						this.state = State.WAIT_WRITE;
						sendRequests(Request.WRITE, rs.key);
					}
				}
			}
			/** Process write request **/
			else if (message instanceof WriteRequest) {
				WriteRequest wq = (WriteRequest) message;
				int putTimestamp = this.timestamps.containsKey(wq.key) ? this.timestamps.get(wq.key) : 0;

				if (wq.timestamp > putTimestamp || (wq.timestamp == putTimestamp && wq.proposal > this.values.get(wq.key))) {
					this.timestamps.put(wq.key, wq.timestamp);
					this.values.put(wq.key, wq.proposal);
				}
				WriteResponse ws = new WriteResponse(wq.seqNumber, wq.key);
				sender().tell(ws, self());
				// this.log.info("p" + self().path().name() + " responded to a write request from p" + sender().path().name());
			}
			/** Process write response **/
			else if (message instanceof WriteResponse && this.state == State.WAIT_WRITE) {
				WriteResponse wr = (WriteResponse) message;
				this.ackNumber++;
				/** Majority **/
				if (this.ackNumber >= this.N/2) {
					this.ackNumber = 0;
					this.state = State.NONE;
					this.chrono = System.nanoTime() - this.chrono;
					this.log.info("p" + self().path().name() + " put the value [" + this.proposal + "] with key [" + wr.key +
						"] and timestamp [" + this.timestamps.get(wr.key) + "] in " + this.chrono / 1000 + "μs");
					nextOperation();
				}
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
		for (ActorRef actor : this.processes.references) {
			if (actor != self()) {
				if (type == Request.WRITE) {
					request = (WriteRequest) new WriteRequest(UUID.randomUUID(), key, this.values.get(key), this.timestamps.get(key));
				}
				else {
					request = (ReadRequest) new ReadRequest(UUID.randomUUID(), key);
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