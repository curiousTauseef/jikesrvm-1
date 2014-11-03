package pt.inescid.gsd.oracle.aggregators;

public abstract class Aggregator {

    protected int numberOfItems;

    public Aggregator(int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    public abstract double[] process(double[] items);
}
