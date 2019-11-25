package rl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import ai.core.AI;
import explorerBot.WeightStore;
import features.Feature;
import features.QuadrantModelFeatureExtractor;
import rts.GameState;
import rts.PlayerAction;
import rts.units.Unit;

public class Sarsa {
	/**
     * Random number generator
     */
    Random random;
    
    /**
     * Probability of exploration
     */
    private double epsilon;
    
    /**
     * Decay rate of epsilon
     */
    private double epsilonDecayRate;
    
    /**
     * Learning rate
     */
    private double alpha;
    
    /**
     * Decay rate of alpha
     */
    private double alphaDecayRate;
    
    /**
     * Discount factor
     */
    private double gamma;
    
    /**
     * Eligibility trace
     */
    private double lambda;
    private Integer prevChoice;
    private Integer nextChoice;

	private boolean debugStatus;
	private BufferedWriter debugFile;
	private WeightStore weights;
    private QuadrantModelFeatureExtractor features;


    
	public Sarsa( Properties config) {
		
        epsilon = Double.parseDouble(config.getProperty("rl.epsilon.initial", "0.1"));
        epsilonDecayRate = Double.parseDouble(config.getProperty("rl.epsilon.decay", "1.0"));
        
        alpha = Double.parseDouble(config.getProperty("rl.alpha.initial", "0.1"));
        alphaDecayRate = Double.parseDouble(config.getProperty("rl.alpha.decay", "1.0"));
        
        gamma = Double.parseDouble(config.getProperty("rl.gamma", "0.9"));
        
        lambda = Double.parseDouble(config.getProperty("rl.lambda", "0.0"));
        random = new Random();
        
        
	}
	public void setFeatnWeight(WeightStore weights,QuadrantModelFeatureExtractor features){
		this.features = features;
		this.weights = weights;
		
	}
	   /**
     * Returns the AI for the given state and player. 
     * @param state
     * @param player
     * @return
     */
    public Integer act(GameState state, int player,List<Integer> nextActions){//change return type
    	//nextChoice is null on the first call to this function, afterwards, it is determined as a side-effect of 'learn'
    	
    	if(nextChoice == null){
    		nextChoice = epsilonGreedy(state, player,nextActions);
    	}
    //	System.out.println("act" + choiceNameHash);
        return nextChoice;
    	
    }
 
    /**
     * Returns an action using epsilon-greedy for the given state
     * (i.e., a random action with probability epsilon, and the greedy action otherwise)
     * @param state
     * @param player
     * @return
     */
    private Integer epsilonGreedy(GameState state, int player,List<Integer> nextActions){//change return
        // epsilon-greedy:
    	float rand = random.nextFloat();
       if( rand < epsilon){ //random choice
    	   
    	   for(Integer actionHash: nextActions){
    		   nextChoice = actionHash;
    		   if (random.nextBoolean() == true)break; 
    	   }
        	if(nextChoice==null){System.err.println("ERROR!!!");}
        	if(debugStatus)
    			try {saveDebugInfo("Random");
    			} catch (IOException e) {e.printStackTrace();}

        }
        else { //greedy choice
        	double maxQ = Double.NEGATIVE_INFINITY; //because MIN_VALUE is positive =/
        	
        	for(Integer actionHash: nextActions){
        		double q = qValue(features.getFeatures(state, player), weights.getUnitActionWeights(actionHash));
            	if(debugStatus)
        			try {saveDebugInfo(q,actionHash);
        			} catch (IOException e) {e.printStackTrace();}

        		if (q > maxQ){
        			maxQ = q;
        			nextChoice =  actionHash;
        		}
        	}
        	if(nextChoice==null){System.err.println("***ERROR!!!");}
        	if(debugStatus)
    			try {saveDebugInfo("Highest Q");
    			} catch (IOException e) {e.printStackTrace();}

        }
        return nextChoice;
    }
    
    /**
     * Receives an experience tuple (s, a, r, s') and updates the action-value function
     * As a side effect of Sarsa, the next action a' is chosen here.
     * @param state s
     * @param choice a
     * @param reward r
     * @param nextState s'
     * @param done whether this is the end of the episode
     * @param player required to extract the features of this state
     */
    
    public void learn(Integer prevChoice,GameState state, double reward, GameState nextState, boolean done, int player,
    		List<Integer> nextActions){
        //GameState state, int player,List<Integer> nextActions,WeightStore weights
    	// ensures all variables are valid (they won't be in the initial state)
    	if (state == null || nextState == null) {
    		return; 
    	}
    	
    	// determines the next choice
    	
    	nextChoice = epsilonGreedy(nextState, player,nextActions);
    	// applies the update rule with s, a, r, s', a'
        sarsaLearning(
    		state, prevChoice, reward, 
    		nextState, nextChoice, player
    	);
        //System.out.println("learn-" + choiceNameHash);
        
        if (done){
        	//decays alpha and epsilon
        	alpha *= alphaDecayRate;
        	epsilon *= epsilonDecayRate;
        	
        }
        
    }
    
    /**
     * Updates the weight vector of the current action (choice) using the Sarsa rule:
     * delta = r + gamma * Q(s',a') - Q(s,a)
     * w_i <- w_i + alpha*delta*f_i (where w_i is the i-th weight and f_i the i-th feature)
     * 
     * @param state s in Sarsa equation
     * @param choice a in Sarsa equation
     * @param reward r in Sarsa equation
     * @param nextState s' in Sarsa equation
     * @param nextChoice a' in Sarsa equation
     * @param player required to extract the features for the states
     */
    private void sarsaLearning(GameState state, Integer choice, double reward, 
    		GameState nextState, Integer nextChoice, int player){
    	// checks if s' and a' are ok (s and a will always be ok, we hope)
    	if(nextState == null || nextChoice == null) return;
    	
    	Map<String, Feature> stateFeatures = features.getFeatures(state, player);
    	Map<String, Feature> nextStateFeatures = features.getFeatures(nextState, player);
    	//qValue(features.getRawFeatures(state, player), weights.getUnitActionWeights(actionHash));

    	double q = qValue(stateFeatures, weights.getUnitActionWeights(choice));
    	double futureQ = qValue(nextStateFeatures, weights.getUnitActionWeights(nextChoice));

    	//the temporal-difference error (delta in Sarsa equation)
    	double delta = reward + gamma * futureQ - q;
    /*	if(debugStatus)
			try {saveDebugInfo(delta,reward,futureQ,q,weights.getUnitActionWeightl(nextChoice));
			} catch (IOException e) {e.printStackTrace();}
    	*/
    	for(String featureName : stateFeatures.keySet()){
    		//retrieves the weight value, updates it and stores the updated value
    		float weightValue = weights.getUnitActionWeights(choice).get(featureName);
    		weightValue += alpha * delta * stateFeatures.get(featureName).getValue();    		
    		weights.getUnitActionWeights(choice).put(featureName, weightValue);
    	}
    }
    
    /**
     * Returns the dot product of features and their respective weights 
     * @param features
     * @param weights
     * @return
     */
    private double qValue(Map<String,Feature> features, Map<String, Float> weights){
    	float product = 0.0f;
    	
    	for(String featureName : features.keySet()){
   /*     	if(debugStatus)
    			try {saveDebugInfo(featureName,features.get(featureName).getValue(),weights.get(featureName));
    			} catch (IOException e) {e.printStackTrace();}
*/
    	//	System.out.println("Feature name " + featureName + " value " + features.get(featureName).getValue() + " Weight " +weights.get(featureName));
    		product += features.get(featureName).getValue() * weights.get(featureName);
    	}
    	return product;
    }
    
    /**
     * Returns the Q-value of a choice (action), for a given set of features
     * @param features
     * @param choice
     * @return
     */
    private double qValue(Map<String,Feature> features, Integer choice){
    	return qValue(features, weights.getUnitActionWeightl(choice));
    }
    
    /**
     * Returns the Q-value of a state (including player information) and choice (action)
     * @param state
     * @param player
     * @param choice
     * @return
     */
    private double qValue(GameState state, int player, String choice){
    	//return qValue(featureExtractor.getFeatures(state, player), choice);
    	return 0;
    }
    public double getAlpha(){
    	return alpha;
    }
    public double getAlphaDecay(){
    	return alphaDecayRate;
    }
    public double getEpsilon(){
    	return epsilon;
    }
    public double getAEpsilonDecay(){
    	return epsilonDecayRate;
    }    
    public void setDebugStatus(boolean status, BufferedWriter bufw){
    	this.debugStatus = status;
    	this.debugFile = bufw;
    }
    public void saveDebugInfo(double delta,double reward,double futureQ,double q, Map<String,Float> weights) throws IOException{
    	
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	debugFile.write("Delta - " + delta + " Reward " + reward + " Gamma " + gamma + " Future Q " + futureQ + " Q " + q) ; debugFile.newLine();
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	debugFile.write("Weights gefore update for next action - " + nextChoice) ; debugFile.newLine();
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	for(String feat: weights.keySet()){
			debugFile.write("Feature " + feat + ", Weight - " + weights.get(feat));
			debugFile.newLine();
		}        	
    }
    public void saveDebugInfo(String actionChoice) throws IOException{
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	debugFile.write("Picked " + actionChoice + " action" + nextChoice) ; debugFile.newLine();
    }
    public void saveDebugInfo( double q,int actionHash) throws IOException{
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	debugFile.write("Q-Value " + q + " Action " + actionHash) ; debugFile.newLine();
    }
    public void saveDebugInfo( String featureName,double featureValue, double weightValue) throws IOException{
    	debugFile.write("------------------------------------------------------------------------------------------------------------------------------------------");debugFile.newLine();
    	debugFile.write("Feature " + featureName + " Value " + featureValue 
    			+ " Weight " + weightValue);  debugFile.newLine();  }

	
}
