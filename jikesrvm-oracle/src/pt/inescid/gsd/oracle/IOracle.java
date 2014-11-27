package pt.inescid.gsd.oracle;

import java.io.BufferedWriter;

public interface IOracle {
	void init() throws Exception;
	OracleResult predict(double[] pcs, BufferedWriter fileWriter) throws Exception;
}
