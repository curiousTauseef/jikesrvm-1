package pt.inescid.gsd.oracle;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;

import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class Oracle implements IOracle {

    private final static String ATTRIBUTES[] = { "BRANCH_INSTRUCTIONS_RETIRED", "CACHE-REFERENCES", "CYCLES",
            "INSTRUCTION_RETIRED", "INSTRUCTIONS", "LLC_MISSES", "LLC_REFERENCES", "MAJOR-FAULTS", "MINOR-FAULTS",
            "MISPREDICTED_BRANCH_RETIRED", "UNHALTED_CORE_CYCLES", "UNHALTED_REFERENCE_CYCLES", "class" };

    private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";

    private final static String TRAINING_SET_PROP = "trainingSet";

    private static final String TRAINING_SET_FILENAME = "training-set.arff";

    private Logger log = Logger.getLogger(Oracle.class);

    private String trainingSet;

    private Classifier classifier;

    private Instances trainingInstances;

    public Oracle() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(PROPERTIES_FILE));
        } catch (IOException e) {
            log.info("Not possible to load properties file '" + PROPERTIES_FILE + "'.");
        }
        trainingSet = properties.getProperty(TRAINING_SET_PROP, TRAINING_SET_FILENAME);

    }

    @Override
    public void init() throws Exception {

        // creates a new trained model with a given training set
        DataSource source = new DataSource(trainingSet);
        System.out.println("trainingSet: " + trainingSet);
        trainingInstances = source.getDataSet();
        trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);

        // TODO implement factory class to choose classifier at runtime
        SMO baseClassifier = new SMO();
        // baseClassifier.setC(Math.pow(2, 15)); // consider the adjustment of
        // the complexity model
        baseClassifier.setKernel(new PolyKernel());

        classifier = (Classifier) baseClassifier;
        classifier.buildClassifier(trainingInstances);
    }

    @Override
    public Matrix predict(double[] pcs) throws Exception {

        Instance instance = new Instance(ATTRIBUTES.length);
        instance.setDataset(trainingInstances);
        for (int i = 0; i < pcs.length; i++)
            instance.setValue(i, pcs[i]);

        for (int i = 0; i < pcs.length; i++) {
            instance.setValue(i, pcs[i]);
        }
        // double[] distribution = classifier.distributionForInstance(instance);
        double result = classifier.classifyInstance(instance);

        System.out.println("result: " + instance.classAttribute().value((int) result));

        return null;
    }

    // for testing purposes
    public static void main(String[] args) {
        try {
            Oracle oracle = new Oracle();

            oracle.init();

            double[] pcs = new double[] { 112709888, 6203905, 1022179323, 1042568017, 522040499, 598977, 12401291, 0,
                    13, 3672798, 1273605903, 70 }; // should be classified as M3
            oracle.predict(pcs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}