package pt.inescid.gsd.oracle;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Properties;
import java.util.Scanner;

import org.apache.log4j.Logger;

import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class Oracle implements IOracle extends Thread {

    private final static String ATTRIBUTES[] = { "BRANCH_INSTRUCTIONS_RETIRED", "CACHE-REFERENCES", "CYCLES",
            "INSTRUCTION_RETIRED", "INSTRUCTIONS", "LLC_MISSES", "LLC_REFERENCES", "MAJOR-FAULTS", "MINOR-FAULTS",
            "MISPREDICTED_BRANCH_RETIRED", "UNHALTED_CORE_CYCLES", "UNHALTED_REFERENCE_CYCLES", "class" };

    private final static String PROPERTIES_FILE = "jikesrvm-oracle.properties";

    private final static String TRAINING_SET_PROP = "trainingSet";

    private static final String TRAINING_SET_FILENAME = "training-set.arff";

    private static final int attributesSize = ATTRIBUTES.length - 1;

    private Logger log = Logger.getLogger(Oracle.class);

    private String trainingSet;

    private Classifier classifier;

    private Instances trainingInstances;

    private Instance instance;
    
    private Socket socket;

    public Oracle(Socket socket) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(PROPERTIES_FILE));
        } catch (IOException e) {
            log.info("Not possible to load properties file '" + PROPERTIES_FILE + "'.");
        }
        trainingSet = properties.getProperty(TRAINING_SET_PROP, TRAINING_SET_FILENAME);
        this.socket = socket;
    }

    @Override
    public void init() throws Exception {

        // creates a new trained model with a given training set
        DataSource source = new DataSource(trainingSet);
        System.out.println("trainingSet: " + trainingSet);
        trainingInstances = source.getDataSet();
        trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);
        // show training instances
        System.out.println(trainingInstances);

        // init Attribute to be feed with values
        instance = new Instance(ATTRIBUTES.length);
        instance.setDataset(trainingInstances);
        
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

        for (int i = 0; i < pcs.length; i++) {
            instance.setValue(i, pcs[i]);
        }
        double classIndex = classifier.classifyInstance(instance);

        /*
        String classStr = instance.classAttribute().value((int) classIndex);
        System.out.println("result: " + classStr);
        log.debug("Class '" + classStr +"' predicted for pcs " + pcsToStr(pcs));
        */
        return Matrix.values()[(int) classIndex];
    }

    private String pcsToStr(double[] pcs) {
        StringBuilder sb = new StringBuilder();
        for(double pc : pcs)
            sb.append("," + pc);
        return "{" + sb.toString().substring(1) + "}";
    }
    
    public void run() {

        try {
            PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while(true) {
                String line = in.readLine();
                String[] items = line.split(",");
                

                if(attributesSize != items.length) {
                    Log.error("Number of pcs mismatch.");
                    return;
                }
                double[] pcs = new double[attributesSize];
		for(int i = 0; i < items.length; i++) {
                    pcs[i] = Double.parseDouble(items[i]);
                }

            }

        } catch(IOException e) {
            e.printStackTrace();           
        } finally {
            socket.close();
        }
    }

    public static void main(String[] args) {

        int port = 9898;
        Log.info(String.format("Starting server on port %d", port));
        ServerSocket listener = new ServerSocket(port);
        try {
            while(true) {
                new Oracle(listener.accept()).start(); 
            }




            Oracle oracle = new Oracle();

            oracle.init();

            ServerSocket socket = new ServerSocket();
            socket.bind(new InetSocketAddress(InetAddress.getLocalHost(), 5005));
            System.out.println("Listening at " + socket.getLocalSocketAddress());
            Socket client = socket.accept();
            Scanner input = new Scanner(client.getInputStream());
            double[] pcs = new double[ATTRIBUTES.length];
            int i=0;
            while (true) {
            	//System.out.println("Reading values...");
        		String value = input.next();
        		//System.out.println("value = " + value);
        		if (value.equals("*")) {
        			i=0;
                	System.out.println("Calling oracle...");
                	System.out.println("Oracle says " + oracle.predict(pcs));
        			continue;
        		}
        		pcs[i++] = Double.parseDouble(value);
            }
            /*
            double[] pcs = new double[] { 112709888, 6203905, 1022179323, 1042568017, 522040499, 598977, 12401291, 0,
                    13, 3672798, 1273605903, 70 }; // should be classified as M3
            oracle.predict(pcs);
            */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
