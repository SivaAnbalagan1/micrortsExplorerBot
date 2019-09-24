package explorerBot;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import util.Pair;
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

    public EBot(UnitTypeTable utt) {
        // calls the other constructor, specifying the default config file
        this(utt, "EBot.properties");
    }

    public EBot(UnitTypeTable utt, Properties EBotConfig) {
        myUnitTypeTable = utt;
        config = EBotConfig;

        logger = LogManager.getLogger(EBot.class);
        
        quadrantDivision = Integer.parseInt(config.getProperty("rl.feature.extractor.quadrant_division", "3"));
        features = new QuadrantModelFeatureExtractor(quadrantDivision);
        exhaustiveActionStore = new WeightStore(features);
        nextPossibleActions = new ActionEnumerator();
        
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
        reset();
    }

    public EBot(UnitTypeTable utt, String configPath){
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
        nextPossibleActions.generateNextActions();
        // Fetch the list of possible set of actions for every unit in the map
        nextActionList = nextPossibleActions.getNextActionsHash(); 
         
        // makes the learning agent learn
        previousState = currentState;
        currentState = state;
        reward = 0;
        if (state.gameover()){
            if(state.winner() == player) reward = 1;
            if(state.winner() == 1-player) reward = -1;
            else reward = 0;
        }
         
        //Pass the states, reward, next possible set of actions and weights to learning agent
        learningAgent.learn(previousState, reward, currentState, currentState.gameover(),
        		player,nextActionList,exhaustiveActionStore,features);
        
        try {
        		nextAction = learningAgent.act(state, player,nextActionList,exhaustiveActionStore,features);
        		System.out.println(nextAction);
        		unitActions = nextPossibleActions.getNextUnitActions(nextAction);
        		PlayerAction pa = new PlayerAction();
        		unitActions.forEach((unitActionPair)->{
        			pa.addUnitAction(unitActionPair.m_a, unitActionPair.m_b);
        		});
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

        learningAgent.learn(previousState, reward, currentState, currentState.gameover(),
        		myPlayerNumber,nextActionList,exhaustiveActionStore,features);
   

        if (config.containsKey("rl.save_weights_bin")) {
            if (config.getProperty("rl.save_weights_bin").equalsIgnoreCase("True")) {
                String dir = config.getProperty("rl.workingdir","weights/");
                if (dir.charAt(dir.length()-1) != '/') {
                    dir = dir + "/";
                }
               saveBin(dir + "weights_" + myPlayerNumber + ".bin");
            }
        }

        if (config.containsKey("rl.save_weights_human")) {
            if (config.getProperty("rl.save_weights_human").equalsIgnoreCase("True")) {
                String dir = config.getProperty("rl.workingdir","weights/");
                if (dir.charAt(dir.length()-1) != '/') {
                    dir = dir + "/";
                }
                saveHuman(dir + "weights_" + myPlayerNumber);
            }
        }
    }

    private void loadBin(String path) throws IOException{
    	
    	FileInputStream fis = new FileInputStream(path);
        ObjectInputStream ois = new ObjectInputStream(fis);
        try {
        	exhaustiveActionStore = (WeightStore) ois.readObject();
		} catch (ClassNotFoundException e) {
			System.err.println("Error while attempting to load weights.");
			e.printStackTrace();
		}
        ois.close();
        fis.close();

    	
    }
    private void saveBin(String path) throws IOException{
    	if(exhaustiveActionStore == null){
    		throw new RuntimeException("Attempted to save non-initialized weights");
    	}
    	
    	FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(exhaustiveActionStore);
        oos.close();
        fos.close();
    	
    }
    
    private void saveHuman(String path) throws IOException{
    	Gson gson = new Gson();
    	
    	FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(gson.toJsonTree(exhaustiveActionStore.getUnitActionWeightsAll()));
        oos.close();
        fos.close();
    	
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
