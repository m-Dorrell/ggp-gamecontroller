/*
    Copyright (C) 2020 Michael Dorrell <michael.dorrell97@gmail.com>

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

import tud.gamecontroller.game.MoveInterface;
import tud.gamecontroller.game.RoleInterface;

import java.util.*;

import tud.gamecontroller.game.StateInterface;
import tud.gamecontroller.game.impl.JointMove;
import tud.gamecontroller.term.TermInterface;

/**
 * Holds a model of how the true state of the game may look given the percepts seen so far
 *
 * @author Michael Dorrell
 * @version 1.1
 * @since 1.0
 */
public class Model<TermType extends TermInterface> implements Cloneable{

    private Stack<JointMove<TermType>> actionPath; // Contains the action taken at each step
    private Stack<Integer> getNumberOfPossibleActionsPath; // Contains the number of possible actions taken at each step
    private Stack<StateInterface<TermType, ?>> statePath; // Contains the state at each step
    private Stack<Collection<TermType>> perceptPath; // Contains the percepts that would be seen at each step
    private int actionPathHash = -1; // Hashes the action path to give a unique identifier to the path taken
    private int previousActionPathHash = -1; // Hashes the previous action path to assist with backtracking

    public Model() {
        this.actionPath = new Stack<JointMove<TermType>>();
        this.getNumberOfPossibleActionsPath = new Stack<Integer>();
        this.statePath = new Stack<StateInterface<TermType, ?>>();
        this.perceptPath = new Stack<Collection<TermType>>();
    }
    public Model(Model<TermType> model) {
        this.actionPath = (Stack<JointMove<TermType>>)model.getActionPath().clone();
        this.getNumberOfPossibleActionsPath = (Stack<Integer>)model.getNumberOfPossibleActionsPath().clone();
        this.statePath = (Stack<StateInterface<TermType, ?>>)model.getStatePath().clone();
        this.perceptPath = (Stack<Collection<TermType>>)model.getPerceptPath().clone();
        this.actionPathHash = model.getActionPathHash();
        this.previousActionPathHash = model.getPreviousActionPathHash();
    }

    // Getters for the state of the model
    public Stack<JointMove<TermType>> getActionPath() { return this.actionPath; }
    public Stack<Integer> getNumberOfPossibleActionsPath() { return this.getNumberOfPossibleActionsPath; }
    public Stack<StateInterface<TermType, ?>> getStatePath() { return this.statePath; }
    public Stack<Collection<TermType>> getPerceptPath() { return this.perceptPath; }
    public int getActionPathHash() { return this.actionPathHash; }
    public int getPreviousActionPathHash() { return this.previousActionPathHash; }
    public JointMove<TermType> getLastAction() { return this.actionPath.peek(); }
    public StateInterface<TermType, ?> getCurrentState() {
        return this.statePath.peek();
    }
    public Collection<TermType> getLatestExpectedPercepts() { return this.perceptPath.peek(); }
    public int getNumberOfPossibleActions() {
        int total = 1;
        for(int num : this.getNumberOfPossibleActionsPath) {
            total *= num;
        }
        return total;
    }

    /**
     * Updates the model by getting the successor of the current state according to the joint action chosen. If there is
     * no joint action chosen, then assume it is the first state and set the inital percepts to the initial percepts seen
     * Note: If it tries to add to the state in an existing place, then the program exits
     *
     * @param stepNum - The number of steps into the game the model is
     * @param initialPercepts - The inital percepts seen
     * @param jointAction - The joint action taken to advance the state
     * @param currState - The current state of the model
     * @param role - The role of the player (used to find what percepts the player would see upon a state update)
     * @param numPossibleJointMoves - The number of possible joint moves at the current state
     */
    public void updateGameplayTracker(int stepNum, Collection<TermType> initialPercepts, JointMove<TermType> jointAction, StateInterface<TermType, ?> currState, RoleInterface<TermType> role, int numPossibleJointMoves) {
        if(this.actionPath.size() > stepNum) {
            System.err.println("Key already contained");
            System.err.println("Actions Path: " + this.actionPath);
            System.exit(0);
        }
        else {
            // Calculate next state from joint action, state pair
            StateInterface<TermType, ?> newState = null;
            Collection<TermType> expectedPercepts = null;
            if(jointAction != null) {
                newState = currState.getSuccessor(jointAction);
                expectedPercepts = currState.getSeesTerms(role, jointAction);
            } else {
                newState = currState;
                expectedPercepts = initialPercepts;
            }

            // Add all to the action pairs
            this.actionPath.push(jointAction);
            this.getNumberOfPossibleActionsPath.push(numPossibleJointMoves);
            this.statePath.push(newState);
            this.perceptPath.push(expectedPercepts);
            this.previousActionPathHash = this.actionPathHash;
            this.actionPathHash = this.actionPath.hashCode();
        }
    }

    /**
     * Backtracks the model by removing the latest state, action and percepts from the stack and updating the hashes
     *
     */
    public void backtrack() {
        this.actionPath.pop();
        this.getNumberOfPossibleActionsPath.pop();
        this.statePath.pop();
        this.perceptPath.pop();
        this.actionPathHash = this.actionPath.hashCode();
        if(!this.actionPath.isEmpty()) {
            this.previousActionPathHash = this.actionPath.subList(0, this.actionPath.size() - 1).hashCode();
        }
    }

    /**
     * Computes all moves that are legal for the player's role in the current state
     *
     * @param role
     * @return
     */
    public Collection<? extends MoveInterface<TermType>> computeLegalMoves(RoleInterface<TermType> role) {
        // Get current state
        StateInterface<TermType, ?> state = getCurrentState();

        // Compute legal moves
        Collection<? extends MoveInterface<TermType>> stateLegalMoves = state.getLegalMoves(role);

        Collection<? extends MoveInterface<TermType>> legalMoves = null;
        legalMoves = new HashSet<MoveInterface<TermType>>(stateLegalMoves);
        legalMoves.retainAll(stateLegalMoves);

        // Return legal moves
        return legalMoves;
    }
}
