/*
    Copyright (C) 2008-2010 Stephan Schiffel <stephan.schiffel@gmx.de>
                  2010-2020 Nicolas JEAN <njean42@gmail.com>
                  2020 Michael Dorrell <michael.dorrell97@gmail.com>

    This file is part of GameController.

    GameController is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GameController is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GameController.  If not, see <http://www.gnu.org/licenses/>.
*/

package tud.gamecontroller.players.CheatHyperPlayer;

import tud.auxiliary.CrossProductMap;
import tud.gamecontroller.ConnectionEstablishedNotifier;
import tud.gamecontroller.GDLVersion;
import tud.gamecontroller.game.*;
import tud.gamecontroller.game.impl.JointMove;
import tud.gamecontroller.players.LocalPlayer;
import tud.gamecontroller.term.TermInterface;

import java.io.*;
import java.util.*;

/*
	How it works:

	At game start:
		Prepares the data structures

	On first move:
		Makes an inital model with:
			null joint action
			initial percepts
			initial state
		Uses this model to generate all legal moves and select one

	On all subsequent moves:
		Update percepts
		Add last move to action tracker
		For each model:
			Update model by choosing a random move such that:
				The player's move matches the last move actually performed
			Ensure the percepts of this update match the actual percepts received after the last move. If they do not:
				Add move to 'bad moves' tracker for state
				Remove model from hypergame set
			Branch the model by:
				Cloning the original model and update such that:
					Player's move matches the last move actually performed
					The move chosen isn't the same move as was chosen for a previous update/branch from the initial state
				Ensure percepts match, then add to hypergame set up to limit
			For all updates and branches, add the legal moves to a set
		Select a move from the legal moves set

	Move Selection:
		Weighted based on likelihood of each hypergame using uniform opponent modelling
 */

/**
 * HyperPlayer is an agent that can play imperfect information and non-deterministic two player extensive-form games
 * with perfect recall by holding many 'hypergames' as models that may represent the true state of the game in perfect
 * information representation. It then calculates the best move for each hypergame weighted against the likelihood of
 * it representing the true state of the game and returns the moves with the greatest weighted expected payoff.
 *
 * This variant uses a central datastructure - LikelihoodTree - to track the likelihood of each state.
 *
 * This cheat variant is intended to work only when the rules of the GDL game have been modified to allow this player
 * full information of the game
 * It uses 1 hypergame and runs 256 simulations per game when fully resourced
 * 		Only 1 hypergame is required since this algorithm has information on the true state
 * 		256 was the number determined in the Hyperplay paper for a fully resourced cheat player
 *
 * Implements the algorithm described in Michael Schofield, Timothy Cerexhe and Michael Thielscher's HyperPlay paper
 * with some alteration to the backtracking
 * @see "https://staff.cdms.westernsydney.edu.au/~dongmo/GTLW/Michael_Tim.pdf"
 *
 *
 * @author Michael Dorrell
 * @version 1.0
 * @since 1.0
 */
public class CheatHyperPlayer<
	TermType extends TermInterface,
	StateType extends StateInterface<TermType, ? extends StateType>> extends LocalPlayer<TermType, StateType>  {

	// Logging variables
	private String matchID;
	private String gameName;
	private String roleName;

	// Hyperplay variables
	private Random random;
	private int numHyperGames = 1; // The maximum number of hypergames allowable
	private int numHyperBranches = 1; // The amount of branches allowed
	private HashMap<Integer, Collection<JointMove<TermType>>> currentlyInUseMoves; // Tracks all of the moves that are currently in use
	private int depth; // Tracks the number of simulations run @todo: name better
	private int maxNumProbes = 16; // @todo: probably remove later
	private int stepNum; // Tracks the steps taken
	private HashMap<Integer, MoveInterface<TermType>> actionTracker; // Tracks the action taken at each step by the player (from 0)
	private HashMap<Integer, Collection<TermType>> perceptTracker; // Tracks the percepts seen at each step by the player (from 0)
	private HashMap<Integer, Collection<JointMove<TermType>>> badMovesTracker; // Tracks the invalid moves from each perfect-information state
	private ArrayList<Model<TermType>> hypergames; // Holds a set of possible models for the hypergame
	private StateInterface<TermType, ?> initialState; // Holds the initial state
	private LikelihoodTree<TermType> likelihoodTree;
	private int numTimesMovesSimulated = 0;
	private int numTimesHypergameForward = 0;

	private long timeLimit; // The total amount of time that can be
	private long startTime;
	private long timeexpired;
	private static final long PREFERRED_PLAY_BUFFER = 1000; // 1 second buffer before end of game to select optimal move

	public CheatHyperPlayer(String name, GDLVersion gdlVersion) {
		super(name, gdlVersion);
		random = new Random();

		// Override settings with config file
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader("java/tud/gamecontroller/players/agentConfig/" + this.getName() + ".config"));
			String row;
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(":");
				if(data[0].equals("maxNumProbes")) maxNumProbes = Integer.parseInt(data[1]);
			}
			csvReader.close();
		}  catch (IOException e) {
			System.out.println(this.getName() + ": NO CONFIG FILE FOUND");
		}
	}

	/**
	 * Runs at game start to set-up the player
	 *
	 * @param match - The match being played
	 * @param role - The role of the player
	 * @param notifier - Indicates the player's intentions to the gamecontroller
	 */
	@Override
	public void gameStart(RunnableMatchInterface<TermType, StateType> match, RoleInterface<TermType> role, ConnectionEstablishedNotifier notifier) {
		super.gameStart(match, role, notifier);

		// Instantiate globals
		actionTracker = new HashMap<Integer, MoveInterface<TermType>>();
		perceptTracker = new HashMap<Integer, Collection<TermType>>();
		badMovesTracker = new HashMap<Integer, Collection<JointMove<TermType>>>();
		currentlyInUseMoves = new HashMap<Integer, Collection<JointMove<TermType>>>();
		hypergames = new ArrayList<Model<TermType>>();
		likelihoodTree = new LikelihoodTree<TermType>(0);
		stepNum = 0;
		timeLimit = (this.match.getPlayclock()*1000 - PREFERRED_PLAY_BUFFER);

		// Instantiate logging variables
		matchID = match.getMatchID();
		gameName = match.getGame().getName();
		roleName = role.toString();
	}

	/**
	 * Runs at the start of each player's turn to update imperfect information state and get the next move of the player
	 *
	 * @param seesTerms - The percepts seen by the player after the last turn
	 * @param priorMove - The move performed by the player at the last turn
	 * @param notifier - Indicates the player's intentions to the gamecontroller
	 * @return The move the player has selected
	 */
	@SuppressWarnings("unchecked")
	@Override
	public MoveInterface<TermType> gamePlay(Object seesTerms, Object priorMove, ConnectionEstablishedNotifier notifier) {
		notifyStartRunning();
		notifier.connectionEstablished();
		numTimesMovesSimulated = 0;
		numTimesHypergameForward = 0;
		perceptTracker.put(stepNum, (Collection<TermType>) seesTerms); // Puts the percepts in the map at the current step
		if(stepNum >= 0) {
			actionTracker.put(stepNum - 1, (MoveInterface<TermType>) priorMove); // Note: This won't get the final move made
		}
		MoveInterface<TermType> move = getNextMove();

		notifyStopRunning();
		stepNum++;
		return move;
	}

	/**
	 * Returns the agent's next move by first updating and branching each hypergame and using these to calculate the
	 * move with the greatest probability of a good outcome
	 *
	 * @return A legal move
	 */
	public MoveInterface<TermType> getNextMove() {
		startTime =  System.currentTimeMillis();
		timeexpired = 0;

		HashSet<MoveInterface<TermType>> legalMoves = new HashSet<MoveInterface<TermType>>();
		HashSet<MoveInterface<TermType>> legalMovesInState = null;

		// If it is the first step, then create the first hypergame with the initial state
		if(stepNum == 0) {
			// Create first model to represent the empty state
			Model<TermType> model = new Model<TermType>();
			Collection<TermType> initialPercepts = perceptTracker.get(stepNum);
			initialState = match.getGame().getInitialState();
			model.updateGameplayTracker(stepNum, initialPercepts, null, initialState, role, 1);
			likelihoodTree = new LikelihoodTree<TermType>(model.getActionPathHash());

			hypergames.add(model);

			// Get legal moves from this model
			legalMoves = new HashSet<MoveInterface<TermType>>(model.computeLegalMoves(role, match));
		} else {
			// For each model in the the current hypergames set, update it with a random joint action that matches player's last action and branch by the branching factor
			ArrayList<Model<TermType>> currentHypergames = new ArrayList<Model<TermType>>(hypergames);
			for (Model<TermType> model : currentHypergames) {
				// Save a copy of the model
				Model<TermType> cloneModel = new Model<TermType>(model);
				int previousActionPathHash = model.getPreviousActionPathHash();
				int currActionPathHash = model.getActionPathHash();
				JointMove<TermType> previousAction = model.getLastAction();

				// Forward the model
				int step = model.getActionPath().size();
				while(step < stepNum + 1) {
					step = forwardHypergame(model, step);
					if(step < stepNum - 1 || step == 0) break;
				}
				// If the hypergame has gone through all possible updates from the current state, then remove it from the set of hypergames
				/* This can be done without checking if future states are in use since this is updating the state, rather than branching
				 	Therefore: No states can be beyond this one from the same node
				 */
				if(step < stepNum - 1 || step == 0) {
					// Add state to bad move tracker
					if(step > 1) {
						updateBadMoveTracker(model.getPreviousActionPathHash(), model.getLastAction(), model.getActionPathHashPath());
					}

					// Remove model
					hypergames.remove(model);
					continue;
				}

				updateCurrentlyInUseMoves(model, currActionPathHash, previousActionPathHash, previousAction);

				// Get legal moves
				legalMovesInState = new HashSet<MoveInterface<TermType>>(model.computeLegalMoves(role, match));
				legalMoves.addAll(legalMovesInState);

				// Branch the clone of the model
				boolean keepBranching = true;
				for(int i = 0 ; i < numHyperBranches - 1; i++) {
					if(hypergames.size() < numHyperGames && keepBranching) {
						// Clone the model
						Model<TermType> newModel = new Model<TermType>(cloneModel);
						previousActionPathHash = newModel.getPreviousActionPathHash();
						currActionPathHash = newModel.getActionPathHash();
						previousAction = newModel.getLastAction();

						// Forward the new model
						step = newModel.getActionPath().size();
						while(step < stepNum + 1) {
							step = forwardHypergame(newModel, step);
							if(step < stepNum - 1 || step == 0) break;
						}
						// If the hypergame has gone through all possible updates from the current state, then break and don't add it to the hyperset
						/* If this occurs on a branch then there must be a successful state after the current state, but not enough to branch
							Therefore no need to discard the current state yet
						 */
						if(step < stepNum - 1 || step == 0) {
							keepBranching = false;
							break;
						}

						// Add to hypergames set and get legal moves
						hypergames.add(newModel);

						// Keep track of moves in use
						updateCurrentlyInUseMoves(newModel, currActionPathHash, previousActionPathHash, previousAction);

						// Get legal moves
						legalMovesInState = new HashSet<MoveInterface<TermType>>(newModel.computeLegalMoves(role, match));
						legalMoves.addAll(legalMovesInState);
					} else break;
				}
			}
		}

		// If no hypergames left, then run until one exists
		System.out.println(this.getName() + ": Number of hypergames after updating: " + hypergames.size());
		while(hypergames.size() == 0) {
			System.out.println(this.getName() + ": Trying to find another path");
			// Create first model to represent the empty state
			Model<TermType> model = new Model<TermType>();
			Collection<TermType> initialPercepts = perceptTracker.get(0);
			model.updateGameplayTracker(0, initialPercepts, null, initialState, role, 1);

			int step = model.getActionPath().size();
			int maxStep = step;
			while(step < stepNum + 1) {
				step = forwardHypergame(model, step);
				if(step < maxStep - 1) break;
				maxStep = Math.max(step, maxStep);
			}
			if(step < maxStep - 1) continue;

			hypergames.add(model);

			// Get legal moves from this model
			legalMoves = new HashSet<MoveInterface<TermType>>(model.computeLegalMoves(role, match));
		}
		System.out.println(this.getName() + ": Number of hypergames after >=1 found: " + hypergames.size());

		//Calculate how long the update took
		long endTime =  System.currentTimeMillis();
		long updateTime = endTime - startTime;

		// Print all models
//		System.out.println();
//		printHypergames();
//		System.out.println();
//		System.out.println(likelihoodTree.toString());
//		System.out.println();

		// Select a move
		long selectStartTime =  System.currentTimeMillis();
		MoveInterface<TermType> bestMove = anytimeMoveSelection(legalMoves);
		long selectEndTime =  System.currentTimeMillis();
		long selectTime = selectEndTime - selectStartTime;

		// Print move to file
		try {
			FileWriter myWriter = new FileWriter("matches/" + matchID + ".csv", true);
			myWriter.write(matchID + "," + gameName + "," + stepNum + "," + roleName + "," + name + "," + hypergames.size() + "," + depth + "," + updateTime + "," + selectTime + "," + bestMove + "," + false + "," + numTimesMovesSimulated + "," + numTimesHypergameForward + "\n");
			myWriter.close();
		} catch (IOException e) {
			System.err.println("An error occurred.");
			e.printStackTrace();
		}

		return bestMove;
	}

	/**
	 * Select a move amongst the possible moves randomly
	 *
	 * @param possibleMoves - A set of the possible moves
	 * @return The chosen move
	 */
	public MoveInterface<TermType> randomMoveSelection(HashSet<MoveInterface<TermType>> possibleMoves) {
		int rand = random.nextInt(possibleMoves.size());
		MoveInterface<TermType> chosenMove = null;
		int i = 0;
		for(MoveInterface<TermType> move : possibleMoves) {
			if(i == rand) {
				chosenMove = move;
			}
			i++;
		}
		return chosenMove;
	}

	/**
	 * anytimeMoveSelection calculates the optimal move to select given a set of possible moves and a bag of hypergames
	 * with increasing accuracy by running more simulations as time permits
	 *
	 * @param possibleMoves A set of possible moves
	 * @return An approximation of the optimal move from known information
	 */
	public MoveInterface<TermType> anytimeMoveSelection(HashSet<MoveInterface<TermType>> possibleMoves) {
		// Calculate P(HG)
		// Calculate inverse choice factor sum @todo: is this necessary to get the invChoiceFactorSum?
		HashMap<Integer, Double> choiceFactors = new HashMap<Integer, Double>();
		double choiceFactor;
		double invChoiceFactorSum = 0;
		for(Model<TermType> model : hypergames) {
			choiceFactor = likelihoodTree.getChoiceFactor(model.getActionPathHashPath());
			double treecf = model.getNumberOfPossibleActions();
			if(choiceFactor != treecf) {
				System.out.println("NO MATCH");
				System.out.println("likelihoodTree choice facto: " + choiceFactor);
				System.out.println("Possible actions: " + treecf);
				System.out.println();
				System.out.println("model.getActionPathHashPath(): " + model.getActionPathHashPath());
				System.out.println("Likelihood Tree Nodes: ");
				ArrayDeque<Integer> incrementalPath = new ArrayDeque<Integer>();
				for(Integer actionPathHash : model.getActionPathHashPath()) {
					incrementalPath.addLast(actionPathHash);
					System.out.println(likelihoodTree.getNode(incrementalPath));
				}
				System.out.println();
				System.out.println("model.getNumberOfPossibleActionsPath()" + model.getNumberOfPossibleActionsPath());
				System.exit(0);
			}
			choiceFactors.put(model.getActionPathHash(), choiceFactor);
			invChoiceFactorSum += (1.0/choiceFactor);
		}

		// Calculate the probability of each hypergame
		HashMap<Integer, Double> hyperProbs = new HashMap<Integer, Double>();
		double prob;
		for(Model<TermType> model : hypergames) {
			choiceFactor = choiceFactors.get(model.getActionPathHash());
			prob = ((1/(Double)choiceFactor)/invChoiceFactorSum);
			hyperProbs.put(model.getActionPathHash(), prob);
		}

		// Calculate expected move value for each hypergame until almost out of time
		HashMap<Integer, Double> weightedExpectedValuePerMove = new HashMap<Integer, Double>();
		HashMap<Integer, MoveInterface<TermType>> moveHashMap = new HashMap<Integer, MoveInterface<TermType>>();
		depth = 1;
		while(timeexpired < timeLimit && depth < maxNumProbes) { // @todo: May need to add break points at the end of each move calc and each hypergame calc
			for (Model<TermType> model : hypergames) {
				StateInterface<TermType, ?> currState = model.getCurrentState(match);
				for (MoveInterface<TermType> move : possibleMoves) {
					moveHashMap.put(move.hashCode(), move);
					// Calculate the the expected value for each move using monte carlo simulation
					double expectedValue = anytimeSimulateMove(currState, move);

					// Calculate the weighted expected value for each move
					double weightedExpectedValue = expectedValue * hyperProbs.get(model.getActionPathHash());

					// Add expected value to hashmap
					if (!weightedExpectedValuePerMove.containsKey(move.hashCode())) {
						weightedExpectedValuePerMove.put(move.hashCode(), weightedExpectedValue);
					} else {
						double prevWeightedExpectedValue = weightedExpectedValuePerMove.get(move.hashCode());
						weightedExpectedValuePerMove.replace(move.hashCode(), prevWeightedExpectedValue + weightedExpectedValue);
					}
				}
			}
			timeexpired = System.currentTimeMillis() - startTime;
			depth++;
		}
		System.out.println("Ran " + depth + " simulations TOTAL");

		// Return the move with the greatest weighted expected value
		long startFinalCalcTime =  System.currentTimeMillis();

		Iterator<HashMap.Entry<Integer, Double>> it = weightedExpectedValuePerMove.entrySet().iterator();
		double maxVal = -(Double.MAX_VALUE);
		MoveInterface<TermType> bestMove = null;

		// Write the moveset to a file
		try {
			// Create the file
			new File("matches/cheat_move_distribution/" + matchID).mkdirs();
			FileWriter myWriter = new FileWriter("matches/cheat_move_distribution/" + matchID + "/" + stepNum +  ".csv", false);

			// Iterate through moves
			while(it.hasNext()){
				HashMap.Entry<Integer, Double> mapElement = (HashMap.Entry<Integer, Double>)it.next();
				Double val = mapElement.getValue();
				if(val > maxVal) {
					bestMove = moveHashMap.get(mapElement.getKey());
					maxVal = val;
				}

				myWriter.write(moveHashMap.get(mapElement.getKey()) + "," + (val/maxNumProbes) + "\n");

			}

			myWriter.close();
		} catch (IOException e) {
			System.err.println("An error occurred.");
			e.printStackTrace();
		}

		long endFinalCalcTime =  System.currentTimeMillis();
		long updateTime = endFinalCalcTime - startFinalCalcTime;
//		System.out.println("Took " + updateTime + " ms to run final calc");


		return bestMove;
	}

	/**
	 * Get the expected result of a move from a given state using a single monte carlo simulation
	 *
	 * @param state - The current state of the game
	 * @param move - The first move to be tried
	 * @return The statistical expected result of a move
	 */
	public float anytimeSimulateMove(StateInterface<TermType, ?> state, MoveInterface<TermType> move) {
		int expectedOutcome = 0;
		// Repeatedly select random joint moves until a terminal state is reached
		StateInterface<TermType, ?> currState = state;
		JointMoveInterface<TermType> randJointMove;
		boolean isFirstMove = true;
		while(!currState.isTerminal()) {
			if(isFirstMove) {
				try {
					randJointMove = getRandomJointMove(currState, move);
				} catch(Exception e) {
					return 0;
				}
				isFirstMove = false;
			} else {
				randJointMove = getRandomJointMove(currState);
			}
			currState = currState.getSuccessor(randJointMove);
		}
		expectedOutcome += currState.getGoalValue(role);
		numTimesMovesSimulated++;
		return (float)expectedOutcome;
	}

	/**
	 * Updates the currentlyInUseMoves hashmap that tracks the moves that should not be added to the hypergames set due
	 * to redundancy
	 *
	 * @param model - The model of the hypergame to update
	 * @param currActionPathHash - The current hash of the current action path to identify the action path as in-use
	 * @param previousActionPathHash - The hash of the current action path without the final move to be removed from in-use
	 * @param previousAction - The final move to be removed from the action path map
	 */
	public void updateCurrentlyInUseMoves(Model<TermType> model, int currActionPathHash, int previousActionPathHash, JointMove<TermType> previousAction){
		HashSet<JointMove<TermType>> inuse = new HashSet<JointMove<TermType>>();
		// First add the new move to the currently inuse
		if(currentlyInUseMoves.containsKey(currActionPathHash)){
			inuse = (HashSet<JointMove<TermType>>) currentlyInUseMoves.get(currActionPathHash);
			inuse.add(model.getLastAction());
		} else {
			inuse.add(model.getLastAction());
			currentlyInUseMoves.put(currActionPathHash, inuse);
		}
		// Remove the previous state-action pair from inuse
		if(currentlyInUseMoves.containsKey(previousActionPathHash)) {
			inuse = (HashSet<JointMove<TermType>>) currentlyInUseMoves.get(previousActionPathHash);
			inuse.remove(previousAction);
		}
	}

	/**
	 * Prints the details about each hypergame in the set of hypergames
	 */
	public void printHypergames() {
		// Print model info
		System.out.println("TRUE PERCEPTS:");
		System.out.println(perceptTracker.get(stepNum));
		System.out.println("MODELS:");
		for (int i = 0 ; i < hypergames.size() ; i++) {
			Model<TermType> mod = hypergames.get(i);
			System.out.println("\tModel Hash: " + mod.getActionPathHash());
			System.out.println("\tAction Path: ");
			for (JointMove<TermType> jointActionInPath : mod.getActionPath()) {
				System.out.println("\t\t" + jointActionInPath);
			}
			System.out.println("\tPercept Path: ");
			for (Collection<TermType> perceptsInPath : mod.getPerceptPath()) {
				System.out.println("\t\t" + perceptsInPath);
			}
			System.out.println("\tState Path: ");
			for (StateInterface<TermType, ?> statesInPath : mod.getStatePath()) {
				System.out.println("\t\t" + statesInPath);
			}
			System.out.println();
		}
		System.out.println("Num hypergames: " + hypergames.size());
	}

	/**
	 * Forwards the hypergame by trying a random joint move such that:
	 * 		The action taken by the player is the same action that was taken in reality last round
	 * 		The action-path generated is not already in use
	 * 		The percepts generated by this action match the percepts actually seen at this step
	 *
	 * 	If these conditions are met, it will return (step + 1)
	 * 	If the percepts do not match, then it will mark the action as 'BAD' and return (step) to attempt a different action at the same state
	 * 	If no such actions are found, then it will mark this state as 'BAD' and will return (step - 1) to attempt a different action at the previous state
	 *
	 * @param model - The model to forward
	 * @param step - The current step of the model
	 * @return The step of the model
	 */
	public int forwardHypergame(Model<TermType> model, int step) {
		numTimesHypergameForward++;
		// Update the model using a random joint move
			// Get all possible moves and remove the known bad moves
		StateInterface<TermType, ?> state = model.getCurrentState(match);
		ArrayList<JointMoveInterface<TermType>> possibleJointMoves = new ArrayList<JointMoveInterface<TermType>>(computeJointMoves((StateType) state, actionTracker.get(step - 1)));
		int numPossibleJointMoves = possibleJointMoves.size();
		removeBadMoves(possibleJointMoves, model.getActionPathHash());

		// Update the value of the current state in the likelihood tree
//		System.out.println();
//		System.out.println("Tree : " + likelihoodTree.toString());
//		System.out.println("root: " + likelihoodTree.getRoot());
//		System.out.println("rood node: " + likelihoodTree.getRoot().toString());
//		System.out.println("Action Path Hash: " + model.getActionPathHashPath());
//		System.out.println("Getting node: " + likelihoodTree.getNode(model.getActionPathHashPath()));
//		System.out.println("Num possibilities: " + possibleJointMoves.size());
//		System.out.println();

		Node node = likelihoodTree.getNode(model.getActionPathHashPath());
		node.setValue(Math.max(node.getValue(), possibleJointMoves.size()));
//		System.out.println("Num possibilities: " + possibleJointMoves.size());
//		System.out.println("Updated node: " + likelihoodTree.getNode(model.getActionPathHashPath()));

//		System.exit(0);

		removeInUseMoves(possibleJointMoves, model.getActionPathHash());
		JointMove<TermType> jointAction = null;
		int numCleanJointMoves = possibleJointMoves.size();
		if(numCleanJointMoves > 0) {
			int i = random.nextInt(numCleanJointMoves);
			jointAction = (JointMove<TermType>) possibleJointMoves.get(i);
		}

		// If there are no valid moves from this state, then backtrack and try again
		if (jointAction == null) {
			// Get move that got to this state and add to bad move set
			JointMove<TermType> lastAction = model.getLastAction();
			model.backtrack();

			// Add move to bad move set if there are no other active moves from this point
			updateBadMoveTracker(model.getActionPathHash(), lastAction, model.getActionPathHashPath());

			return step - 1;
		} else {
			// If a valid move could be found, update the state
			model.updateGameplayTracker(step, null, jointAction, state, role, numPossibleJointMoves);

			// Check if new model does not match expected percepts
			if (!model.getLatestExpectedPercepts().equals(perceptTracker.get(step))) {
				// Backtrack
				model.backtrack();

//				System.out.println();
//				System.out.println("Latest actionpath hash: " + model.getActionPathHash());
//				System.out.println("Actionpath hash path: " + model.getActionPathHashPath());
//				System.out.println();

				// Add move to bad move set
				updateBadMoveTracker(model.getActionPathHash(), jointAction, model.getActionPathHashPath());

				// Try again
				return step;
			} else {
				// Else this is a valid move

				// Update the likelihood tree
				node.addChild(new Node(model.getActionPathHash()));

				return step + 1;
			}
		}
	}

	/**
	 * Updates the bad move tracker at the action-path hash for the last action
	 *
	 * @param backtrackedModelHash - The action-path hash to add the bad move to
	 * @param badMove - The bad move to add to the tracker
	 */
	public void updateBadMoveTracker(int backtrackedModelHash, JointMove<TermType> badMove, ArrayDeque<Integer> actionPathHashPath) {
		if (badMovesTracker.containsKey(backtrackedModelHash)) {
			Collection<JointMove<TermType>> badJointActions = badMovesTracker.get(backtrackedModelHash);
			badJointActions.add(badMove);
		} else {
			Collection<JointMove<TermType>> badJointActions = new ArrayList<JointMove<TermType>>();
			badJointActions.add(badMove);
			badMovesTracker.put(backtrackedModelHash, badJointActions);
		}

		// Decrement the value at the node
//		Node node = likelihoodTree.getNode(actionPathHashPath);
//		if(node != null) {
//			System.out.println("DECREMENTED " + backtrackedModelHash + " for the move " + badMove + " with actionPathHashPath " + actionPathHashPath);
//			node.setValue(Math.max(node.getValue() - 1, 0));
//		}
	}

	/**
	 * Gets a random joint move given the action matches the action taken in the last step
	 *
	 * @param state - The current state
	 * @param action - The action that the player will take
	 * @return A random joint move
	 */
	public JointMoveInterface<TermType> getRandomJointMove(StateInterface<TermType, ?> state, MoveInterface<TermType> action) {
		ArrayList<JointMoveInterface<TermType>> possibleJointMoves = new ArrayList<JointMoveInterface<TermType>>(computeJointMoves((StateType) state, action));
		JointMoveInterface<TermType> jointMove = null;
		if(possibleJointMoves.size() > 0) {
			int i = random.nextInt(possibleJointMoves.size());
			jointMove = possibleJointMoves.get(i);
		}
		return jointMove;
	}
	public JointMoveInterface<TermType> getRandomJointMove(StateInterface<TermType, ?> state) {
		ArrayList<JointMoveInterface<TermType>> possibleJointMoves = new ArrayList<JointMoveInterface<TermType>>(statesTracker.computeJointMoves((StateType) state));
		JointMoveInterface<TermType> jointMove = null;
		if(possibleJointMoves.size() > 0) {
			int i = random.nextInt(possibleJointMoves.size());
			jointMove = possibleJointMoves.get(i);
		}
		return jointMove;
	}

	/**
	 * computeJointMoves computes all joint moves possible from a state such that the action taken by the player is the action input
	 *
	 * @param state - The state to compute action from
	 * @param action - The action to be taken by the player
	 * @return A set of all possible moves
	 */
	public Collection<JointMoveInterface<TermType>> computeJointMoves(StateType state, MoveInterface<TermType> action) {
		// compute legal moves for all roles such that the action matches for the player's role
		HashMap<RoleInterface<TermType>, Collection<? extends MoveInterface<TermType>>> legalMovesMap = new HashMap<RoleInterface<TermType>, Collection<? extends MoveInterface<TermType>>>();
		for(RoleInterface<TermType> role: match.getGame().getOrderedRoles()) {
			if(role == this.role) {
				Collection<MoveInterface<TermType>> lastMoveMap = new ArrayList<MoveInterface<TermType>>();
				lastMoveMap.add(action);
				legalMovesMap.put(role, lastMoveMap);
			} else {
				legalMovesMap.put(role, state.getLegalMoves(role));
			}
		}
		// build the cross product
		final CrossProductMap<RoleInterface<TermType>, MoveInterface<TermType>> jointMovesMap = new CrossProductMap<RoleInterface<TermType>, MoveInterface<TermType>>(legalMovesMap);
		// wrap the elements of the cross product in JointMove<TermType>
		// the following is an on-the-fly collection that just refers to "jointMoves" above
		Collection<JointMoveInterface<TermType>> jointMoves = new AbstractCollection<JointMoveInterface<TermType>>(){
			@Override
			public Iterator<JointMoveInterface<TermType>> iterator() {
				final Iterator<Map<RoleInterface<TermType>, MoveInterface<TermType>>> iterator = jointMovesMap.iterator();
				return new Iterator<JointMoveInterface<TermType>>(){
					@Override public boolean hasNext() { return iterator.hasNext(); }

					@Override public JointMoveInterface<TermType> next() { return new JointMove<TermType>(match.getGame().getOrderedRoles(), iterator.next()); }

					@Override public void remove() { iterator.remove();	}
				};
			}

			@Override
			public int size() {
				return jointMovesMap.size();
			}
		};
		return jointMoves;
	}

	/**
	 * Removes all moves in the bad move Tracker
	 *
	 * @param jointMoves - A list of joint moves
	 * @param actionPathHash - The action-path hash from which to consider which moves are invalid
	 */
	public void removeBadMoves(ArrayList<JointMoveInterface<TermType>> jointMoves, int actionPathHash) {
		if(badMovesTracker.containsKey(actionPathHash)) {
			jointMoves.removeAll(badMovesTracker.get(actionPathHash));
		}
	}

	/**
	 * Removes all moves in the in-use moves tracker
	 *
	 * @param jointMoves - A list of joint moves
	 * @param actionPathHash - The action-path hash from which to consider which moves are invalid
	 */
	public void removeInUseMoves(ArrayList<JointMoveInterface<TermType>> jointMoves, int actionPathHash) {
		if(currentlyInUseMoves.containsKey(actionPathHash)) {
			jointMoves.removeAll(currentlyInUseMoves.get(actionPathHash));
		}
	}
}
