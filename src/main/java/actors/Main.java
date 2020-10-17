package actors;

import java.util.ArrayList;

import actors.operation.Get;
import actors.operation.Put;
import actors.operation.Fail;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {

    public static int N = 10;

    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N);

        ArrayList<ActorRef> references = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N), "" + i);
            references.add(a);
        }

        // Give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }
        
        // Begin tests
        ActorRef p0 = m.references.get(0);
        ActorRef p3 = m.references.get(3);
        ActorRef p5 = m.references.get(5);
        ActorRef p7 = m.references.get(7);

        p3.tell(new Fail(), ActorRef.noSender());
        p5.tell(new Fail(), ActorRef.noSender());
        p0.tell(new Put(2), ActorRef.noSender());
        p0.tell(new Get(), ActorRef.noSender());
        p7.tell(new Get(), ActorRef.noSender());
        p7.tell(new Put(8), ActorRef.noSender());
    }
}
