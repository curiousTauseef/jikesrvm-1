package pt.inescid.gsd.oracle;

public interface IOracle {
	enum Matrix {A, B, C, D}
	
	void init() throws Exception;
	Matrix predict(double[] pcs) throws Exception;
}
