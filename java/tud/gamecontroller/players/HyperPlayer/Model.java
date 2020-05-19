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

import tud.gamecontroller.game.JointMoveInterface;
import tud.gamecontroller.game.MoveInterface;
import tud.gamecontroller.game.RoleInterface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import tud.gamecontroller.game.StateInterface;
import tud.gamecontroller.game.impl.JointMove;
import tud.gamecontroller.term.TermInterface;

/**
 * Holds a model of how the true state of the game may look given the percepts seen so far
 *
 * @author Michael Dorrell
 * @version 1.0
 * @since 1.0
 */
public class Model<TermType extends TermInterface> implements Cloneable{

    private LikelihoodTracker likelihoodTracker; // Tracks the likelihood of the model representing the true state
    private ArrayList<JointMove<TermType>> actionPath;
    private ArrayList<Integer> getNumberOfPossibleActionsPath;
    private ArrayList<StateInterface<TermType, ?>> statePath;
    private ArrayList<Collection<TermType>> perceptPath;
    private int actionPathHash = -1;
    private int previousActionPathHash = -1;
    private boolean isInitalModel;

    public Model() {
        this.likelihoodTracker = new LikelihoodTracker();
        this.actionPath = new ArrayList<JointMove<TermType>>();
        this.getNumberOfPossibleActionsPath = new ArrayList<Integer>();
        this.statePath = new ArrayList<StateInterface<TermType, ?>>();
        this.perceptPath = new ArrayList<Collection<TermType>>();
        this.isInitalModel = true;
    }
    public Model(Model<TermType> model) {
        this.likelihoodTracker = new LikelihoodTracker(model.getLikelihoodTracker());
        this.actionPath = new ArrayList<JointMove<TermType>>(model.getActionPath());
        this.getNumberOfPossibleActionsPath = new ArrayList<Integer>(model.getNumberOfPossibleActionsPath());
        this.statePath = new ArrayList<StateInterface<TermType, ?>>(model.getStatePath());
        this.perceptPath = new ArrayList<Collection<TermType>>(model.getPerceptPath());
        this.actionPathHash = model.getActionPathHash();
        this.previousActionPathHash = model.getPreviousActionPathHash();
    }

    public LikelihoodTracker getLikelihoodTracker() { return this.likelihoodTracker; }
    public ArrayList<JointMove<TermType>> getActionPath() { return this.actionPath; }
    public ArrayList<Integer> getNumberOfPossibleActionsPath() { return this.getNumberOfPossibleActionsPath; }
    public ArrayList<StateInterface<TermType, ?>> getStatePath() { return this.statePath; }
    public ArrayList<Collection<TermType>> getPerceptPath() { return this.perceptPath; }
    public boolean isInitalModel() { return this.isInitalModel; }
    public int getActionPathHash() { return this.actionPathHash; }
    public int getPreviousActionPathHash() { return this.previousActionPathHash; }
    public JointMove<TermType> getLastAction() { return this.actionPath.get(this.actionPath.size() - 1); }
    public int getNumberOfPossibleActions() {
        int total = 0;
        for(int num : this.getNumberOfPossibleActionsPath) {
            total += num;
        }
        return total;
    }


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
            this.actionPath.add(jointAction);
            this.getNumberOfPossibleActionsPath.add(numPossibleJointMoves);
            this.statePath.add(newState);
            this.perceptPath.add(expectedPercepts);
            this.previousActionPathHash = this.actionPathHash;
            this.actionPathHash = this.actionPath.hashCode();
            this.isInitalModel = false;
        }
    }

    /**
     * Backtracks the model by removing the latest state
     *
     */
    public void backtrack() {
        this.actionPath.remove(this.actionPath.size() - 1);
        this.getNumberOfPossibleActionsPath.remove(this.getNumberOfPossibleActionsPath.size() - 1);
        this.statePath.remove(this.statePath.size() - 1);
        this.perceptPath.remove(this.perceptPath.size() - 1);
        this.actionPathHash = this.actionPath.hashCode();
        if(this.actionPath.size() > 0) {
            this.previousActionPathHash = this.actionPath.subList(0, this.actionPath.size() - 1).hashCode();
        } else {
            this.isInitalModel = true;
        }
    }

    public StateInterface<TermType, ?> getCurrentState() {
        return this.statePath.get(this.statePath.size() - 1);
    }

    public Collection<TermType> getLatestExpectedPercepts() {
        return this.perceptPath.get(this.perceptPath.size() - 1);
    }

    /**
     * @return moves that are legal in all of the current possible states
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
