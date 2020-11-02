package actors;

import java.util.ArrayList;
import java.util.Collections;

import actors.operation.Get;
import actors.operation.Put;
import actors.operation.Fail;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {

    public static int N = 3;
    public static int M = 3;

    public static ArrayList<String> faultyActors(ArrayList<ActorRef> refs) {
        ArrayList<String> faulty = new ArrayList<>();
        int nbFaulty = N % 2 == 0 ? (N - 1) / 2 : N / 2;
        Collections.shuffle(refs);
        for (int i = 0; i < nbFaulty; i++) {
            refs.get(i).tell(new Fail(), ActorRef.noSender());
            faulty.add(refs.get(i).path().name());
        }
        return faulty;
    }

    public static void launch(ArrayList<ActorRef> r) {
        for (ActorRef actor : r) {
            for (int i = 0; i < M; i++) {
                actor.tell(new Put(1, i * N + Integer.parseInt(actor.path().name())), ActorRef.noSender());
                actor.tell(new Get(1), ActorRef.noSender());
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        // system.log().info("System started with N=" + N + " and M=" + M);

        // Set N and M from command line arguments
        try {
            if (args.length >= 2) {
                N = Integer.parseInt(args[0]);
                M = Integer.parseInt(args[1]);
            }
        }
        catch (NumberFormatException e) {
            system.log().warning("Invalid arguments. Setting N=3, M=3.");
        }

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
        ArrayList<String> faulty = faultyActors(references);
        launch(references);

        // Try to terminate the system
        Boolean run = true;
        while (run) {
            run = false;
            for (ActorRef actor : references) {
                if (!actor.isTerminated() && !faulty.contains(actor.path().name())) {
                    run = true;
                    break;
                }
            }
            Thread.sleep(100);
        }
        system.terminate();
    }
}
