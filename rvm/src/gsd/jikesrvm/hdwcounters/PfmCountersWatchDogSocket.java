package gsd.jikesrvm.hdwcounters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.*;

import org.jikesrvm.Callbacks;
import org.jikesrvm.CommandLineArgs;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;
//import org.jikesrvm.mm.mmtk.Statistics;
import org.mmtk.plan.ControllerCollectorContext;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.HeapGrowthManager;
//import org.mmtk.utility.statistics.PerfEvent;
import org.vmmagic.pragma.NonMoving;

import static org.jikesrvm.runtime.SysCall.sysCall;

@NonMoving
public class PfmCountersWatchDogSocket  extends Thread implements ExitMonitor {
	/** {@code true} if the counter did not run due to contention for a physical counter */
	private boolean contended;

	/** {@code true} if the counter did not run all of the time and has been scaled appropriately */
	private boolean scaled;

	/** {@code true} if the counter overflowed */
	private boolean overflowed;

	/** The previously read value of the counter (used to detect overflow) */
	private long previousValue;

	/** A buffer passed to the native code when reading values, returns the tuple RAW_COUNT, TIME_ENABLED, TIME_RUNNING */
	private final long[] readBuffer = new long[3];
	private static final int RAW_COUNT = 0;
	private static final int TIME_ENABLED = 1;
	private static final int TIME_RUNNING = 2;
	//private static final long MIN_INTERVAL = 500;
	private static final int SERVER_PORT = 5005;
	
	/** {@code true} if any data was scaled */
	public static boolean dataWasScaled = false;

	/** interval = number of miliseconds between reads */
	private long interval;
	
	/* Socket connection to Local monitor */
	Socket socket;

	/**	array of counter's names for the watchdog */
	private String perfEventNames[];
	/** max values */
	private static int MAXVALUES = 4096;
	
	public static boolean enabled;

	protected PfmCountersWatchDogSocket(String name, long interval, String eventsNames, String prefix) {
		super(name);

		// set threshold
		threshold = Integer.parseInt(System.getenv("THRESHOLD"));
		
		// read the server port from env variable 
		int port = Integer.parseInt(System.getenv("MLPORT"));
		
		// create socket to local monitor
		socket = new Socket();
		try {
			InetSocketAddress localserver = new InetSocketAddress(InetAddress.getLocalHost(), port);
			Log.writeln("[perf-mon-watch-dog] tying to connect to " + localserver);
			socket.connect(localserver);
		} catch (IOException e) {
			Log.writeln("[perf-mon-watch-dog] error connecting to server.");
			Log.writeln("[perf-mon-watch-dog] watch dog disabled.");
			return;
		}
		
		//this.interval = interval<MIN_INTERVAL ? MIN_INTERVAL : interval;
		this.interval = interval;
		Log.writeln("[perf-mon-watch-dog] initiating events " + eventsNames);
		perfEventNames = eventsNames.split(",");
		/* */
		int n = perfEventNames.length;
		sysCall.sysPerfEventInit(n);
		for (int i = 0; i < n; i++) {
			sysCall.sysPerfEventCreate(i, perfEventNames[i].concat("\0").getBytes());
		}
		sysCall.sysPerfEventEnable();
		enabled = true;
		Callbacks.addExitMonitor(this);  // dosen't work
		super.setDaemon(false);
	}

	/**
	 * wdog-enabled = boolean
	 * wdog-interval = number of miliseconds between reads
	 * wdog-events = comma-separated list of events 
	 */
	public static void boot() {
		Log.writeln("[perf-mon-watch-dog] booting");
		boolean enabled = Boolean.parseBoolean(System.getenv("wdogenabled"));
		if (enabled) {
			PfmCountersWatchDogSocket ft = new PfmCountersWatchDogSocket(
					"perf-mon-watch-dog",
					100/*Long.parseLong(System.getenv("wdoginterval"))*/,
					System.getenv("wdogevents"),
					System.getenv("wdogprefix"));
			ft.start();
		} else {
			Log.writeln("[perf-mon-watch-dog] watch dog disabled");			
		}
	}

	private int previousMatrix;
	private int threshold;
	
	@Override
	public void run() {
		if (!enabled)
			return;		
		Log.writeln("[perf-mon-watch-dog] start");
		Log.writeln("[perf-mon-watch-dog] reading at each " + interval + " milisencods");
		try {
			StringBuilder sb = new StringBuilder();
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			// write applications name
			String appname = System.getenv("appname");
			Log.writeln("[perf-mon-watch-dog] app name is " + appname);
			writer.write(appname+"\n");
			
			int previousMatrix = 0, countDiffDecisions=0;
			
			//BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			while (enabled) {
				try {
					for (int i=0; i<perfEventNames.length; ++i) {
						long value = getCurrentValue(i);
						sb.append(value);
						sb.append(',');
					}
					sb.append("\n");
					//Log.writeln("[perf-mon-watch-dog] writing HPC values");
					
					// send to remote monitor
					writer.write(sb.toString());
					writer.flush();
					sb.delete(0,sb.length());
					
					// read response
					int newMatrix = Integer.parseInt(reader.readLine());
					
					if (newMatrix != previousMatrix) {
						countDiffDecisions++;
						if (countDiffDecisions == threshold) {
							countDiffDecisions=0;
							// change matrix
							Log.writeln("[perf-mon-watch-dog] Changing MATRIX to " + newMatrix);
							HeapGrowthManager.setGenerationGrowthRate(newMatrix);
							Runtime.getRuntime().gc(); // ??
							previousMatrix=newMatrix;
						}
					}
					
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		} catch (IOException ex) {
			Log.writeln("[perf-mon-watch-dog] exception... exiting.");
			System.exit(-1);
		}
	}

	protected long getCurrentValue(int index) {
		VM.statistics.perfEventRead(index, readBuffer);
		if (readBuffer[RAW_COUNT] < 0 || readBuffer[TIME_ENABLED] < 0 || readBuffer[TIME_RUNNING] < 0) {
			// Negative implies they have exceeded 63 bits.
			overflowed = true;
		}
		if (readBuffer[TIME_ENABLED] == 0) {
			// Counter never run (assume contention)
			contended = true;
		}
		// Was the counter scaled?
		if (readBuffer[TIME_ENABLED] != readBuffer[TIME_RUNNING]) {
			scaled = true;
			dataWasScaled = true;
			double scaleFactor;
			if (readBuffer[TIME_RUNNING] == 0) {
				scaleFactor = 0;
			} else {
				scaleFactor = readBuffer[TIME_ENABLED] / readBuffer[TIME_RUNNING];
			}
			readBuffer[RAW_COUNT] = (long) (readBuffer[RAW_COUNT] * scaleFactor);
		}
		if (readBuffer[RAW_COUNT] < previousValue) {
			// value should monotonically increase
			overflowed = true;
		}
		previousValue = readBuffer[RAW_COUNT];
		return readBuffer[RAW_COUNT];
	}

	@Override
	public void notifyExit(int value) {
		// JS: hack to end watch dog
	    Log.writeln("exiting watch dog");
		PfmCountersWatchDogSocket.enabled = false;
		// end of hack
	}

}
