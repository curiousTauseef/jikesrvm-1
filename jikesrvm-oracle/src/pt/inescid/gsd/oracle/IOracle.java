package pt.inescid.gsd.oracle;

public interface IOracle {
	void init() throws Exception;
	String predict(double[] pcs) throws Exception;
}
