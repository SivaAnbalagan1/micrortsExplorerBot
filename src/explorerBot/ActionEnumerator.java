package explorerBot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import rts.GameState;
import rts.UnitAction;
import rts.units.Unit;
import javafx.util.Pair;//serializable

public class ActionEnumerator {
	ActionEnumerator(){
		unitActionList = new ArrayList<>();
		tempUnitActionList = new ArrayList<>();
		test = new ArrayList<>();
		waitUnitActionList =  new ArrayList<>();
		nextActions = new ArrayList<>();
		nextPossibleUnitActions = new HashMap(); 

	}
	
	private GameState gs;
    private Unit [] terrain;
    private int player;
    private List<UnitAction> unitActions;
    private WeightStore weights;
    List<Unit> availableUnits;
    private List<Integer> nextActions;
    /* next action names of all units
     * */
    private String[] nextUnitsActionNameList; 
    private String[] tempnextUnitsActionNameList;
    /* Unit Action name list - wait issued to all units
     * */
    private String[] waitnextUnitsActionNameList;
    /* Unit and Action pair
     * */
    private List<Pair<Unit, UnitAction>> unitActionList;
    private List<Pair<Unit, UnitAction>> tempUnitActionList;
    private List<Pair<Unit, UnitAction>> test;    
    private List<Pair<Unit, UnitAction>> waitUnitActionList;    
    private Iterator<Pair<Unit, UnitAction>> iter;
    private Pair<Unit,UnitAction> tempUnitActionPair;
    /* List of unit and corresponding action pair mapping to a given state
     * State (integer hash value) of the array of next action names of all units 
     * */
    private Map<Integer,List<Pair<Unit, UnitAction>>> nextPossibleUnitActions;
    private UnitAction unitAction;
    int unitActionRef;
	private boolean debugStatus;
	private BufferedWriter debugFile;
    
    //private hashList
	
	public void set(GameState gs,WeightStore weights,int player){
		this.player = player;
		this.gs = gs;		
		this.terrain = new Unit[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.nextUnitsActionNameList = new String[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.tempnextUnitsActionNameList = new String[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.waitnextUnitsActionNameList = new String[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];		
		this.weights = weights;
	}

	/*
	 * Create new state by changing 1 action of any of the available unit. 
	 * */
	public void generateNextActions(){
		availableUnits = gs.getUnits().stream()
				           .filter(unit-> unit.getPlayer() == player)
				           .collect(Collectors.toList());
	
		unitActionList.clear();
		waitUnitActionList.clear();
		nextPossibleUnitActions.clear();		
		nextActions.clear();
		
		Arrays.fill(nextUnitsActionNameList," ");
		Arrays.fill(waitnextUnitsActionNameList," ");
		//State - No action to any units.
		availableUnits.forEach((unit)-> {
						if(!unit.getType().isResource){
						  terrain[unit.getX()*unit.getY()] = unit;
						  unitActions = unit.getUnitActions(gs,0);
						  unitAction = unitActions.stream()
								  		.filter(unitAction -> unitAction.getActionName().equals("wait"))
								  		.findFirst().get();
						  unitActionList.add(new Pair<Unit, UnitAction>(unit,unitAction));
						  waitUnitActionList.add(new Pair<Unit, UnitAction>(unit,unitAction));
						  nextUnitsActionNameList[unit.getX()*unit.getY()] =  unit.toString()+unitAction.toString() ;
						  waitnextUnitsActionNameList[unit.getX()*unit.getY()] =  unit.toString()+unitAction.toString();
						}
					  });
		unitActionRef = Arrays.deepHashCode(Arrays.copyOf(nextUnitsActionNameList,nextUnitsActionNameList.length));	
		nextActions.add(unitActionRef);
		nextPossibleUnitActions.put(unitActionRef, unitActionList.stream().collect(Collectors.toList()));
		if(!weights.checkUnitActionWeights(unitActionRef)){
			weights.addInitialWeights(gs, unitActionRef);
		/*	try {
				if(debugStatus)saveDebugInfo(weights.getUnitActionWeightl(unitActionRef),unitActionRef);
			} catch (IOException e) {e.printStackTrace();}*/
		}
		
		/*create other states
		 * */
		// adding all possible actions		
		waitUnitActionList.forEach((unitActionPair)->{
			unitActionPair.getKey().getUnitActions(gs,0).forEach((action)->{
				 if(!action.getActionName().equals("wait")){
					 if(gs.isUnitActionAllowed(unitActionPair.getKey(), action)){
					  //copy the wait events for all unit
					  tempnextUnitsActionNameList = Arrays.copyOf(waitnextUnitsActionNameList, waitnextUnitsActionNameList.length);
					  tempUnitActionList.clear();
					  tempUnitActionList  = new ArrayList<Pair<Unit, UnitAction>>(waitUnitActionList);				  
					  
					  
					  //change the action for this unit from wait to the current one in loop
				//	  tempUnitActionList.removeIf(u -> u.getKey.getID() == unitActionPair.getKey().getID());
					  tempUnitActionList.removeIf(u -> u == unitActionPair);
					  tempUnitActionList.add(new Pair<Unit, UnitAction>(unitActionPair.getKey(),action));
					  
					  				  
					  tempnextUnitsActionNameList[unitActionPair.getKey().getX()*unitActionPair.getKey().getY()]
							  = unitActionPair.getKey().toString()+action.toString();
					  
					  //create hash key
					  unitActionRef = Arrays.deepHashCode(Arrays.copyOf(tempnextUnitsActionNameList, 
							  tempnextUnitsActionNameList.length));
					  
					  //add hash key, next possible action and initial weights
					  nextActions.add(unitActionRef);					  				  
					  nextPossibleUnitActions.put(unitActionRef, tempUnitActionList.stream().collect(Collectors.toList()));
					  if(!weights.checkUnitActionWeights(unitActionRef)){
							weights.addInitialWeights(gs, unitActionRef);
							/*try {
								if(debugStatus)saveDebugInfo(weights.getUnitActionWeightl(unitActionRef),unitActionRef);
							} catch (IOException e) {e.printStackTrace();}*/
							
					  }
							
					 }//actions allowed in this state loop					 
				 }//non wait action loop
			});//action loop
		});//unit loop
			
	}
	public void addUnits(){
		
	}
	public List<Integer> getNextActionsHash(){
		return nextActions.stream().collect(Collectors.toList());
	}
	public List<Pair<Unit, UnitAction>> getNextUnitActions(Integer nextAction){
		
		return nextPossibleUnitActions.get(nextAction);//.stream().collect(Collectors.toList()); 
		
	}
	public Map<Integer,List<Pair<Unit, UnitAction>>> getNextPossibleUnitActions(){
		
		return nextPossibleUnitActions; 
		
	}
	public void printGeneratedActions(){
		for(Integer key:nextPossibleUnitActions.keySet() ){
			System.out.println("Key " + key + " Value " + nextPossibleUnitActions.get(key) );
		}
	}
    public void setDebugStatus(boolean status, BufferedWriter bufw){
    	this.debugStatus = status;
    	this.debugFile = bufw;
    }
    public void saveDebugInfo(Map<String,Float> weights, int actionhash) throws IOException {
    	
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	debugFile.write("Initial Weights for action - " + actionhash) ; debugFile.newLine();
    	debugFile.write("=========================================================================================================================================");debugFile.newLine();
    	for(String feat: weights.keySet()){
			debugFile.write("Feature " + feat + ", Weight - " + weights.get(feat));
			debugFile.newLine();
		}        	
    }

	
}