package pt.inescid.gsd.oracle;

import org.apache.log4j.Logger;

import pt.inescid.gsd.oracle.aggregators.Aggregator;
import pt.inescid.gsd.oracle.aggregators.MeanDiffAggregator;
import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class Oracle extends Thread implements IOracle {

    private final static String ATTRIBUTES[] = { "BRANCH_INSTRUCTIONS_RETIRED", "CACHE-REFERENCES", "CYCLES",
            "INSTRUCTION_RETIRED", "INSTRUCTIONS", "LLC_MISSES", "LLC_REFERENCES", "MAJOR-FAULTS", "MINOR-FAULTS",
            "MISPREDICTED_BRANCH_RETIRED", "UNHALTED_CORE_CYCLES", "UNHALTED_REFERENCE_CYCLES", "class" };

    private static final double MIN_CONFIDENCE = 0.9;

    private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";

    private final static String TRAINING_SET_PROP = "trainingSet";

    private static final String TRAINING_SET_FILENAME = "training-set.arff";

    private static final int attributesSize = ATTRIBUTES.length - 1;

	private static final String PORT = "port";

	private static final String DEFAULT_PORT = "9898";


    private static Logger log = Logger.getLogger(Oracle.class);

    private static String trainingSet;

    private static Properties properties;
    
    
    private Classifier classifier;

    private Instances trainingInstances;

    private Instance instance;
    
    private Socket socket;

    private Aggregator aggregator = new MeanDiffAggregator(attributesSize);

    static {
        properties = new Properties();
        try {
            properties.load(new FileInputStream(PROPERTIES_FILE));
        } catch (IOException e) {
            log.warn(String.format("It was not possible to load properties file '%s'.", PROPERTIES_FILE));
        }
        trainingSet = properties.getProperty(TRAINING_SET_PROP, TRAINING_SET_FILENAME);
    }
    
    public Oracle(Socket socket) {
        this.socket = socket;
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
        
        // TODO implement factory class to choose classifier at runtime
        SMO baseClassifier = new SMO();
        // baseClassifier.setC(Math.pow(2, 15)); // consider the adjustment of
        // the complexity model
        baseClassifier.setKernel(new PolyKernel());
        baseClassifier.setBuildLogisticModels(true);

        classifier = (Classifier) baseClassifier;
        classifier.buildClassifier(trainingInstances);
    }
    
    @Override
    public OracleResult predict(double[] pcs, BufferedWriter fileWriter) throws Exception {
        if(pcs == null)
            return new OracleResult(-1, null);

        for (int i = 0; i < pcs.length; i++) {
            instance.setValue(i, pcs[i]);
        }
        double classIndex = classifier.classifyInstance(instance);

        String classStr = instance.classAttribute().value((int) classIndex);
        double[] distribution = classifier.distributionForInstance(instance);
        double confidence = distribution[(int)classIndex];
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

    private String pcsToStr(double[] pcs) {
        StringBuilder sb = new StringBuilder();
        for(double pc : pcs)
            sb.append("," + pc);
        return "{" + sb.toString().substring(1) + "}";
    }
    
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
	            init();
	            while(true) {
	            	String line = in.readLine();
	                if(line == null || !line.contains(","))
	                    continue;
	                log.info(String.format("Received instance to classify: '%s'", line));
	
	                String[] items = line.split(",");
	                
	                if(attributesSize != items.length) {
	                    log.error(String.format("Number of provided pcs: %d (required: %d)", items.length, attributesSize));
	                    return;
	                }
	                double[] pcs = new double[attributesSize];
			        for(int i = 0; i < items.length; i++) {
	                    pcs[i] = Double.parseDouble(items[i]);
	                }
	                pcs = aggregator.process(pcs);
	                OracleResult result = predict(pcs, fileWriter);
	                
	                // write best matrix yet
	                log.info("Sending result to JVM " + result);
	                out.write(""+result.matrix); out.newLine(); out.flush();
	            }
            } catch(IOException ex) {
            	fileWriter.close();
            	throw ex;
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

    public static void main(String[] args) {

        try {
            int port = Integer.parseInt(Oracle.properties.getProperty(PORT, DEFAULT_PORT));
            ServerSocket listener = new ServerSocket(port);
            log.info(String.format("Server running on %s:%d", listener.getInetAddress().getCanonicalHostName(),
                    listener.getLocalPort()));

            while(true) {
                new Oracle(listener.accept()).start(); 
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
