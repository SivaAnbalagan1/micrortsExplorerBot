package explorerBot;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import features.FeatureExtractor;
import features.QuadrantModelFeatureExtractor;
import rts.GameState;

public class WeightStore {

	private QuadrantModelFeatureExtractor featureExtractor;
    
    private Map<Integer, Map<String, Float>> unitActionWeights; //Map<NextActionHash, Map<feature name, weight>
    private Map<String,Float> featureWeights;// Map<feature name, weight>    
    Random random;
    
	WeightStore(QuadrantModelFeatureExtractor featureExtractor ){
		unitActionWeights = new HashMap<>();
		featureWeights = new HashMap<>();
		this.featureExtractor = featureExtractor; 
		random = new Random();
	}

	public void addInitialWeights(GameState state,int unitActionHash){
    	// weights are initialized randomly within [-1, 1]
		featureWeights = new HashMap<>();
    	for (String featureName : featureExtractor.getFeatureNames(state))
    		featureWeights.put(featureName, random.nextFloat());
    	
    	unitActionWeights.put(unitActionHash, featureWeights);
 //   	System.out.println("Adding" + unitActionHash);
     }
    
    public boolean checkUnitActionWeights(int unitActionHash){
    	return unitActionWeights.containsKey(unitActionHash);
    }
    
    public void setUnitActionWeights(int unitActionHash,Map<String,Float> featureWeights ){
    	unitActionWeights.put(unitActionHash, featureWeights);
    }

    public void loadWeightsUser(Map<Integer, Map<String, Float>> weightsUser ){    	
    	unitActionWeights = weightsUser;
    }

    public Map<String,Float> getUnitActionWeights(int unitActionHash){
    	return unitActionWeights.get(unitActionHash);
    }
    public Map<Integer, Map<String, Float>> getUnitActionWeightsAll(){
    	return unitActionWeights;
    }
    public Map<String, Float> getUnitActionWeightl(Integer key){
    	return unitActionWeights.get(key);
    }
    
    public void printActionWeightsAll(){
    	System.out.println("--------------------------------------------------------------------------------");
    	for(Integer key : unitActionWeights.keySet()){
    		System.out.println("Weights " + "Key " + key);
    		for(String feat: unitActionWeights.get(key).keySet()){
    			System.out.println("Feature " + feat + ", Value - " + unitActionWeights.get(key).get(feat));
    		}
    	}
    	System.out.println("--------------------------------------------------------------------------------");
    }
    
    public void printActionWeights(Integer key){
    	System.out.println("--------------------------------------------------------------------------------");
    	for(String feat: unitActionWeights.get(key).keySet()){
    			System.out.println("Feature " + feat + ", Value - " + unitActionWeights.get(key).get(feat));
    	}
    	System.out.println("--------------------------------------------------------------------------------");
    }
    
}
