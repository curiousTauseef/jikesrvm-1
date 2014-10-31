public class MeanDiffAggregator implements Aggregator {

    private double[] previousItems;

    private double[] sum;

    private double[] count;


    public double[] process(double[] items) {



      for(int i = 0; i < items.length; i++) {

          sum[i] += items[i];
          count[i]++;

      }


    }

}
