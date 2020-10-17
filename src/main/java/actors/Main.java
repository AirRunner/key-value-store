package actors;

import java.util.ArrayList;

import actors.msg.Get;
import actors.msg.Put;
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
        
        m.references.get(2).tell(new Put(5), ActorRef.noSender());
        m.references.get(2).tell(new Get(), ActorRef.noSender());
    }
}
