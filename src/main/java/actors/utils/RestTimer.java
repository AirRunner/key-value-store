package actors.utils;

import actors.Process;
import actors.Process.State;

public class RestTimer implements Runnable {
	
	private Process proc;
	
	public RestTimer(Process proc) {
		this.proc = proc;
	}
	
	@Override
	public void run() {
        int i = 0;
        while (i < 100) {
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (proc.getState() != State.NONE) {
                break;
            }
            i++;
        }
        if (i >= 100) {
            proc.terminate();
        }
	}
}