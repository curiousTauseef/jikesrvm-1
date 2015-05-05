package pt.inescid.gsd.oracle.aggregators;

public abstract class Aggregator {

	protected boolean first;
	
    protected int numberOfItems;

    public Aggregator(int numberOfItems) {
        this.numberOfItems = numberOfItems;
        this.first = true;
    }

    public abstract double[] process(double[] items);
    
    public void start() {
    	first = true;
    }
}
