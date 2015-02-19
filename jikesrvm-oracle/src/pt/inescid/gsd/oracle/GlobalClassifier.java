package pt.inescid.gsd.oracle;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

import org.apache.log4j.Logger;

import pt.inescid.gsd.oracle.aggregators.Aggregator;
import pt.inescid.gsd.oracle.aggregators.MeanDiffAggregator;

import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.Discretize;

public class GlobalClassifier implements IOracle {

    private final static String ATTRIBUTES[] = { "BRANCH_INSTRUCTIONS_RETIRED", "CACHE-REFERENCES", "CYCLES",
        "INSTRUCTION_RETIRED", "INSTRUCTIONS", "LLC_MISSES", "LLC_REFERENCES", "MAJOR-FAULTS", "MINOR-FAULTS",
        "MISPREDICTED_BRANCH_RETIRED", "UNHALTED_CORE_CYCLES", "UNHALTED_REFERENCE_CYCLES", "class" };
	
	private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";
	
	private final static String PROP_TRAINING_SET = "trainingSet";
	private static final String PROP_TRAINING_SET_DEFAULT = "training-set.arff";
	private final static String PROP_DISCRETIZE = "discretize";
	private final static String PROP_DISCRETIZE_DEFAULT = "false";
	
	public static final int attributesSize = ATTRIBUTES.length - 1;
	
	private static Logger log = Logger.getLogger(Oracle.class);
	
	private static String trainingSet;
	
	private static Properties properties;
	
	private static boolean discretize;
	
	private Classifier classifier;
	
	private Instances trainingInstances;
	
	private Instance instance;
	
	private Socket socket;
	
	private Aggregator aggregator = new MeanDiffAggregator(attributesSize);
	
	Discretize filter;    
    
    private String pcsToStr(double[] pcs) {
        StringBuilder sb = new StringBuilder();
        for(double pc : pcs) {
            sb.append(",");
            sb.append(pc);
        }
        return "{" + sb.toString().substring(1) + "}";
    }
    
    public GlobalClassifier(Properties properties) {
    	trainingSet = properties.getProperty(PROP_TRAINING_SET, PROP_TRAINING_SET_DEFAULT);
    	discretize = Boolean.parseBoolean(properties.getProperty(PROP_DISCRETIZE, PROP_DISCRETIZE_DEFAULT));
    }
    
    public Aggregator getAggregator() {
    	return aggregator;
    }
    
    @Override
    public void init() throws Exception {

        // creates a new trained model with a given training set
        DataSource source = new DataSource(trainingSet);
        log.info(String.format("Training set being used: %s", trainingSet));
        trainingInstances = source.getDataSet();
        trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);

        // init Attribute to be feed with values
        instance = new Instance(ATTRIBUTES.length);
        instance.setDataset(trainingInstances);

        if(discretize) {
            filter = new Discretize();
            filter.setInputFormat(trainingInstances);
            trainingInstances = Filter.useFilter(trainingInstances, filter);
        }

        // TODO implement factory class to choose classifier at runtime
        SMO baseClassifier = new SMO();
        // baseClassifier.setC(Math.pow(2, 15)); // consider the adjustment of
        // the complexity model
        baseClassifier.setKernel(new PolyKernel());
        baseClassifier.setBuildLogisticModels(true);

        classifier = baseClassifier;
        classifier.buildClassifier(trainingInstances);
    }

    @Override
    public OracleResult predict(double[] pcs, BufferedWriter fileWriter) throws Exception {
        if(pcs == null)
            return new OracleResult(-1, null);

        for (int i = 0; i < pcs.length; i++) {
            instance.setValue(i, pcs[i]);
        }

        if(discretize) {
            Instances instances = new Instances(trainingInstances, 1);
            instances.add(instance);
            instances = Filter.useFilter(instances, filter);
            instance = instances.firstInstance();
        }

        double classIndex = classifier.classifyInstance(instance);

        String classStr = instance.classAttribute().value((int) classIndex);
        double[] distribution = classifier.distributionForInstance(instance);
        double confidence = distribution[(int)classIndex];
        if (Oracle.LogALL)
	        log.info(String.format("Class %s predicted with a confidence of %f for pcs '%s'", classStr, confidence,
	                pcsToStr(pcs)));

        // write all distributions
        for (int i=0; i<distribution.length; ++i) {
        	fileWriter.write(""+distribution[i]);
        	if (i < distribution.length-1)
        		fileWriter.write(";");
        }
        fileWriter.write('\n');
        fileWriter.flush();
        
		//return confidence >= MIN_CONFIDENCE ? classStr : null;
        return new OracleResult((int)classIndex, distribution);
    }

}
