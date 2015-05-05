package pt.inescid.gsd.oracle;

import org.apache.log4j.Logger;

import pt.inescid.gsd.oracle.aggregators.Aggregator;
import pt.inescid.gsd.oracle.aggregators.MeanDiffAggregator;
import pt.inescid.gsd.oracle.pcoutputs.PCFileOutput;
import pt.inescid.gsd.oracle.pcoutputs.PCOutput;
import pt.inescid.gsd.oracle.pcoutputs.PCVoidOupput;
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

//    private static final double MIN_CONFIDENCE = 0.9;

	private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";
    private static final String PROP_PORT = "port";
    private static final String PROP_PORT_DEFAULT = "5005";

	private Socket socket;
	private GlobalClassifier classifier;
	private PCOutput outputPC;
	
	public static final boolean LogALL = false;
    public static Logger log = Logger.getLogger(Oracle.class);

    
    public Oracle(PCOutput outputPC, GlobalClassifier classifier, Socket socket) {
    	this.outputPC = outputPC;
        this.classifier = classifier;
    	this.socket = socket;
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
            
            outputPC.start(appName+"-AGGR-PCs.csv");
            classifier.getAggregator().start();
            
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

	                if (pcs!=null) {
	                	outputPC.log(pcs);
	                }
	                
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
            	outputPC.stop();
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
        
        PCOutput output;
        boolean outputPC = Boolean.parseBoolean(properties.getProperty("outputPCs", "false"));
		if (outputPC) {
			log.info("Dumping PC to file");
        	output = new PCFileOutput();       	
        } else {
        	log.info("Not dumping PCs");
        	output = new PCVoidOupput();
        }

		while(true) {
            new Oracle(output, classifier, listener.accept()).start(); 
        }
    }

}
