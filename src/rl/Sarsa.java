package rl;

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
    private Integer nextChoice;
    private Integer choiceNameHash;
  //  private QuadrantModelFeatureExtractor features;
    
	public Sarsa( Properties config) {
		
        
        epsilon = Double.parseDouble(config.getProperty("rl.epsilon.initial", "0.1"));
        epsilonDecayRate = Double.parseDouble(config.getProperty("rl.epsilon.decay", "1.0"));
        
        alpha = Double.parseDouble(config.getProperty("rl.alpha.initial", "0.1"));
        alphaDecayRate = Double.parseDouble(config.getProperty("rl.alpha.decay", "1.0"));
        
        gamma = Double.parseDouble(config.getProperty("rl.gamma", "0.9"));
        
        lambda = Double.parseDouble(config.getProperty("rl.lambda", "0.0"));
        random = new Random();
        
	}
	   /**
     * Returns the AI for the given state and player. 
     * @param state
     * @param player
     * @return
     */
    public Integer act(GameState state, int player,List<Integer> nextActions,WeightStore weights,QuadrantModelFeatureExtractor features){//change return type
    	//nextChoice is null on the first call to this function, afterwards, it is determined as a side-effect of 'learn'
    	if(choiceNameHash == null){
    		choiceNameHash = epsilonGreedy(state, player,nextActions,weights,features);
    	}
    	
        return choiceNameHash;
    	
    }
 
    /**
     * Returns an action using epsilon-greedy for the given state
     * (i.e., a random action with probability epsilon, and the greedy action otherwise)
     * @param state
     * @param player
     * @return
     */
    private Integer epsilonGreedy(GameState state, int player,List<Integer> nextActions,WeightStore weights,QuadrantModelFeatureExtractor features){//change return
        // epsilon-greedy:
       if(random.nextFloat() < epsilon){ //random choice
    	   choiceNameHash = nextActions.get(random.nextInt(nextActions.size()));
        	if(choiceNameHash==null){System.err.println("ERROR!!!");}
        }
        else { //greedy choice
        	double maxQ = Double.NEGATIVE_INFINITY; //because MIN_VALUE is positive =/
        	
        	for(Integer actionHash: nextActions){
        		double q = qValue(features.getRawFeatures(state, player), weights.getUnitActionWeights(actionHash));
        		if (q > maxQ){
        			maxQ = q;
        			choiceNameHash = actionHash;
        		}
        	}
        	if(choiceNameHash==null){System.err.println("***ERROR!!!");}
        }
                
        return choiceNameHash;
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
    public void learn(GameState state, double reward, GameState nextState, boolean done, int player,
    		List<Integer> nextActions,WeightStore weights,QuadrantModelFeatureExtractor features){
        //GameState state, int player,List<Integer> nextActions,WeightStore weights
    	// ensures all variables are valid (they won't be in the initial state)
    	if (state == null || nextState == null || nextChoice == null) {
    		return; 
    	}
    	
    	// determines the next choice
    	nextChoice = epsilonGreedy(nextState, player,nextActions,weights,features);
    	// applies the update rule with s, a, r, s', a'
        sarsaLearning(
    		state, choiceNameHash, reward, 
    		nextState, nextChoice, player,weights,features
    	);
        
        
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
    		GameState nextState, Integer nextChoice, int player,WeightStore weights,QuadrantModelFeatureExtractor features){
    	// checks if s' and a' are ok (s and a will always be ok, we hope)
    	if(nextState == null || nextChoice == null) return;
    	
    	Map<String, Feature> stateFeatures = features.getRawFeatures(state, player);
    	Map<String, Feature> nextStateFeatures = features.getRawFeatures(nextState, player);
    	//qValue(features.getRawFeatures(state, player), weights.getUnitActionWeights(actionHash));

    	double q = qValue(stateFeatures, weights.getUnitActionWeights(choice));
    	double futureQ = qValue(nextStateFeatures, weights.getUnitActionWeights(nextChoice));
    	
    	//the temporal-difference error (delta in Sarsa equation)
    	double delta = reward + gamma * futureQ - q;
    	
    	for(String featureName : stateFeatures.keySet()){
    		//retrieves the weight value, updates it and stores the updated value
    		float weightValue = weights.getUnitActionWeights(choice).get(featureName);
    		weightValue += alpha * delta * stateFeatures.get(featureName).getValue();
    		System.out.println(nextChoice + " " + featureName + " " + weightValue);
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
//    	return dotProduct(features, weights.get(choice));
    	return 0;
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
    

}
