package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

import actors.msg.Fail;
import actors.msg.Get;
import actors.msg.Operation;
import actors.msg.Put;
import actors.msg.ReadRequest;
import actors.msg.ReadResponse;
import actors.msg.WriteRequest;
import actors.msg.WriteResponse;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);   // Logger attached to actor
    private final int N;    // Number of processes
    private final int id;   // ID of current process
    private Members processes;  // other processes' references
    private int proposal;
    private int value;
    private int timestamp;
    private State state;
    private int ackNumber;
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
        return "Process{" + "id=" + id;
    }
    
    public enum State {
    	PUT, GET, FAULTY, WAIT, NONE;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }
    
    
    public void onReceive(Object message) throws Throwable {
    	if (this.state != State.FAULTY) {
    		if (message instanceof Members) { // Save the system's info
    			Members m = (Members) message;
    			this.processes = m;
    			log.info("p" + self().path().name() + " received processes info");
    		}
    		else if (message instanceof Fail) {
    			this.state = State.FAULTY;
    			log.info("p" + self().path().name() + " becomes faulty");
    		}
    		else if (this.state == State.NONE && message instanceof Get) {
    			this.state = State.GET;
    			for (ActorRef actor : this.processes.references) {
    				if (actor != self()) {
    					ReadRequest rq = new ReadRequest(UUID.randomUUID());
        				actor.tell(rq, self());
    				}
    			}
    			log.info("p" + self().path().name() + " launched a get request");
    		}
    		else if (this.state == State.NONE && message instanceof Put) {
    			this.state = State.PUT;
    			this.proposal = ((Put) message).proposal;
    			for (ActorRef actor : this.processes.references) {
    				if (actor != self()) {
    					ReadRequest rq = new ReadRequest(UUID.randomUUID());
        				actor.tell(rq, self());
    				}
    			}
    			log.info("p" + self().path().name() + " launched a put request");
    		}
    		else if (message instanceof ReadRequest) {
    			ReadRequest rq = (ReadRequest) message;
    			ReadResponse rs = new ReadResponse(rq.seqNumber, this.value, this.timestamp);
    			sender().tell(rs, self());
    			log.info("p" + self().path().name() + " responded to a read request from p" + sender().path().name());
    		}
    		else if (this.state != State.NONE && this.state != State.WAIT && message instanceof ReadResponse) {
    			ReadResponse rs = (ReadResponse) message;
    			this.ackNumber++;
				if (rs.timestamp > this.timestamp) {
					this.timestamp = rs.timestamp;
					this.value = rs.value;
				}
    			if (this.state == State.GET) {
    				if (this.ackNumber >= this.N/2) {
    					this.state = State.NONE;
    					this.ackNumber = 0;
    					log.info("p" + self().path().name() + " got the value [" + this.value + "] with timestamp [" + this.timestamp + "]");
    				}
    			}
    			else if (this.state == State.PUT) {
    				if (this.ackNumber >= this.N/2) {
    					this.timestamp++;
    					this.value = this.proposal;
    					for (ActorRef actor : this.processes.references) {
    	    				if (actor != self()) {
    	    					WriteRequest wq = new WriteRequest(UUID.randomUUID(), this.value, this.timestamp);
    	        				actor.tell(wq, self());
    	    				}
    	    			}
    					this.state = State.WAIT;
    					this.ackNumber = 0;
    				}
    			}
    		}
    		else if (message instanceof WriteRequest) {
    			WriteRequest wq = (WriteRequest) message;
    			this.timestamp = wq.timestamp;
    			this.value = wq.proposal;
    			WriteResponse ws = new WriteResponse(wq.seqNumber);
    			sender().tell(ws, self());
    			log.info("p" + self().path().name() + " responded to a write request from p" + sender().path().name());
    		}
    		else if (this.state != State.NONE && message instanceof WriteResponse) {
    			this.ackNumber++;
    			if (this.ackNumber >= this.N/2) {
					this.state = State.NONE;
					this.ackNumber = 0;
					log.info("p" + self().path().name() + " put the value [" + this.value + "] with timestamp [" + this.timestamp + "]");
				}
    		}
    	}
    	else {
    		log.info("p" + self().path().name() + " received a message but it is faulty");
    	}
    }
}
