package pt.inescid.gsd.oracle.aggregators;

public class MeanDiffAggregator extends Aggregator {

    private double[] previousItems;

    private double[] sum;

    private int[] count;

    public MeanDiffAggregator(int numberOfItems) {
        super(numberOfItems);
        previousItems = new double[numberOfItems];
        sum = new double[numberOfItems];
        count = new int[numberOfItems];
    }

    public double[] process(double[] items) {
        double[] result = null;
        
        if (!first) {
        	result = new double[numberOfItems];
        }
        
        for (int i = 0; i < numberOfItems; i++) {
            if(!first) {
                double diff = items[i] - previousItems[i];
                sum[i] += diff;
                count[i]++;
                result[i] = sum[i] / (double)count[i];
            }
            previousItems[i] = items[i];
        }

        first = false;
        
        return result;
    }

}
