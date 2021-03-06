/*
    Copyright (C) 2008 Stephan Schiffel <stephan.schiffel@gmx.de>
                  2010 Nicolas JEAN <njean42@gmail.com>

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

package tud.gamecontroller.players;

import tud.gamecontroller.game.StateInterface;
import tud.gamecontroller.players.StateVarianceNoBiasHyperPlayer.StateVarianceNoBiasHyperPlayer;
import tud.gamecontroller.players.StateVarianceNoBiasHyperPlayer.StateVarianceNoBiasHyperPlayerInfo;
import tud.gamecontroller.players.OPLikelihoodStateVarianceHyperPlayer.OPLikelihoodStateVarianceHyperPlayer;
import tud.gamecontroller.players.OPLikelihoodStateVarianceHyperPlayer.OPLikelihoodStateVarianceHyperPlayerInfo;
import tud.gamecontroller.players.OPExpansionAnytimeHyperPlayer.OPExpansionAnytimeHyperPlayer;
import tud.gamecontroller.players.OPExpansionAnytimeHyperPlayer.OPExpansionAnytimeHyperPlayerInfo;
import tud.gamecontroller.players.OPLikelihoodAnytimeHyperPlayer.OPLikelihoodAnytimeHyperPlayer;
import tud.gamecontroller.players.OPLikelihoodAnytimeHyperPlayer.OPLikelihoodAnytimeHyperPlayerInfo;
import tud.gamecontroller.players.OPStateVarianceHyperPlayer.OPStateVarianceHyperPlayer;
import tud.gamecontroller.players.OPStateVarianceHyperPlayer.OPStateVarianceHyperPlayerInfo;
import tud.gamecontroller.players.StateVarianceHyperPlayer.StateVarianceHyperPlayer;
import tud.gamecontroller.players.StateVarianceHyperPlayer.StateVarianceHyperPlayerInfo;
import tud.gamecontroller.players.AnytimeHyperPlayer.AnytimeHyperPlayer;
import tud.gamecontroller.players.AnytimeHyperPlayer.AnytimeHyperPlayerInfo;
import tud.gamecontroller.players.AnytimeHyperPlayerLikelihoodTree.AnytimeHyperPlayerLikelihoodTree;
import tud.gamecontroller.players.AnytimeHyperPlayerLikelihoodTree.AnytimeHyperPlayerLikelihoodTreeInfo;
import tud.gamecontroller.players.CheatHyperPlayer.CheatHyperPlayer;
import tud.gamecontroller.players.CheatHyperPlayer.CheatHyperPlayerInfo;
import tud.gamecontroller.players.HyperPlayer.HyperPlayer;
import tud.gamecontroller.players.HyperPlayer.HyperPlayerInfo;
import tud.gamecontroller.players.ImprovedRandomPlayer.ImprovedRandomPlayer;
import tud.gamecontroller.players.ImprovedRandomPlayer.ImprovedRandomPlayerInfo;
import tud.gamecontroller.players.MCSPlayer.MCSPlayer;
import tud.gamecontroller.players.MCSPlayer.MCSPlayerInfo;
import tud.gamecontroller.players.OPAnytimeHyperPlayer.OPAnytimeHyperPlayer;
import tud.gamecontroller.players.OPAnytimeHyperPlayer.OPAnytimeHyperPlayerInfo;
import tud.gamecontroller.players.OPBiasAnytimeHyperPlayer.OPBiasAnytimeHyperPlayer;
import tud.gamecontroller.players.OPBiasAnytimeHyperPlayer.OPBiasAnytimeHyperPlayerInfo;
import tud.gamecontroller.players.OPVarianceHyperPlayer.OPVarianceHyperPlayer;
import tud.gamecontroller.players.OPVarianceHyperPlayer.OPVarianceHyperPlayerInfo;
import tud.gamecontroller.players.VarianceHyperPlayer.VarianceHyperPlayer;
import tud.gamecontroller.players.VarianceHyperPlayer.VarianceHyperPlayerInfo;
import tud.gamecontroller.players.XXXXPlayer.XXXXPlayer;
import tud.gamecontroller.players.XXXXPlayer.XXXXPlayerInfo;
import tud.gamecontroller.scrambling.GameScramblerInterface;
import tud.gamecontroller.term.TermInterface;

public class PlayerFactory {

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
		Player<TermType, StateType> createRemotePlayer(RemotePlayerInfo info, GameScramblerInterface gameScrambler) {
		return new RemotePlayer<TermType, StateType>(info.getName(), info.getHost(), info.getPort(), info.getGdlVersion(), gameScrambler);
	}
	
	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
		Player<TermType, StateType> createRandomPlayer(RandomPlayerInfo info) {
		return new RandomPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}
	
	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
		Player<TermType, StateType> createLegalPlayer(LegalPlayerInfo info) {
		return new LegalPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}
	
	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
		Player<TermType, StateType> createHumanPlayer(HumanPlayerInfo info) {
		return new HumanPlayer<TermType, StateType>(info.getName());
	}

	// ADDED
	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createXXXXPlayer(XXXXPlayerInfo info) {
		return new XXXXPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createMCSPlayer(MCSPlayerInfo info) {
		return new MCSPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createHyperPlayer(HyperPlayerInfo info) {
		return new HyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createAnytimeHyperPlayer(AnytimeHyperPlayerInfo info) {
		return new AnytimeHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createImprovedRandomPlayer(ImprovedRandomPlayerInfo info) {
		return new ImprovedRandomPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createAnytimeHyperPlayerLikelihoodTree(AnytimeHyperPlayerLikelihoodTreeInfo info) {
		return new AnytimeHyperPlayerLikelihoodTree<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createCheatPlayer(CheatHyperPlayerInfo info) {
		return new CheatHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPAnytimeHyperPlayer(OPAnytimeHyperPlayerInfo info) {
		return new OPAnytimeHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPBiasAnytimeHyperPlayer(OPBiasAnytimeHyperPlayerInfo info) {
		return new OPBiasAnytimeHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createVarianceHyperPlayer(VarianceHyperPlayerInfo info) {
		return new VarianceHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPVarianceHyperPlayer(OPVarianceHyperPlayerInfo info) {
		return new OPVarianceHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createStateVarianceHyperPlayerInfo(StateVarianceHyperPlayerInfo info) {
		return new StateVarianceHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPStateVarianceHyperPlayerInfo(OPStateVarianceHyperPlayerInfo info) {
		return new OPStateVarianceHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPLikelihoodAnytimeHyperPlayerInfo(OPLikelihoodAnytimeHyperPlayerInfo info) {
		return new OPLikelihoodAnytimeHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPExpansionAnytimeHyperPlayerInfo(OPExpansionAnytimeHyperPlayerInfo info) {
		return new OPExpansionAnytimeHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createOPLikelihoodStateVarianceHyperPlayerInfo(OPLikelihoodStateVarianceHyperPlayerInfo info) {
		return new OPLikelihoodStateVarianceHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
	Player<TermType, StateType> createStateVarianceNoBiasHyperPlayerInfo(StateVarianceNoBiasHyperPlayerInfo info) {
		return new StateVarianceNoBiasHyperPlayer<TermType, StateType>(info.getName(), info.getGdlVersion());
	}

	public static <TermType extends TermInterface, StateType extends StateInterface<TermType, ? extends StateType>>
		Player<TermType, StateType> createPlayer(PlayerInfo info, GameScramblerInterface gameScrambler) {
		if(info instanceof RemotePlayerInfo){
			return PlayerFactory. <TermType, StateType> createRemotePlayer((RemotePlayerInfo)info, gameScrambler);
		}else if(info instanceof RandomPlayerInfo){
			return PlayerFactory. <TermType, StateType> createRandomPlayer((RandomPlayerInfo)info);
		}else if(info instanceof LegalPlayerInfo){
			return PlayerFactory. <TermType, StateType> createLegalPlayer((LegalPlayerInfo)info);
		}else if(info instanceof HumanPlayerInfo){
			return PlayerFactory. <TermType, StateType> createHumanPlayer((HumanPlayerInfo)info);
		}
		else if(info instanceof XXXXPlayerInfo){ // ADDED
			return PlayerFactory. <TermType, StateType> createXXXXPlayer((XXXXPlayerInfo)info);
		}
		else if(info instanceof MCSPlayerInfo){
			return PlayerFactory. <TermType, StateType> createMCSPlayer((MCSPlayerInfo)info);
		}
		else if(info instanceof HyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createHyperPlayer((HyperPlayerInfo)info);
		}
		else if(info instanceof AnytimeHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createAnytimeHyperPlayer((AnytimeHyperPlayerInfo)info);
		}
		else if(info instanceof ImprovedRandomPlayerInfo){
			return PlayerFactory. <TermType, StateType> createImprovedRandomPlayer((ImprovedRandomPlayerInfo)info);
		}
		else if(info instanceof AnytimeHyperPlayerLikelihoodTreeInfo){
			return PlayerFactory. <TermType, StateType> createAnytimeHyperPlayerLikelihoodTree((AnytimeHyperPlayerLikelihoodTreeInfo)info);
		}
		else if(info instanceof CheatHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createCheatPlayer((CheatHyperPlayerInfo)info);
		}
		else if(info instanceof OPAnytimeHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPAnytimeHyperPlayer((OPAnytimeHyperPlayerInfo)info);
		}
		else if(info instanceof OPBiasAnytimeHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPBiasAnytimeHyperPlayer((OPBiasAnytimeHyperPlayerInfo)info);
		}
		else if(info instanceof VarianceHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createVarianceHyperPlayer((VarianceHyperPlayerInfo)info);
		}
		else if(info instanceof OPVarianceHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPVarianceHyperPlayer((OPVarianceHyperPlayerInfo)info);
		}
		else if(info instanceof StateVarianceHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createStateVarianceHyperPlayerInfo((StateVarianceHyperPlayerInfo)info);
		}else if(info instanceof OPStateVarianceHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPStateVarianceHyperPlayerInfo((OPStateVarianceHyperPlayerInfo)info);
		}else if(info instanceof OPLikelihoodAnytimeHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPLikelihoodAnytimeHyperPlayerInfo((OPLikelihoodAnytimeHyperPlayerInfo)info);
		}else if(info instanceof OPExpansionAnytimeHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPExpansionAnytimeHyperPlayerInfo((OPExpansionAnytimeHyperPlayerInfo)info);
		}else if(info instanceof OPLikelihoodStateVarianceHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createOPLikelihoodStateVarianceHyperPlayerInfo((OPLikelihoodStateVarianceHyperPlayerInfo)info);
		}else if(info instanceof StateVarianceNoBiasHyperPlayerInfo){
			return PlayerFactory. <TermType, StateType> createStateVarianceNoBiasHyperPlayerInfo((StateVarianceNoBiasHyperPlayerInfo)info);
		}
		return null;
	}


}
