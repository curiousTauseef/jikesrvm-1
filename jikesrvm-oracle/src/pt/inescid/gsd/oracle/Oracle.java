package pt.inescid.gsd.oracle;

import org.apache.log4j.Logger;

import pt.inescid.gsd.oracle.aggregators.Aggregator;
import pt.inescid.gsd.oracle.aggregators.MeanDiffAggregator;
import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.NormalizedPolyKernel;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.Discretize;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class Oracle extends Thread {

//    private final static String ATTRIBUTES[] = { "BRANCH_INSTRUCTIONS_RETIRED", "CACHE-REFERENCES", "CYCLES",
//            "INSTRUCTION_RETIRED", "INSTRUCTIONS", "LLC_MISSES", "LLC_REFERENCES", "MAJOR-FAULTS", "MINOR-FAULTS",
//            "MISPREDICTED_BRANCH_RETIRED", "UNHALTED_CORE_CYCLES", "UNHALTED_REFERENCE_CYCLES", "class" };
//
//    private static final double MIN_CONFIDENCE = 0.9;
//
	private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";
//
//    private final static String PROP_TRAINING_SET = "trainingSet";
//    private static final String PROP_TRAINING_SET_DEFAULT = "training-set.arff";
//    private final static String PROP_DISCRETIZE = "discretize";
//    private final static String PROP_DISCRETIZE_DEFAULT = "false";
    private static final String PROP_PORT = "port";
    private static final String PROP_PORT_DEFAULT = "5005";
//
//    private static final int attributesSize = ATTRIBUTES.length - 1;
//
//    private static String trainingSet;
//
//    private static Properties properties;
//
//    private static boolean discretize;
//    
//    private Classifier classifier;
//
//    private Instances trainingInstances;
//
//    private Instance instance;
//   
//    private Aggregator aggregator = new MeanDiffAggregator(attributesSize);
//
//    Discretize filter;

	private Socket socket;
	private GlobalClassifier classifier;
	
	public static final boolean LogALL = false;
    public static Logger log = Logger.getLogger(Oracle.class);

	
//    static {
//        properties = new Properties();
//        try {
//            properties.load(new FileInputStream(PROPERTIES_FILE));
//        } catch (IOException e) {
//            log.warn(String.format("It was not possible to load properties file '%s'.", PROPERTIES_FILE));
//        }
//        trainingSet = properties.getProperty(PROP_TRAINING_SET, PROP_TRAINING_SET_DEFAULT);
//        discretize = Boolean.parseBoolean(properties.getProperty(PROP_DISCRETIZE, PROP_DISCRETIZE_DEFAULT));
//    }
    
    public Oracle(GlobalClassifier classifier, Socket socket) {
        this.classifier = classifier;
    	this.socket = socket;
    }

//    @Override
//    public void init() throws Exception {
//
//        // creates a new trained model with a given training set
//        DataSource source = new DataSource(trainingSet);
//        log.info(String.format("Training set being used: %s", trainingSet));
//        trainingInstances = source.getDataSet();
//        trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);
//
//        // init Attribute to be feed with values
//        instance = new Instance(ATTRIBUTES.length);
//        instance.setDataset(trainingInstances);
//
//        if(discretize) {
//            filter = new Discretize();
//            filter.setInputFormat(trainingInstances);
//            trainingInstances = Filter.useFilter(trainingInstances, filter);
//        }
//
//        // TODO implement factory class to choose classifier at runtime
//        SMO baseClassifier = new SMO();
//        // baseClassifier.setC(Math.pow(2, 15)); // consider the adjustment of
//        // the complexity model
//        baseClassifier.setKernel(new PolyKernel());
//        baseClassifier.setBuildLogisticModels(true);
//
//        classifier = baseClassifier;
//        classifier.buildClassifier(trainingInstances);
//    }
    
//    @Override
//    public OracleResult predict(double[] pcs, BufferedWriter fileWriter) throws Exception {
//        if(pcs == null)
//            return new OracleResult(-1, null);
//
//        for (int i = 0; i < pcs.length; i++) {
//            instance.setValue(i, pcs[i]);
//        }
//
//        if(discretize) {
//            Instances instances = new Instances(trainingInstances, 1);
//            instances.add(instance);
//            instances = Filter.useFilter(instances, filter);
//            instance = instances.firstInstance();
//        }
//
//        double classIndex = classifier.classifyInstance(instance);
//
//        String classStr = instance.classAttribute().value((int) classIndex);
//        double[] distribution = classifier.distributionForInstance(instance);
//        double confidence = distribution[(int)classIndex];
//        if (Oracle.LogALL)
//	        log.info(String.format("Class %s predicted with a confidence of %f for pcs '%s'", classStr, confidence,
//	                pcsToStr(pcs)));
//
//        // write all distributions
//        for (int i=0; i<distribution.length; ++i) {
//        	fileWriter.write(""+distribution[i]);
//        	if (i < distribution.length-1)
//        		fileWriter.write(";");
//        }
//        fileWriter.write('\n');
//        fileWriter.flush();
//        
//		//return confidence >= MIN_CONFIDENCE ? classStr : null;
//        return new OracleResult((int)classIndex, distribution);
//    }

    
    public void run() {
    	log.info("connected to a JVM:" + socket.getPort());
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String appName = in.readLine();
            log.info(appName);
            
            BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("LOG_" + appName + ".csv")));
            fileWriter.write("M0;M1;M2;M3\n");
            
            try {
	            // FIXME training a model for each thread?
//	            init();
	            while(true) {
	            	String line = in.readLine();
	            	if (line == null || line.equals("exit"))
	            		break;
	                if (!line.contains(","))
	                    continue;
	                if (Oracle.LogALL)
	                	log.info(String.format("Received instance to classify: '%s'", line));
	
	                String[] items = line.split(",");
	                
	                if(GlobalClassifier.attributesSize != items.length) {
	                    log.error(String.format("Number of provided pcs: %d (required: %d)", items.length, 
	                    		GlobalClassifier.attributesSize));
	                    return;
	                }
	                double[] pcs = new double[GlobalClassifier.attributesSize];
			        for(int i = 0; i < items.length; i++) {
	                    pcs[i] = Double.parseDouble(items[i]);
	                }
	                pcs = classifier.getAggregator().process(pcs);

	                OracleResult result = classifier.predict(pcs, fileWriter);
	                // write best matrix yet
	                if (Oracle.LogALL)
	                	log.info("Sending result to JVM " + result);
	                out.write(""+result.matrix); out.newLine(); out.flush();
	            }
            } catch(IOException ex) {
            	ex.printStackTrace();
            } finally {
            	fileWriter.close();
            }
        } catch(Exception e) {
        	e.printStackTrace();            
        } finally {
            try {
                socket.close();
                log.debug("connection to JVM:" + socket.getPort() + " lost");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();
        properties.load(new FileInputStream(PROPERTIES_FILE));
    	
        int port = Integer.parseInt(properties.getProperty(PROP_PORT, PROP_PORT_DEFAULT));
        ServerSocket listener = new ServerSocket(port);
        log.info(String.format("Server running on %s:%d", listener.getInetAddress().getCanonicalHostName(),
                listener.getLocalPort()));
        
        GlobalClassifier classifier = new GlobalClassifier(properties);
        classifier.init();
        
        while(true) {
            new Oracle(classifier, listener.accept()).start(); 
        }
    }

}
