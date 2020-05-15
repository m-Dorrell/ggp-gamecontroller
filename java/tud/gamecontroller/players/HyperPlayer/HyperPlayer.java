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

package tud.gamecontroller.players.HyperPlayer;

import tud.auxiliary.CrossProductMap;
import tud.gamecontroller.ConnectionEstablishedNotifier;
import tud.gamecontroller.GDLVersion;
import tud.gamecontroller.game.*;
import tud.gamecontroller.game.impl.JointMove;
import tud.gamecontroller.players.LocalPlayer;
import tud.gamecontroller.term.TermInterface;

import java.util.*;

/**
 * HyperPlayer is an agent that can play imperfect information and non-deterministic two player extensive-form games
 * with perfect recall by holding many 'hypergames' as models that may represent the true state of the game in perfect
 * information representation. It then calculates the best move for each hypergame weighted against the likelihood of
 * it representing the true state of the game and returns the moves with the greatest weighted expected payoff.
 * Implements the algorithm descrived in Michael Schofield, Timothy Cerexhe and Michael Thielscher's HyperPlay paper
 * @see "https://staff.cdms.westernsydney.edu.au/~dongmo/GTLW/Michael_Tim.pdf"
 *
 * @author Michael Dorrell
 * @version 1.0
 * @since 1.0
 */
public class HyperPlayer<
	TermType extends TermInterface,
	StateType extends StateInterface<TermType, ? extends StateType>> extends LocalPlayer<TermType, StateType>  {

	private Random random;
	private int numHyperGames = 10;
	private int numHyperBranches = 20;
	private HashSet<Integer> hypergameHashSet;

	private int stepNum; // Tracks the steps taken
	private HashMap<Integer, MoveInterface<TermType>> actionTracker; // Tracks the action taken at each step by the player (from 0)
	private HashMap<Integer, Collection<TermType>> perceptTracker; // Tracks the percepts seen at each step by the player (from 0)
	private HashMap<String, Collection<MoveInterface<TermType>>> badMovesTracker; // Tracks the invalid moves from each perfect-information state
	private ArrayList<Model<TermType>> hypergames; // Holds a set of possible models for the hypergame

	public HyperPlayer(String name, GDLVersion gdlVersion) {
		super(name, gdlVersion);
		random = new Random();
	}

	/**
	 * Run when the game is start to perform basic set-up
	 */
	@Override
	public void gameStart(RunnableMatchInterface<TermType, StateType> match, RoleInterface<TermType> role, ConnectionEstablishedNotifier notifier) {
		super.gameStart(match, role, notifier);

		// Instantiate globals
		actionTracker = new HashMap<Integer, MoveInterface<TermType>>();
		perceptTracker = new HashMap<Integer, Collection<TermType>>();
		badMovesTracker = new HashMap<String, Collection<MoveInterface<TermType>>>();
		hypergames = new ArrayList<Model<TermType>>();
		hypergameHashSet = new HashSet<Integer>();
		stepNum = 0;
	}

	/*
	 * @param seesFluents is either:
	 * - with regular GDL, a "JointMoveInterface<TermType>" object that is the jointMove previously done by players
	 * - or with GDL-II, a "Collection<? extends FluentInterface<TermType>>" object that really are SeesTerms
	 */
	@SuppressWarnings("unchecked")
	@Override
	public MoveInterface<TermType> gamePlay(Object seesTerms, Object priorMove, ConnectionEstablishedNotifier notifier) {
		notifyStartRunning();
		notifier.connectionEstablished();
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
	 * Returns the agent's next move
	 *
	 * @return A legal move
	 */
	public MoveInterface<TermType> getNextMove() {
		ArrayList<MoveInterface<TermType>> legalMoves = null;
		if(stepNum == 0) {
			// Create first model to represent the empty state
			Model<TermType> model = new Model<TermType>();

			Collection<TermType> initialPercepts = perceptTracker.get(stepNum);
			JointMove<TermType> initialJointAction = null;
			StateInterface<TermType, ?> initialState = match.getGame().getInitialState();
			model.updateGameplayTracker(stepNum, initialPercepts, initialJointAction, initialState);

			hypergames.add(model);
			hypergameHashSet.add(model.getActionPathHash());

			// Get legal moves from this model
			legalMoves = new ArrayList<MoveInterface<TermType>>(model.computeLegalMoves(role));
		} else {
			// Get first model and update it with a random joint action that matches player's last action
			// @todo: Do this for all hypergames at each turn
			Model<TermType> model = hypergames.get(0);
			Model<TermType> cloneModel = new Model<TermType>(model);

			Collection<TermType> percepts = perceptTracker.get(stepNum);
			StateInterface<TermType, ?> state = model.getCurrentState();
			JointMove<TermType> jointAction = (JointMove<TermType>) getRandomJointMove(state);

			hypergameHashSet.remove(model.getActionPathHash());
			model.updateGameplayTracker(stepNum, percepts, jointAction, state);
			hypergameHashSet.add(model.getActionPathHash());

			// Branch numHyperBranches times
			for(int i = 0 ; i < numHyperBranches - 1; i++) {
				if(hypergames.size() < numHyperGames) {
					Model<TermType> newModel = new Model<TermType>(cloneModel);
					StateInterface<TermType, ?> currState = newModel.getCurrentState();
					JointMove<TermType> nextJointAction = (JointMove<TermType>) getRandomJointMove(state);

					newModel.updateGameplayTracker(stepNum, percepts, nextJointAction, currState);

					int newModelHash = newModel.getActionPathHash();
					if(hypergameHashSet.contains(newModelHash)) {
						i--; // @todo: This may cause an infinite loop
						System.out.println("DUPLICATE MOVE HANDLED");
					} else {
						hypergames.add(newModel);
						hypergameHashSet.add(newModelHash);
					}
				} else break;
			}

			// Print model info
			System.out.println("MODELS:");
			for (int i = 0 ; i < hypergames.size() ; i++) {
				Model<TermType> mod = hypergames.get(i);
				System.out.println("\tModel Hash: " + mod.getActionPathHash());
				System.out.println("\tAction Path: ");
				for (JointMove<TermType> jointActionInPath : mod.getActionPath()) {
					System.out.println("\t\t" + jointActionInPath);
				}
				System.out.println();
			}
			System.out.println(hypergameHashSet.size());

			// Get legal moves from model
			legalMoves = new ArrayList<MoveInterface<TermType>>(model.computeLegalMoves(role));
		}

		int i = random.nextInt(legalMoves.size());
		return legalMoves.get(i);
	}

	/**
	 * Gets a random joint move given the action matches the action taken in the last step
	 *
	 * @param state
	 * @return
	 */
	public JointMoveInterface<TermType> getRandomJointMove(StateInterface<TermType, ?> state) {
		ArrayList<JointMoveInterface<TermType>> possibleJointMoves = new ArrayList<JointMoveInterface<TermType>>(computeJointMoves((StateType) state, actionTracker.get(stepNum - 1)));
		int i = random.nextInt(possibleJointMoves.size());
		return possibleJointMoves.get(i);
	}

	/**
	 * computeJointMoves computes all joint moves possible from a state such that the action taken by the player is the action input
	 *
	 * @param state
	 * @return
	 */
	public Collection<JointMoveInterface<TermType>> computeJointMoves(StateType state, MoveInterface<TermType> action) {
		// compute legal moves for all roles
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
		// System.out.println("legal joint moves: " + jointMoves);
		return jointMoves;
	}
}
