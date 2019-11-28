package explorerBot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import ai.core.AI;
import ai.core.ParameterSpecification;
import config.ConfigManager;
import features.QuadrantModelFeatureExtractor;
import metabot.MetaBot;
import rl.Sarsa;
import rts.GameState;
import rts.PlayerAction;
import rts.UnitAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import javafx.util.Pair;
import utils.FileNameUtil;


public class EBot extends AI{
    
    UnitTypeTable myUnitTypeTable = null;

    Logger logger;
    /*
     * Game configuration
     * */
    private Properties config;
    /*
     * Sarsa learning agent
     * */
    private Sarsa learningAgent;
    /*
     * Next possible set of actions for all the units in the map
     * 
     * */
    private ActionEnumerator nextPossibleActions;
    /*
     * Weights for each possible set of actions.
     * */
    private WeightStore exhaustiveActionStore;
    /*
     * List (hashed value) action possible action set
     * */
    private List<Integer> nextActionList;
    /* 
     * Next chosen action from the list. (hashed value)
     * */
    private Integer nextAction;
    private Integer prevAction;
    /* 
     * Pair of unit and its action assigned.
     * */
    private List<Pair<Unit, UnitAction>> unitActions;
    int myPlayerNumber;
    private QuadrantModelFeatureExtractor features;
    // BEGIN -- variables to feed the learning agent
    private GameState previousState;
    private GameState currentState;
    double reward;
    // END-- variables to feed the learning agent
    int quadrantDivision;
    int i=0;
    String debugDir,debugFileName,filePart;
    boolean debug;
    FileOutputStream fos;   
	Writer writer;
	BufferedWriter bufW;   
	final static long fileSize = 250000000;
	int filePartNum,gameCount;
    
    public EBot(UnitTypeTable utt) {
        // calls the other constructor, specifying the default config file
        this(utt, "EBot.properties");
    }
    public EBot(UnitTypeTable utt, Properties EBotConfig) {
        System.gc();//Call garbage collector
        myUnitTypeTable = utt;
        config = EBotConfig;
        gameCount = 1;
        logger = LogManager.getLogger(EBot.class);
        
        quadrantDivision = Integer.parseInt(config.getProperty("rl.feature.extractor.quadrant_division", "3"));
        features = new QuadrantModelFeatureExtractor(quadrantDivision);
        exhaustiveActionStore = new WeightStore(features);
        nextPossibleActions = new ActionEnumerator();
        nextActionList = new ArrayList<Integer>();
         // creates the learning agent with the specified portfolio and loaded parameters
        learningAgent = new Sarsa(config);
        learningAgent.setFeatnWeight(exhaustiveActionStore, features);
        
        if (config.containsKey("rl.load_weights_bin")) {
            try {
            	if(config.getProperty("rl.load_weights_bin").equalsIgnoreCase("True"))
            		loadBin(config.getProperty("rl.bin_input"));
            } catch (IOException e) {
                logger.error("Error while loading weights from " + config.getProperty("rl.input"), e);
                logger.error("Weights initialized randomly.");
                e.printStackTrace();
            }
        }
        debug = false;
        if (config.containsKey("rl.debug")) {
            if (config.getProperty("rl.debug").equalsIgnoreCase("True")) {
            	debug = true;
                debugDir = config.getProperty("rl.debugdir","debug/");
                if (debugDir.charAt(debugDir.length()-1) != '/') {
                	debugDir = debugDir + "/";
                }
                debugFileName = "EBot_debug_data_Time-" + System.currentTimeMillis();
                new File(debugDir).mkdirs();
                filePartNum = 0;                
                filePart = "_Part_" + filePartNum;
                try {
					openDebugFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
            }
        }
        if(debug)System.out.println("Warning!!!! - Running in Debug mode. Debug data stored in debug/");
        reset();
    }


    public EBot(UnitTypeTable utt, String configPath){
        System.gc();//Call garbage collector

        myUnitTypeTable = utt;

        logger = LogManager.getLogger(EBot.class);

        try {
            config = ConfigManager.loadConfig(configPath);
        } catch (IOException e) {
            logger.error("Error while loading configuration from '" + configPath+ "'. Using defaults.", e);

        }
        
        quadrantDivision = Integer.parseInt(config.getProperty("rl.feature.extractor.quadrant_division", "3"));
        features = new QuadrantModelFeatureExtractor(quadrantDivision);
        exhaustiveActionStore = new WeightStore(features);
        nextPossibleActions = new ActionEnumerator();
        nextActionList = new ArrayList<Integer>();
         // creates the learning agent with the specified portfolio and loaded parameters
        learningAgent = new Sarsa(config);

        if (config.containsKey("rl.bin_input")) {
            try {
                loadBin(config.getProperty("rl.bin_input"));
            } catch (IOException e) {
                logger.error("Error while loading weights from " + config.getProperty("rl.input"), e);
                logger.error("Weights initialized randomly.");
                e.printStackTrace();
            }
        }
        debug = false;
        if (config.containsKey("rl.debug")) {
            if (config.getProperty("rl.debug").equalsIgnoreCase("True")) {
            	debug = true;
                debugDir = config.getProperty("rl.debugdir","debug/");
                if (debugDir.charAt(debugDir.length()-1) != '/') {
                	debugDir = debugDir + "/";
                }
                debugFileName = "EBot_debug_data_Time-" + System.currentTimeMillis();
                new File(debugDir).mkdirs();
                filePartNum = 0;                
                filePart = "_Part_" + filePartNum;
                try {
					openDebugFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
            }
        }
        reset();
    }
    
    public PlayerAction getAction(int player, GameState state) {
    	
        // sets to a valid number on the first call
        if(myPlayerNumber == -1){
            myPlayerNumber = player;
        }

        // verifies if the number I set previously holds
        if(myPlayerNumber != player){
            throw new RuntimeException(
                "Called with wrong player number " + player + ". Was expecting: " + myPlayerNumber
            );
        }
        
        // setup the action enumerator with current state and weights.
        nextPossibleActions.set(state, exhaustiveActionStore, player);        
        // call action enumerator to generate actions
        if(debug)nextPossibleActions.setDebugStatus(true,bufW);
        nextPossibleActions.generateNextActions();
    //    System.out.println("Generated Actions..");
    //    nextPossibleActions.printGeneratedActions();
        if(debug)
			try {saveDebugData("Actions");
			} catch (IOException e1) {e1.printStackTrace();}
        // Fetch the list of possible set of actions for every unit in the map
        nextActionList.clear();
        nextActionList = nextPossibleActions.getNextActionsHash().stream().collect(Collectors.toList());
        // makes the learning agent learn
        previousState = currentState;
        currentState = state;
        prevAction = nextAction;
        reward = 0;
        if (state.gameover()){
            if(state.winner() == player) reward = 1;
            if(state.winner() == 1-player) reward = -1;
            else reward = 0;
        }
        
        if(debug)learningAgent.setDebugStatus(true,bufW);
        //Pass the states, reward, next possible set of actions and weights to learning agent
        learningAgent.learn(prevAction,previousState, reward, currentState, currentState.gameover(),
        		player,nextActionList);
        try {
        		nextAction = learningAgent.act(state, player,nextActionList);
        		if(debug)saveDebugData("SelectedAction");//saving selected action
        	//	System.out.println("Issuing Action.." + nextAction);
            //    nextPossibleActions.printGeneratedActions();
        		unitActions = nextPossibleActions.getNextUnitActions(nextAction).stream().collect(Collectors.toList());
        		PlayerAction pa = new PlayerAction();
        		for(Pair<Unit, UnitAction> acts:unitActions){
        			pa.addUnitAction(acts.getKey(), acts.getValue());
        		}
        		return pa;
        		//
        } catch (Exception e) {
      //      logger.error("Exception while getting action in frame #" + state.getTime() + " from " + choice.getClass().getSimpleName(), e);
            logger.error("Defaulting to empty action");
            e.printStackTrace();

            PlayerAction pa = new PlayerAction();
            pa.fillWithNones(state, player, 1);
            return pa;
        }
        
        
    }

    public void gameOver(int winner) throws Exception {
        if (winner == -1) reward = 0; //game not finished (timeout) or draw
        else if (winner == myPlayerNumber) reward = 1; //I won
        else reward = -1; //I lost

        learningAgent.learn(prevAction,previousState, reward, currentState, currentState.gameover(),
        		myPlayerNumber,nextActionList);
   
        System.gc();//Call garbage collector
        if (config.containsKey("rl.save_weights_bin")) {
            if (config.getProperty("rl.save_weights_bin").equalsIgnoreCase("True")) {
                String dir = config.getProperty("rl.output.binprefix","training/weightstore/forinput");
                if(Files.notExists(Paths.get(dir))) new File(dir).mkdirs();
                if (dir.charAt(dir.length()-1) != '/') {
                    dir = dir + "/";
                }
             //  saveBin(dir + "weightstore_" + myPlayerNumber + ".bin");
               saveBin(dir + "weightstore" + "-game-" + gameCount++ + ".bin");
              
            }
        }

        if (config.containsKey("rl.debug")) {
            if (config.getProperty("rl.debug").equalsIgnoreCase("True")) {
            	closeDebugFile();
            }
        }
    }

    @SuppressWarnings("unchecked")
	private void loadBin(String path) throws IOException{
    	if(Files.notExists(Paths.get(path))){
    		logger.error("Error while attempting to load weights,file Path does not exist.");
    		logger.error("Continuing with default weights..");           
    	}
    	else{
    	FileInputStream fis = new FileInputStream(path);
    	
        ObjectInputStream ois = new ObjectInputStream(fis);
        try {
        	exhaustiveActionStore.loadWeightsUser((Map<Integer, Map<String, Float>>) ois.readObject());
		} catch (ClassNotFoundException e) {
			System.err.println("Error while attempting to load weights.");
			e.printStackTrace();
		}
        ois.close();
        fis.close();
    	}
    	
    }
    private void saveBin(String path) throws IOException{
    	if(exhaustiveActionStore == null){
    		throw new RuntimeException("Attempted to save non-initialized weights");
    	}    	
    	FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(exhaustiveActionStore.getUnitActionWeightsAll());
        oos.close();
        fos.close();
    	
    }
    
    private void openDebugFile() throws IOException{
    	fos = new FileOutputStream(debugDir + debugFileName + filePart + ".txt");    
    	writer = new OutputStreamWriter(fos,"UTF-8");    	
    	bufW =new BufferedWriter(writer,25000000);        

    }
    private void closeDebugFile() throws IOException{
    	bufW.flush();
    	bufW.close();
    	writer.close();
    	fos.close();
    }
    private void checkAndCreateSubFile(){
    	try {
			if(Files.size(Paths.get(debugDir + debugFileName + filePart+  ".txt")) > fileSize  ){
				closeDebugFile();
				filePartNum += 1;
				filePart = "_Part_" + filePartNum;
				openDebugFile();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    private void saveDebugData(String content) throws IOException{
    	
    	Map<Integer, List<Pair<Unit, UnitAction>>> nxtactions;
    	Map<Integer, Map<String, Float>> weights;
    	checkAndCreateSubFile();
    	if(content == "Actions") {
    		bufW.write("========================================================================================================================================");bufW.newLine();
    		bufW.write("Learning Rate - " + learningAgent.getAlpha() + " Epsilon - " + learningAgent.getEpsilon()); bufW.newLine();
    		bufW.write("========================================================================================================================================");bufW.newLine();
    		bufW.write("Generated Actions..."); bufW.newLine();
    		bufW.write("========================================================================================================================================");bufW.newLine();
    		nxtactions = nextPossibleActions.getNextPossibleUnitActions();
    		for(Integer key:nxtactions.keySet() ){
    			bufW.write("Key " + key + " Value " + nxtactions.get(key));
    			bufW.newLine();
    		}
    	}
    	else if(content == "SelectedAction"){
    		bufW.write("========================================================================================================================================");bufW.newLine();
    		bufW.write("Issuing Action - " + nextAction);bufW.newLine();    		
    		bufW.write("========================================================================================================================================");bufW.newLine();
//    		bufW.write("Revised learning Rate - " + learningAgent.getAlpha() + " Epsilon - " + learningAgent.getEpsilon());bufW.newLine();
//    		weights = exhaustiveActionStore.getUnitActionWeightsAll();
//    		bufW.write("========================================================================================================================================");bufW.newLine();
    		bufW.write("Updated Weights for Key " + nextAction);bufW.newLine();
    		bufW.write("========================================================================================================================================");bufW.newLine();
    		for(String feat: exhaustiveActionStore.getUnitActionWeightl(nextAction).keySet()){
    			bufW.write("Feature " + feat + ", Weight - " + exhaustiveActionStore.getUnitActionWeightl(nextAction).get(feat));
    			bufW.newLine();}    		
    		
       /* 	for(Integer key : weights.keySet()){
        		bufW.write("========================================================================================================================================");bufW.newLine();
        		bufW.write("Feature Weight for Key " + key);bufW.newLine();
        		bufW.write("========================================================================================================================================");bufW.newLine();
        		for(String feat: weights.get(key).keySet()){
        			bufW.write("Feature " + feat + ", Weight - " + weights.get(key).get(feat));
        			bufW.newLine();
        		}
        	}*/

    	}
    }

	@Override
	public List<ParameterSpecification> getParameters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public AI clone() {
		// TODO Auto-generated method stub
		return null;
	}

}
