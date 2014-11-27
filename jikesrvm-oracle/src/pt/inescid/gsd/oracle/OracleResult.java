package pt.inescid.gsd.oracle;

public class OracleResult {
	public int matrix;
	public double[] confidences;
	public OracleResult(int matrix, double[] confidences) {
		this.matrix = matrix;
		this.confidences = confidences;
	}
	public String toString() {
		return "M="+matrix;
	}
}
