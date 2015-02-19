package gsd.jikesrvm.hdwcounters;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;
//import org.jikesrvm.mm.mmtk.Statistics;
import org.mmtk.utility.Log;
//import org.mmtk.utility.statistics.PerfEvent;
import org.vmmagic.pragma.NonMoving;

import static org.jikesrvm.runtime.SysCall.sysCall;

@NonMoving
public class PfmCountersWatchDog  extends Thread implements ExitMonitor {
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
	
	/** {@code true} if any data was scaled */
	public static boolean dataWasScaled = false;

	/** interval = number of miliseconds between reads */
	private long interval;

	/**	array of counter's values for the watchdog */
	private BufferedWriter counters[];
	/**	array of counter's names for the watchdog */
	private String perfEventNames[];
	/** max values */
	private static int MAXVALUES = 4096; 
	
	public static boolean enabled;
	
	public static final boolean LOG_ALL = false;

	protected PfmCountersWatchDog(String name, long interval, String eventsNames, String prefix) {
		super(name);
		//this.interval = interval<MIN_INTERVAL ? MIN_INTERVAL : interval;
		this.interval = interval;
		perfEventNames = eventsNames.split(",");
		counters = new BufferedWriter[perfEventNames.length];
		/* */
		int n = perfEventNames.length;
		sysCall.sysPerfEventInit(n);
		for (int i = 0; i < n; i++) {
			sysCall.sysPerfEventCreate(i, perfEventNames[i].concat("\0").getBytes());
			try {
				counters[i] = new BufferedWriter(new FileWriter(prefix+"_"+perfEventNames[i]));
			} catch (IOException e) {
				Log.write("Error creating file for event " + perfEventNames[i]);
				System.exit(0);
			}
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
			PfmCountersWatchDog ft = new PfmCountersWatchDog(
					"perf-mon-watch-dog",
					100/*Long.parseLong(System.getenv("wdoginterval"))*/,
					System.getenv("wdogevents"),
					System.getenv("wdogprefix"));
			ft.start();
		} else {
			Log.writeln("[perf-mon-watch-dog] watch dog disabled");			
		}
	}

	@Override
	public void run() {
		if (!enabled)
			return;
		
		Log.writeln("[perf-mon-watch-dog] start");
		Log.writeln("[perf-mon-watch-dog] reading at each " + interval + " milisencods");
//		for (int i=0; i<counters.length; ++i) {
//			counters[i] = getCurrentValue(i);
//			Log.write(perfEventNames[i] + ", ");
//		}
		while (enabled) {
			try {
				for (int i=0; i<counters.length; ++i) {
					long value = getCurrentValue(i);
//					Log.writeln("value="+value+"\t rate="+(value-counters[i])/interval + "\t");
//					counters[i] = value;
					try {
						counters[i].write(Long.toString(value));
						counters[i].write(',');
						counters[i].flush();
					} catch (IOException e) {
						Log.write("Error writing value for event " + perfEventNames[i]);
						System.exit(0);
					}
				}
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
//		/* print header with names of counters */ 
//		for (int i=0; i<counters.length; ++i) {
//			Log.write(perfEventNames[i] + "\t");
//		}
//		Log.writeln();
//		/* print rate of counters */ 
//		for (int i=0; i<counters.length; ++i) {
//			Log.write(counters[i] + "\t");
//		}
//		Log.writeln();		
//		Log.writeln("[perf-mon-watch-dog] end");
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
		PfmCountersWatchDog.enabled = false;
		// end of hack
	}

}
