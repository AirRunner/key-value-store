package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.lang.System;

import actors.operation.*;
import actors.operation.msg.*;

public class Process extends UntypedAbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);   // Logger attached to actor
	private final int N;	// Number of processes
	private final int id;   // ID of current process
	private Members processes;  // other processes' references
	private int proposal;
	private int value;
	private int timestamp;
	private int ackNumber;
	private State state;
	private long timer;
	private Queue<Operation> mailbox;

	public Process(int ID, int nb) {
		this.N = nb;
		this.id = ID;
		this.value = 0;
		this.timestamp = 0;
		this.state = State.NONE;
		this.ackNumber = 0;
		this.mailbox = new LinkedList<>();
	}

	public String toString() {
		return "Process {" + "id: " + this.id
			+ ", state: " + this.state
			+ ", value: " + this.value
			+ ", timestamp: " + this.timestamp + "}";
	}
	
	// On message reception
	public void onReceive(Object message) throws Throwable {
		if (this.state != State.FAULTY) {
			// Save the members of the system
			if (message instanceof Members) {
				Members m = (Members) message;
				this.processes = m;
				// this.log.info("p" + self().path().name() + " received processes info");
			}
			// Become faulty
			else if (message instanceof Fail) {
				this.state = State.FAULTY;
				// this.log.info("p" + self().path().name() + " became faulty");
			}
			// Add message to the mailbox
			else if (message instanceof Operation && this.state != State.NONE) {
				this.mailbox.add((Operation) message);
			}
			// Process GET operation
			else if (message instanceof Get) {
				this.timer = System.nanoTime();
				this.state = State.GET;
				this.log.info("p" + self().path().name() + " is launching a get request...");
				sendRequests(Request.READ);
				// this.log.info("p" + self().path().name() + " launched a get request");
			}
			// Process PUT operation
			else if (message instanceof Put) {
				this.timer = System.nanoTime();
				this.state = State.PUT;
				this.proposal = ((Put) message).proposal;
				this.log.info("p" + self().path().name() + " is launching a put request...");
				sendRequests(Request.READ);
				// this.log.info("p" + self().path().name() + " launched a put request");
			}
			// Process read request
			else if (message instanceof ReadRequest) {
				ReadRequest rq = (ReadRequest) message;
				ReadResponse rs = new ReadResponse(rq.seqNumber, this.value, this.timestamp);
				sender().tell(rs, self());
				// this.log.info("p" + self().path().name() + " responded to a read request from p" + sender().path().name());
			}
			// Process read response
			else if (message instanceof ReadResponse && (this.state == State.GET || this.state == State.PUT)) {
				ReadResponse rs = (ReadResponse) message;
				this.ackNumber++;
				if (rs.timestamp > this.timestamp || (rs.timestamp == this.timestamp && rs.value > this.value)) {
					this.timestamp = rs.timestamp;
					this.value = rs.value;
				}
				if (this.ackNumber >= this.N/2) {
					this.ackNumber = 0;
					if (this.state == State.GET) {
						this.state = State.NONE;
						this.timer = System.nanoTime() - this.timer;
						this.log.info("p" + self().path().name() + " got the value [" + this.value + "] with timestamp [" + this.timestamp + "] in " + this.timer / 1000 + "μs");
						processNext();
					}
					else if (this.state == State.PUT) {
						this.timestamp++;
						this.value = this.proposal;
						this.state = State.WAIT_WRITE;
						sendRequests(Request.WRITE);
					}
				}
			}
			// Process write request
			else if (message instanceof WriteRequest) {
				WriteRequest wq = (WriteRequest) message;
				if (wq.timestamp > this.timestamp || (wq.timestamp == this.timestamp && wq.proposal > this.value)) {
					this.timestamp = wq.timestamp;
					this.value = wq.proposal;
				}
				WriteResponse ws = new WriteResponse(wq.seqNumber);
				sender().tell(ws, self());
				// this.log.info("p" + self().path().name() + " responded to a write request from p" + sender().path().name());
			}
			// Process write response
			else if (message instanceof WriteResponse && this.state == State.WAIT_WRITE) {
				this.ackNumber++;
				if (this.ackNumber >= this.N/2) {
					this.ackNumber = 0;
					this.state = State.NONE;
					this.timer = System.nanoTime() - this.timer;
					this.log.info("p" + self().path().name() + " put the value [" + this.proposal + "] with timestamp [" + this.timestamp + "] in " + this.timer / 1000 + "μs");
					processNext();
				}
			}
		}
		// The process is faulty
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


	/** Private methods **/
	private void processNext() throws Throwable {
		if (!this.mailbox.isEmpty()) {
			this.onReceive(this.mailbox.remove());
		}
	}

	private void sendRequests(Request type) {
		Object request;
		for (ActorRef actor : this.processes.references) {
			if (actor != self()) {
				if (type == Request.WRITE) {
					request = (WriteRequest) new WriteRequest(UUID.randomUUID(), this.value, this.timestamp);
				}
				else {
					request = (ReadRequest) new ReadRequest(UUID.randomUUID());
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