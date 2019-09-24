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
    	for (String featureName : featureExtractor.getFeatureNames(state))
    		featureWeights.put(featureName, random.nextFloat()*2 - 1);
    	
    	unitActionWeights.put(unitActionHash, featureWeights);
     }
    
    public boolean checkUnitActionWeights(int unitActionHash){
    	return unitActionWeights.containsKey(unitActionHash);
    }
    
    public void setUnitActionWeights(int unitActionHash,Map<String,Float> featureWeights ){
    	unitActionWeights.put(unitActionHash, featureWeights);
    }
    
    public Map<String,Float> getUnitActionWeights(int unitActionHash){
    	return unitActionWeights.get(unitActionHash);
    }
    public Map<Integer, Map<String, Float>> getUnitActionWeightsAll(){
    	return unitActionWeights;
    }

}
