package pt.inescid.gsd.oracle.pcoutputs;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;



public class PCFileOutput implements PCOutput {

	private BufferedWriter writer = null; 
	
	@Override
	public void start(String filename) throws IOException {
		writer = new BufferedWriter(new FileWriter(filename));
	}

	@Override
	public void log(double[] pcs) throws IOException {
		StringBuffer out = new StringBuffer();
		for (int i=0; i<pcs.length; ++i) {
			out.append(pcs[i]);
			if (i+1<pcs.length) {
				out.append(",");
			}
		}
		out.append("\n");
		writer.write(out.toString());
	}

	@Override
	public void stop() throws IOException {
		writer.close();
	}

}
