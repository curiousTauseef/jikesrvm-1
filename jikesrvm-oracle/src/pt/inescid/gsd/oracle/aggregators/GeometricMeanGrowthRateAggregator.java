package pt.inescid.gsd.oracle.aggregators;

public class GeometricMeanGrowthRateAggregator extends Aggregator {

    private double[] previousItems;

    private double[] product;

    private int[] count;

    public GeometricMeanGrowthRateAggregator(int numberOfItems) {
        super(numberOfItems);
        previousItems = new double[numberOfItems];
        product = new double[numberOfItems];
        count = new int[numberOfItems];
    }

    @Override
    public double[] process(double[] items) {
        double[] result = first? null : new double[numberOfItems];

        for (int i = 0; i < numberOfItems; i++) {
            if(!first) {
                double growthRate = items[i] / previousItems[i];
                product[i] *= growthRate;
                count[i]++;
                result[i] = Math.pow(product[i], 1.0 / (double) count[i]);
            }
            previousItems[i] = items[i];
        }
        first = false;

        return result;
    }
}
