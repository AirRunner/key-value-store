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

    public static void faultyActors(ArrayList<ActorRef> r) {
        int nbFaulty = N % 2 == 0 ? (N - 1) / 2 : N / 2;
        Collections.shuffle(r);
        for (int i = 0; i < nbFaulty; i++) {
            r.get(i).tell(new Fail(), ActorRef.noSender());
        }
    }

    public static void launch(ArrayList<ActorRef> r) {
        for (ActorRef actor : r) {
            for (int i = 0; i < M; i++) {
                actor.tell(new Put(i * N + Integer.parseInt(actor.path().name())), ActorRef.noSender());
                actor.tell(new Get(), ActorRef.noSender());
            }
        }
    }

    public static void waitBeforeTerminate() throws InterruptedException {
        Thread.sleep(5000);
    }

    public static void main(String[] args) throws InterruptedException {

        // Set N and M from command line arguments
        try {
            if (args.length >= 2) {
                N = Integer.parseInt(args[0]);
                M = Integer.parseInt(args[1]);
            }
        }
        catch (NumberFormatException e) {
            System.out.println("[WARNING] Invalid arguments. Setting N=3, M=3.");
        }
        

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N + " and M=" + M);

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
        faultyActors(references);
        launch(references);

        // Wait 5 seconds before ending the program
        try {
            waitBeforeTerminate();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            system.terminate();
        }
    }
}
