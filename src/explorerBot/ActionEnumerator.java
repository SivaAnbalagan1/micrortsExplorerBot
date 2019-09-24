package explorerBot;

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
import util.Pair;

public class ActionEnumerator {
	ActionEnumerator(){
		unitActionList = new ArrayList<>();
		tempUnitActionList = new ArrayList<>();
		
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
    private String[] nextUnitsActionNameList; 
    private String[] tempnextUnitsActionNameList;
    private List<Pair<Unit, UnitAction>> unitActionList;
    private List<Pair<Unit, UnitAction>> tempUnitActionList;
    private Iterator<Pair<Unit, UnitAction>> iter;
    private Map<Integer,List<Pair<Unit, UnitAction>>> nextPossibleUnitActions;
    private UnitAction unitAction;
    int unitActionRef;
    
    //private hashList
	
	public void set(GameState gs,WeightStore weights,int player){
		this.player = player;
		this.gs = gs;		
		this.terrain = new Unit[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.nextUnitsActionNameList = new String[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.tempnextUnitsActionNameList = new String[gs.getPhysicalGameState().getHeight() * gs.getPhysicalGameState().getWidth()];
		this.weights = weights;
	}

	/*
	 * Create new state by changing 1 action of any of the available unit. 
	 * */
	public void generateNextActions(){
		availableUnits = gs.getUnits().stream()
				           .filter(unit-> unit.getPlayer() == player)
				           .collect(Collectors.toList());
		//State - No action to any units.
		availableUnits.forEach((unit)-> {
						  terrain[unit.getX()*unit.getY()] = unit;
						  unitActions = unit.getUnitActions(gs,0);
						  unitAction = unitActions.stream()
								  		.filter(unitAction -> unitAction.getActionName().equals("wait"))
								  		.findFirst().get();
						  unitActionList.add(new Pair<Unit, UnitAction>(unit,unitAction));
						  nextUnitsActionNameList[unit.getX()*unit.getY()] =  unitAction.getActionName();
					  });
		
		unitActionRef = Arrays.deepHashCode(nextUnitsActionNameList);
		if(!weights.checkUnitActionWeights(unitActionRef))
			weights.addInitialWeights(gs, unitActionRef);
		/*create other states
		 * */
		nextActions.add(unitActionRef);
		nextPossibleUnitActions.put(unitActionRef, unitActionList);
		
		// adding all possible actions
		
		availableUnits.forEach((unit)-> {
			  terrain[unit.getX()*unit.getY()] = unit;
			  unitActions = unit.getUnitActions(gs,0);
			  unitActions.forEach((action) -> {
				  tempUnitActionList.clear();
				  tempnextUnitsActionNameList = Arrays.copyOf(nextUnitsActionNameList, nextUnitsActionNameList.length);
				  unitActionList.forEach((unitActionPair)->{
						tempUnitActionList.add(unitActionPair);
					});	
				  if(action.getActionName() != "wait"){
					   iter = tempUnitActionList.iterator();
					   int ix =0;
					   while(iter.hasNext()){
						   if(iter.equals(unit)){
							   tempUnitActionList.add(ix, new Pair<Unit, UnitAction>(unit,action)); 
						   }
						   iter.next();ix++;
					   }
					   tempnextUnitsActionNameList[unit.getX()*unit.getY()] = action.getActionName();
						unitActionRef = Arrays.deepHashCode(tempnextUnitsActionNameList);
						if(!weights.checkUnitActionWeights(unitActionRef))
							weights.addInitialWeights(gs, unitActionRef);
						
						nextActions.add(unitActionRef);
						nextPossibleUnitActions.put(unitActionRef, tempUnitActionList);
				  }
				  
			  });			  
		  });
		/*		unitActionList.forEach((action)->{
		tempUnitActionList.add(action);
	});	
	tempnextActionList = Arrays.copyOf(nextActionList, nextActionList.length);
	tempUnitActionList.clear();
*/		

	}
	public List<Integer> getNextActionsHash(){
		return nextActions;
	}
	public List<Pair<Unit, UnitAction>> getNextUnitActions(Integer nextAction){
		return nextPossibleUnitActions.get(nextAction); 
		
	}
	
}