package pt.inescid.gsd.oracle.pcoutputs;

import java.io.IOException;

public interface PCOutput {
	void start(String filename) throws IOException;
	void log(double[] pcs) throws IOException;
	void stop() throws IOException;
}
