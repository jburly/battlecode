package the_Porcelain_Champions;

import java.math.*;
import java.util.Vector;
import battlecode.common.*;
import static battlecode.common.GameConstants.*;

public class RobotPlayer implements Runnable {
	private final RobotController rc;

	// Strings for the various robot behaviors.
	private static final String ATTACK_BEHAVIOR = "attack";
	private static final String SPAWN_BEHAVIOR = "spawn";
	private static final String SPAWN_SCOUT_BEHAVIOR = "spawnScout";
	private static final String SPAWN_MORTAR_BEHAVIOR = "spawnMortar";
	private static final String DEFENSE_BEHAVIOR = "defense";
	private static final String IDLE_BEHAVIOR = "idle";
	private static final String NO_BEHAVIOR = "nothing";
	
	// labels to indicate where a message originated from
	private static final String LEADER_MSG_LABEL = "leaderMsg";
	private static final String SCOUT_FOUND_LOC = "scoutTowerLocation";
	
	// indices in the message string array where particular data is stored
	private static final int MSG_LABEL_INDEX = 0;
	private static final int BEHAVIOR_INDEX = 1;
	private static final int GOAL_DIRECTION_INDEX = 2;
	
	// indices in the message int array where particular data is stored
	private static final int ARCHON_ID_INDEX = 0;
	private static final int MORTAR_ID_INDEX = 1;
	private static final int SCOUT_ID_INDEX = 2;
	
	// indices in the message location array where particular data is stored
	private static final int ARCHON_LOC_INDEX = 0;
	private static final int ARCHON_TARGET_INDEX = 1;
	private static final int SCOUT_TARGET_INDEX = 0;
	
	// the amount of time to wait before attacking at the beginning of the game
	private static final int ATTACK_TIME = 170;
	
	// the distance to an adjacent, diagonal square rounded up
	private static final double MAX_DIST_TO_ADJ_LOC = 1.75;
	
	// the percentage of maximum health that marks the point at which robots
	// need to be healed
	private static final double LOW_HEALTH_MULTIPLIER = .4;
	private static final double MORTAR_LOW_HEALTH_MULTIPLIER = .8;

	// unchanging variables for any robot
	// so I am not always calling the robot controller for these
	private Team myTeam;
	private RobotType myType;

	//location where an archon can spawn at the time
	private MapLocation spawnLocation = null; 
	
	// a non-archon bot's leader, i.e. the archon a mortar/scout should follow
	private Robot fighterMom = null; 
	private int following = 0;// the ID of the robot you're following

	private Message leaderMsg = null; // message from your leader

	private boolean working;// if the archon is already doing something

	public RobotPlayer(RobotController rC) {
		rc = rC;

		// since these variables rarely change, store them in a variable, so we
		// do not have to repeatedly call upon these methods.
		myTeam = rc.getTeam();
		myType = rc.getRobotType();
	}

	public void run() {
		while (true) {
			try {
				/** * beginning of main loop ** */
				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				switch (myType) {

				// we only use archons, scouts, soldiers, and mortars
				// according to a robot's type, perform certain actions
				case ARCHON:
					archon();
					break;
				case SCOUT:
					scout();
					break;
				case SOLDIER:
					soldier();
					break;
				case MORTAR:
					mortar();
					break;
				}
				rc.yield();
				/** * end of main loop ** */
			} catch (Exception e) {
				System.out.println("caught exception:");
				e.printStackTrace();
			}
		}
	}

	/***************************************************************************
	 * Movement Methods
	 **************************************************************************/

	// whether or not the bot is currently tracing around an obstacle
	private boolean isTracing = false;

	/**
	 * The movement method used by all robots. Takes a direction and tries to
	 * move towards it utilizing a basic bug algorithm with simplified
	 * implementation of 'tracing.'
	 * 
	 * @param goalDir
	 *            the direction of the goal that the robot is pursuing
	 */
	private void hunt(Direction goalDir) {
		try {
			// make sure that an actual direction was passed as an argument
			if (goalDir != Direction.NONE && goalDir != null) {

				// if you aren't tracing, move in the direction of the target
				if (!isTracing) {
					if (rc.getDirection() != goalDir)
						rc.setDirection(goalDir);
					else if (rc.canMove(goalDir))
						rc.moveForward();
					else {
						isTracing = true;
					}

					// otherwise trace around the obstacle until you can move
					// towards your goal again
				} else {
					if (rc.canMove(goalDir)) {
						isTracing = false;
						rc.setDirection(goalDir);
					} else if (rc.canMove(rc.getDirection()))
						rc.moveForward();
					else
						rc.setDirection(rc.getDirection().rotateRight());
				}
			}
			rc.yield();
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
	}

	/**
	 * Calculates the direction from a robot's current location to the desired
	 * location by comparing coordinates.
	 * 
	 * @param goal
	 *            The location which a robot wants to move to.
	 * @return The direction for the robots current location to its goal.
	 */
	private Direction calcDirection(MapLocation goal) {

		// the current robot's coordinates on the map
		int currX = rc.getLocation().getX();
		int currY = rc.getLocation().getY();

		if (goal != null) {

			// compare the value of the coordinates to determine which direction
			// the robot must face to reach its goal
			if (currX < goal.getX()) {
				if (currY < goal.getY())
					return Direction.SOUTH_EAST;
				else if (currY > goal.getY())
					return Direction.NORTH_EAST;
				else
					return Direction.EAST;
			} else if (currX > goal.getX()) {
				if (currY < goal.getY())
					return Direction.SOUTH_WEST;
				else if (currY > goal.getY())
					return Direction.NORTH_WEST;
				else
					return Direction.WEST;
			} else if (currY < goal.getY())
				return Direction.SOUTH;
			else if (currY > goal.getY())
				return Direction.NORTH;
		}

		// unless the goal was null, this should never be the case
		return Direction.NONE;

	}

	/**
	 * Calculates the distance from the robot's location to a targeted location.
	 * 
	 * @return The distance from the robot's current location to the desired
	 *         location.
	 */
	private double distanceFrom(MapLocation target) {
		int xDiff = Math.abs(rc.getLocation().getX() - target.getX());
		int yDiff = Math.abs(rc.getLocation().getY() - target.getY());

		return Math.hypot(xDiff, yDiff);
	}

	/**
	 * Calculates the distance from one location to another location.
	 * 
	 * @return The distance from one location to another.
	 */
	private double distanceFrom(MapLocation location, MapLocation target) {
		int xDiff = Math.abs(location.getX() - target.getX());
		int yDiff = Math.abs(location.getY() - target.getY());

		return Math.hypot(xDiff, yDiff);
	}

	/***************************************************************************
	 * SEARCH METHODS
	 **************************************************************************/

	/**
	 * Locates an enemy or neutral tower if one exist within the robot's sensor
	 * range.
	 * 
	 * @return A neutral or enemy tower within the robot's sensor range.
	 */
	private Robot searchForTower() {
		try {
			// since a tower is a ground robot,
			// sense for all ground robots within range
			Robot nearbyBots[] = rc.senseNearbyGroundRobots();

			for (int i = 0; i < nearbyBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearbyBots[i]);

				// find the first non-ally tower and return it
				if (botInfo.team != rc.getTeam()
						&& botInfo.type == RobotType.TOWER)
					return nearbyBots[i];
			}
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}

		// if no tower was found, return null
		return null;
	}

	/**
	 * Searches for the nearest allied tower.
	 * 
	 * @return the MapLocation of the nearest allied tower.
	 */
	private MapLocation nearestAlliedTower() {

		// the location of the closest allied tower
		MapLocation closestTower = null;

		try {
			// array containin the locations of all allied towers
			MapLocation[] allyTowers = rc.senseAlliedTowers();

			// search amongst all allied towers to find the closest one
			for (int i = 0; i < allyTowers.length; i++) {
				if (closestTower == null
						|| distanceFrom(closestTower) > distanceFrom(allyTowers[i]))
					closestTower = allyTowers[i];
			}

		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}

		// return the closest tower, returns null if none are found
		return closestTower;
	}

	/**
	 * Checks whether or not a tower at a given location is an ally or not.
	 * Important to stop mortars and other robots from attacking a tower after
	 * it has been captured.
	 * 
	 * @param loc
	 *            The location where one should check whether or not the tower
	 *            there is an ally.
	 * @return Whether or not the tower at a given location is an ally.
	 */
	public boolean isAlliedTower(MapLocation loc) {
		try {

			// array containing the locations of all allied towers
			MapLocation[] allyTowers = rc.senseAlliedTowers();

			// make sure an actual location was given
			if (loc != null) {
				for (int i = 0; i < allyTowers.length; i++) {

					// check the locations of all allied towers to see if one
					// exists at the given location
					if (loc.equals(allyTowers[i])) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * For spawned soldiers to find out who spawned them. They will designate
	 * the one that is closest to them as leader.
	 * 
	 * @return The archon closest to them.
	 */
	// just to make sure the archon does not change every call
	private Robot closestArchon = null;

	private Robot findClosestArchon() {
		try {
			
			// locations of all the allied archons
			MapLocation[] archonLocs = rc.senseAlliedArchons();
		
			MapLocation archonLoc = null; //location of the closest archon
			
			// find the closest archon
			for (int i = 0; i < archonLocs.length; i++) {
				if (archonLoc == null 
						|| distanceFrom(archonLoc) > distanceFrom(archonLocs[i]))
					archonLoc = archonLocs[i];
			}
			
			// with the closest archon found, try and sense him, if you cannot
			// turn to face the archon, your senses will probably pick him up then
			if (archonLoc != null) {
				Direction archonDir = calcDirection(archonLoc);
				if (rc.getDirection() == archonDir
						&& rc.canSenseSquare(archonLoc)) {
					closestArchon = rc.senseGroundRobotAtLocation(archonLoc);
				} else if (!rc.isMovementActive()){
					rc.setDirection(archonDir);
				}
			}
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
		
		return closestArchon;
	}

	/**
	 * Find the message from this robot's leader amongst all the messages received.
	 * 
	 * @param incomingMsgs an array of messages
	 * @return the message from this robot's leader
	 */
	private Message findLeaderMessage(Message[] incomingMsgs) {
		
		Message leaderMsg = null; // the leader's message
		
		try {
			
			// parse messages
			if (incomingMsgs != null) {
				for (int i = 0; i < incomingMsgs.length; i++) {
				
					// checks if it's a message from the leader
					if (incomingMsgs[i].ints != null
							&& incomingMsgs[i].ints[ARCHON_ID_INDEX] == following){
						leaderMsg = incomingMsgs[i];
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		
		return leaderMsg;
	}

	/**
	 * Find a robot with the specified ID#.
	 * 
	 * @param ID a robot's ID#
	 * @return the robot with the specified ID#
	 */
	public Robot findRobotWithID(int ID) {
		try {
			
			Robot[] nearGroundBots = rc.senseNearbyGroundRobots();

			// try and match the specified ID# with the ID#s of all the robots
			// within this robot's sensor range
			for (int i = 0; i < nearGroundBots.length; i++) {
				if (nearGroundBots[i].getID() == ID)
					return nearGroundBots[i];
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		
		return null;
	}

	/***************************************************************************
	 * BOT ROUTINES
	 **************************************************************************/

	/**
	 * A Scout's routines.
	 * 
	 * Primary purpose is to scout out enemy robots and all non-allied towers, as 
	 * well as help the archon it follows heal the mortar. 
	 */
	private void scout() {
		
		// the mortar that a scout is to help take care of
		Robot myMortar = null;
		
		while (true) {
			try {
				
				// if already doing something, yield
				while (rc.isMovementActive() || rc.isAttackActive()) {
					rc.yield();
				}

				// start by retrieving all the messages
				Message[] incomingMsgs = rc.getAllMessages();

				// if the scout retrieves messages but does not have an assigned archon
				// or mortar yet
				if ((fighterMom == null || myMortar == null) && incomingMsgs != null) {
						for (int i = 0; i < incomingMsgs.length; i++) {
							if (incomingMsgs[i].ints != null
									&& incomingMsgs[i].ints[SCOUT_ID_INDEX] == rc.getRobot()
											.getID()) {
								following = incomingMsgs[i].ints[ARCHON_ID_INDEX];
								fighterMom = findRobotWithID(following);
								
								// accompanying an archon's ID# should be a mortar's
								// as well
								if (incomingMsgs[i].ints[ARCHON_ID_INDEX] != 0)
									myMortar = findRobotWithID(incomingMsgs[i].
											ints[MORTAR_ID_INDEX]);
							}
						}
				} else {
					
					// before healing the mortar, make sure the scout has enough
					// energon for itself
					if (rc.getEnergonLevel() > rc.getMaxEnergonLevel()
							* LOW_HEALTH_MULTIPLIER
							&& rc.canSenseObject(myMortar)) {
						
						// the location of the mortar
						MapLocation mortarLoc = rc.senseRobotInfo(myMortar).location;
						
						// make sure the mortar is within range to be healed
						if (distanceFrom(mortarLoc) < MAX_DIST_TO_ADJ_LOC)
							scoutHeal(myMortar);
						
						// if it isn't, move closer to the mortar
						else
							hunt(calcDirection(mortarLoc));
					
					// if the scout does not have enough energon, have it move close 
					// to its archon so it can be given more energon
					} else {
						if (rc.canSenseObject(fighterMom)) {
							MapLocation archonLoc = rc
									.senseRobotInfo(fighterMom).location;
							if (distanceFrom(archonLoc) > MAX_DIST_TO_ADJ_LOC)
								hunt(calcDirection(archonLoc));
						}
					}
				}

				// after managing energon, sense if any enemy or 
				// neutral towers are nearby
				Robot target = searchForTower();
				
				// if a tower is found create a message and broadcast the details
				// about this tower
				if (target != null) {
					
					// create a new string
					Message m = new Message();
					m.strings = new String[1];
					m.locations = new MapLocation[1];
					
					// indicate that it this tower was spotted by a scout
					m.strings[MSG_LABEL_INDEX] = SCOUT_FOUND_LOC;
					
					// add the tower's location to the message and broadcast it
					m.locations[SCOUT_TARGET_INDEX] = rc.senseRobotInfo(target).location;
					rc.broadcast(m);
				}

				// set the one of the scouts indicator strings to display the 
				// ID# of the archon he is following, and another to display the 
				// ID# of its mortar.
				rc.setIndicatorString(0, following + " ");
				if (myMortar != null)
					rc.setIndicatorString(1, myMortar.getID() + " ");

				rc.yield();
			} catch (Exception e) {
				System.out.println("caught exception");
				e.printStackTrace();
			}
		}
	}

	/**
	 * A soldier's routines.
	 * 
	 * Soldiers exist long enough to evolve into mortars.
	 */
	private void soldier() {
		try {
			
			// top priority is figuring out who to follow
			// this should only be done once
			if (fighterMom == null){
				findMotherBot();
			}else {
				
				// make 100% sure we are dealing with a soldier and that the
				// soldier has enough energon to evolve
				if (rc.getRobotType().compareTo(RobotType.SOLDIER) == 0
						&& rc.getEventualEnergonLevel() > GameConstants.EVOLVE_COST) {
					rc.yield();
					rc.evolve(RobotType.MORTAR);
					myType = RobotType.MORTAR;
				}
			}
			rc.yield();
		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
	}

	/**
	 * A mortar's routines.
	 * 
	 * The mortars purpose is to attack.  It is our only offensive unit.
	 */
	private void mortar() {
		while (true) {
			try {
				// if already performing an action, yield
				while (rc.isMovementActive() || rc.isAttackActive()) {
					rc.yield();
				}

				// grab all broadcasted messages
				Message[] incomingMsgs = rc.getAllMessages();

				// find the message from your archon leader
				if (fighterMom != null)
					leaderMsg = findLeaderMessage(incomingMsgs);
				
				// if for some reason you do not already have a leader, find one ASAP
				else
					findMotherBot();

				// if you have a message, that means you have a leader, so start working
				if (leaderMsg != null) {
					
					// move away from the enemy if low on energon, so you don't get
					// killed to quickly (I realize these layered if statements are odd
					// but hell broke loose when I tried to get rid of them)
					if (rc.getEventualEnergonLevel() <= rc.getMaxEnergonLevel()
							* LOW_HEALTH_MULTIPLIER) {
						if (leaderMsg.locations[ARCHON_TARGET_INDEX] != null) {
							if (rc.canAttackSquare(leaderMsg.locations[ARCHON_TARGET_INDEX])
									&& rc.canMove(rc.getDirection().opposite())){
								rc.moveBackward();
							}
						}
						
					// if not low on health, attack and defend 
					} else if (leaderMsg.strings[BEHAVIOR_INDEX].
								equalsIgnoreCase(ATTACK_BEHAVIOR)
							|| leaderMsg.strings[BEHAVIOR_INDEX].
								equalsIgnoreCase(DEFENSE_BEHAVIOR)) {
						
						// if someone else has located a target move in and attack it
						if (leaderMsg.locations[ARCHON_TARGET_INDEX] != null) {
							if (rc.canAttackSquare(leaderMsg.
									locations[ARCHON_TARGET_INDEX])) {
								
								rc.attackGround(leaderMsg.locations[ARCHON_TARGET_INDEX]);
								
							} else {
								Direction goalDir = calcDirection(leaderMsg.
										locations[ARCHON_TARGET_INDEX]);
								
								hunt(goalDir);
							}
							
						// if no attack target, move in the direction specified by the leader	
						} else {
							Direction goalDir = msgDirection(leaderMsg.
									strings[GOAL_DIRECTION_INDEX]);
							
							if (goalDir != null)
								hunt(goalDir);
						}
					}
				}
				
				rc.yield();
			} catch (Exception e) {
				System.out.println("caught exception:");
				e.printStackTrace();
			}
		}
	}

	/**
	 * An Archon's routines.
	 * 
	 * An archon is supposed to heal, sense targets when able, but most importantly,
	 * provide frequent communication with other allied bots.
	 */
	private void archon() {
		
		MapLocation goalLoc = null; // location of the current target
		boolean startOfGame = true; // indicative that the game just started
		
		// an archons followers, i.e. 1 mortar and 1 scout
		Robot myMortar = null;
		Robot myScout = null;

		// whether or not the archon is currently defending against enemy robots
		boolean inDefense = false;

		while (true)
			try {
				String behavior = NO_BEHAVIOR; // start with no behavior
				
				// location of tower targeted by another allied archon
				MapLocation alliedLeaderTower = null;

				// check whether or not the archon is in the midst
				// of performing an action
				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				Message[] incomingMsgs = rc.getAllMessages();
				
				if (incomingMsgs != null) {
					
					// search through all the messages being broadcast
					for (int i = 0; i < incomingMsgs.length; i++) {
						
						// find out what allied archons are doing
						if (incomingMsgs[i].strings != null){
							if (incomingMsgs[i].strings[MSG_LABEL_INDEX].
									equals(LEADER_MSG_LABEL)
								&& incomingMsgs[i].strings[BEHAVIOR_INDEX].
									equals(ATTACK_BEHAVIOR)
							    && incomingMsgs[i].locations[ARCHON_TARGET_INDEX] != null) {
								
								alliedLeaderTower = incomingMsgs[i].
									locations[ARCHON_TARGET_INDEX];
								
							// if allied archons aren't doing anything, check to see if
							// your scout has discovered something
							} else if (incomingMsgs[i].strings[MSG_LABEL_INDEX]
										.equals(SCOUT_FOUND_LOC)){
								
								alliedLeaderTower = incomingMsgs[i].
												locations[SCOUT_TARGET_INDEX];
								
							}
						}
					}
				}

				// prepare your own message
				Message msg = new Message();
				msg.strings = new String[3];
				msg.locations = new MapLocation[2];
				msg.ints = new int[3];

				// let people know, who and where you are, what you are doing
				// and who is accompanying you
				msg.strings[MSG_LABEL_INDEX] = LEADER_MSG_LABEL;
				msg.strings[BEHAVIOR_INDEX] = IDLE_BEHAVIOR;
				msg.locations[ARCHON_LOC_INDEX] = rc.getLocation();
				msg.ints[ARCHON_ID_INDEX] = rc.getRobot().getID();
				if (myMortar != null)
					msg.ints[MORTAR_ID_INDEX] = myMortar.getID();
				if (myScout != null)
					msg.ints[SCOUT_ID_INDEX] = myScout.getID();

				// at the start of the game, wait for mortars to evolve, and everyone
				// to get healed before initiated an assault
				if (startOfGame == true) {
					msg.strings[BEHAVIOR_INDEX] = IDLE_BEHAVIOR;
					int time = Clock.getRoundNum();
					if (time < RobotType.MORTAR.wakeDelay())
						behavior = SPAWN_BEHAVIOR;
					if (time >= ATTACK_TIME) {
						behavior = ATTACK_BEHAVIOR;
					}
				}

				// keep healing the scout as much as possible so it can heal the mortar
				if (myScout != null && rc.canSenseObject(myScout)) {
					RobotInfo scoutInfo = rc.senseRobotInfo(myScout);
					if (distanceFrom(scoutInfo.location) < MAX_DIST_TO_ADJ_LOC) {
						archonHeal(myScout);
					}
				
				// means the scout doesn't exist so spawn one if able
				} else if (rc.canSpawn())
					behavior = SPAWN_SCOUT_BEHAVIOR;

				// if there is no scout, try healing your mortar
				if (myMortar != null && rc.canSenseObject(myMortar)) {
					RobotInfo mortarInfo = rc.senseRobotInfo(myMortar);
					if (distanceFrom(mortarInfo.location) < MAX_DIST_TO_ADJ_LOC) {
						archonHeal(myMortar);
					} else if (!working && mortarInfo.eventualEnergon 
								<= mortarInfo.maxEnergon * MORTAR_LOW_HEALTH_MULTIPLIER){
					
							hunt(calcDirection(mortarInfo.location));
							working = true;
					}
				
				// if you are not trying to spawn a scout, and you cannot heal a mortar,
				// you need a mortar, so spawn one
				} else if (!behavior.equals(SPAWN_SCOUT_BEHAVIOR))
					behavior = SPAWN_MORTAR_BEHAVIOR;

				// try spawning a mortar
				if (behavior.equals(SPAWN_MORTAR_BEHAVIOR)) {
					
					MapLocation spawnLoc = spaceToSpawn(); // location to spawn a mortar
					
					// to get a mortar, spawn a soldier if able
					// the soldier will evolve after being healed
					if (rc.canSpawn()) {
						if (spawnLoc != rc.getLocation()
								&& rc.getEnergonLevel() > 2 * RobotType.SOLDIER
										.spawnCost()) {
							
							Direction spawnDir = calcDirection(spawnLoc);
							
							if (spawnDir == rc.getDirection()) {
								rc.spawn(RobotType.SOLDIER);
								spawnLocation = rc.getLocation().add(
										rc.getDirection());
								rc.yield();
								myMortar = rc
										.senseGroundRobotAtLocation(spawnLocation);
							
							// face the direction of the spawn location if you aren't
							// already
							} else if (!working)
								rc.setDirection(spawnDir);
						}
						
					// if you cannot spawn, move to the nearest allied tower to do so
					} else if (!working && nearestAlliedTower() != null)
						hunt(calcDirection(nearestAlliedTower()));
				}

				// try to spawn a scout
				if (behavior.equals(SPAWN_SCOUT_BEHAVIOR)) {
					
					MapLocation spawnLoc = airSpaceToSpawn(); // space to spawn an air unit
					
					// if able to spawn, do so
					if (rc.canSpawn()) {
						if (spawnLoc != rc.getLocation()
								&& rc.getEnergonLevel() > 2 * RobotType.SOLDIER
										.spawnCost()) {
							
							Direction spawnDir = calcDirection(spawnLoc);
							
							if (spawnDir == rc.getDirection()) {
								rc.spawn(RobotType.SCOUT);
								spawnLocation = rc.getLocation().add(
										rc.getDirection());
								rc.yield();
								myScout = rc
										.senseAirRobotAtLocation(spawnLocation);
								
							// face the direction of the spawn location if you aren't
							// already	
							} else if (!working)
								rc.setDirection(spawnDir);
						}
					}
				}
				
				// if in defense mode, since your target is an enemy robot, it is 
				// location and existence may change the next turn, so at this turns
				// end the target is no longer valid
				if (inDefense) {
					goalLoc = null;
					inDefense = false; // may no longer be enemy robots in range
				}
				
				// if attacking 
				if (behavior.equals(ATTACK_BEHAVIOR)) {
					
					// tell your other bots to exhibit the same behavior
					msg.strings[BEHAVIOR_INDEX] = ATTACK_BEHAVIOR;
					
					Direction goalDir = null;
					
					// if someone else hasn't located a target, search for yourself
					if (goalLoc == null) {
						
						//search for enemies in range
						Robot target = targetEnemyGBot();
						
						// if no enemy robots are in range, find a tower
						if (target == null) {
							target = searchForTower();
							
						// else, prepare to fend off enemy robots	
						} else {
							msg.strings[BEHAVIOR_INDEX] = "DEFENSE";
							inDefense = true;
						}
						
						// if a target is found, pass along its info
						if (target != null) {
							RobotInfo targetInfo = rc.senseRobotInfo(target);
							if (targetInfo.team != myTeam) {
								goalLoc = targetInfo.location;
							}
						}
					}

					// if an ally has found a tower, and you haven't
					// join them in an attack
					if (goalLoc == null && alliedLeaderTower != null)
						goalLoc = alliedLeaderTower;

					// check to make sure it has not already been captured
					if (isAlliedTower(goalLoc))
						goalLoc = null;

					// if you still don't have the location of a target, 
					// move in the direction of the closest unknown tower
					if (goalLoc == null) {
						goalDir = rc.senseClosestUnknownTower();
						
					// if you have the location of a target, move towards it	
					} else
						goalDir = calcDirection(goalLoc);
					
					// let others know your direction and target location
					msg.strings[GOAL_DIRECTION_INDEX] = goalDir.toString();
					msg.locations[ARCHON_TARGET_INDEX] = goalLoc;

					// if you can still perform an action, haven't already located a
					// nearby target, move towards your goal direction if you have one
					if (!working && msg.locations[ARCHON_TARGET_INDEX] == null 
							&& goalDir != null)
						hunt(goalDir);

				}

				// display your behavior and broadcast your message
				rc.setIndicatorString(0, behavior);
				rc.broadcast(msg);
				rc.yield();

			} catch (Exception e) {
				System.out.println("caught exception:");
				e.printStackTrace();
			}
	}

	
	/***************************************************************************
	 * Supporting methods for robot routines.
	 **************************************************************************/

	
	/**
	 * Method for the archon to heal a specified bot to the best of its ability.
	 */
	private void archonHeal(Robot hurtBot) {
		try {
			RobotInfo hurtBotInfo = rc.senseRobotInfo(hurtBot);
			if (rc.getEnergonLevel() > RobotType.ARCHON.maxEnergon() / 8
					&& hurtBotInfo.eventualEnergon < hurtBotInfo.type
							.maxEnergon()) {

				double energonNeeded = hurtBotInfo.maxEnergon
						- hurtBotInfo.eventualEnergon;
				
				// if possible, heal hurt bot completely, else contribute as much
				// energon as possible without committing suicide (hence subtracting 
				// the archon's energon upkeep)
				double transferAmount = Math.min(energonNeeded, rc
						.getEnergonLevel()
						- RobotType.ARCHON.energonUpkeep());
				rc.transferEnergon(transferAmount, hurtBotInfo.location,
						hurtBot.getRobotLevel());

			}

		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
	}

	/**
	 * Method for scouts to transfer energon to other robots with low energon.
	 * 
	 * @param hurtBot robot with low energon that the scout should heal
	 */
	private void scoutHeal(Robot hurtBot) {
		try {
			RobotInfo hurtBotInfo = rc.senseRobotInfo(hurtBot);
			if (rc.getEnergonLevel() > RobotType.SCOUT.maxEnergon() / 8
					&& hurtBotInfo.eventualEnergon < hurtBotInfo.type
							.maxEnergon()) {

				double energonNeeded = hurtBotInfo.maxEnergon
						- hurtBotInfo.eventualEnergon;
				// if possible, heal hurt bot completely, else contribute all
				// possible
				// without committing suicide (hence subtracting the archon's
				// energon upkeep)
				double transferAmount = Math.min(energonNeeded, rc
						.getEnergonLevel() * MORTAR_LOW_HEALTH_MULTIPLIER);
				rc.transferEnergon(transferAmount, hurtBotInfo.location,
						hurtBot.getRobotLevel());
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
	}

	/**
	 * Returns an adjacent MapLocation, which an Archon is able to spawn a
	 * ground unit into.
	 * 
	 * @return MapLocation available to spawn ground robots into.
	 */
	private MapLocation spaceToSpawn() {
		try {

			MapLocation currLoc = rc.getLocation(); // current location of archon

			// the following if statements checks all the adjacent MapLocations
			// and checks whether or not they are empty, based upon the robots
			// ability to move into it.
			if (rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH)) == null
					&& rc.canMove(Direction.NORTH))
				return currLoc.add(Direction.NORTH);

			else if (rc
					.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH)) == null
					&& rc.canMove(Direction.SOUTH))
				return currLoc.add(Direction.SOUTH);

			else if (rc.senseGroundRobotAtLocation(currLoc.add(Direction.EAST)) == null
					&& rc.canMove(Direction.EAST))
				return currLoc.add(Direction.EAST);

			else if (rc.senseGroundRobotAtLocation(currLoc.add(Direction.WEST)) == null
					&& rc.canMove(Direction.WEST))
				return currLoc.add(Direction.WEST);

			else if (rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.NORTH_EAST)) == null
					&& rc.canMove(Direction.NORTH_EAST))
				return currLoc.add(Direction.NORTH_EAST);

			else if (rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.NORTH_WEST)) == null
					&& rc.canMove(Direction.NORTH_WEST))
				return currLoc.add(Direction.NORTH_WEST);

			else if (rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.SOUTH_EAST)) == null
					&& rc.canMove(Direction.SOUTH_EAST))
				return currLoc.add(Direction.SOUTH_EAST);

			else if (rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.SOUTH_WEST)) == null
					&& rc.canMove(Direction.SOUTH_WEST))
				return currLoc.add(Direction.SOUTH_WEST);
			
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}

		// if there is not an empty adjacent MapLocation, return null
		return null;
	}

	/**
	 * Returns an adjacent MapLocation, which an Archon is able to spawn an air
	 * unit into.
	 * 
	 * @return MapLocation available to spawn air robots into.
	 */
	private MapLocation airSpaceToSpawn() {
		try {
			
			MapLocation currLoc = rc.getLocation(); // current location of archon

			// the following if statements checks all the adjacent MapLocations
			// to see if there is already an air robot occupying it.
			if (rc.senseAirRobotAtLocation(currLoc.add(Direction.NORTH)) == null)
				return currLoc.add(Direction.NORTH);

			else if (rc.senseAirRobotAtLocation(currLoc.add(Direction.SOUTH)) == null)
				return currLoc.add(Direction.SOUTH);

			else if (rc.senseAirRobotAtLocation(currLoc.add(Direction.EAST)) == null)
				return currLoc.add(Direction.EAST);

			else if (rc.senseAirRobotAtLocation(currLoc.add(Direction.WEST)) == null)
				return currLoc.add(Direction.WEST);

			else if (rc.senseAirRobotAtLocation(currLoc
					.add(Direction.NORTH_EAST)) == null)
				return currLoc.add(Direction.NORTH_EAST);

			else if (rc.senseAirRobotAtLocation(currLoc
					.add(Direction.NORTH_WEST)) == null)
				return currLoc.add(Direction.NORTH_WEST);

			else if (rc.senseAirRobotAtLocation(currLoc
					.add(Direction.SOUTH_EAST)) == null)
				return currLoc.add(Direction.SOUTH_EAST);

			else if (rc.senseAirRobotAtLocation(currLoc
					.add(Direction.SOUTH_WEST)) == null)
				return currLoc.add(Direction.SOUTH_WEST);
			
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}

		// if there is not an empty adjacent MapLocation, return null
		return null;
	}

	/**
	 * Method for non-Archon robots to search for the Archon which spawned them.
	 * Specifically looks for the Archon closest to the robot.
	 */
	private void findMotherBot() {
		try {
			fighterMom = findClosestArchon(); // the closest archon

			closestArchon = null;

			// this should not be null
			if (fighterMom != null) {
				following = fighterMom.getID();
				rc.setIndicatorString(0, "" + following);
			}

			// if it is, you are a bastard child with no use, so take an extreme
			// utilitarian approach
			// else
			// rc.suicide();
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
	}

	/**
	 * Takes a string and returns a direction based on the given string.
	 * 
	 * @return the direction specified by the given string.
	 */
	private Direction msgDirection(String msgDir) {
		try {
			
			// compares the string to the direction in string format
			if (msgDir.equalsIgnoreCase(Direction.NORTH.toString()))
				return Direction.NORTH;
			if (msgDir.equalsIgnoreCase(Direction.SOUTH.toString()))
				return Direction.SOUTH;
			if (msgDir.equalsIgnoreCase(Direction.EAST.toString()))
				return Direction.EAST;
			if (msgDir.equalsIgnoreCase(Direction.WEST.toString()))
				return Direction.WEST;
			if (msgDir.equalsIgnoreCase(Direction.NORTH_EAST.toString()))
				return Direction.NORTH_EAST;
			if (msgDir.equalsIgnoreCase(Direction.NORTH_WEST.toString()))
				return Direction.NORTH_WEST;
			if (msgDir.equalsIgnoreCase(Direction.SOUTH_EAST.toString()))
				return Direction.SOUTH_EAST;
			if (msgDir.equalsIgnoreCase(Direction.SOUTH_WEST.toString()))
				return Direction.SOUTH_WEST;
			
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		
		// if the string did not match a direction, return null
		return null;
	}

	/**
	 * Searches for nearby enemy robots within the attack range of a Mortar.
	 * 
	 * @return an enemy bot within that vicinity, null otherwise
	 */
	private Robot targetEnemyGBot() {
		
		Robot enemyBot = null;
		
		try {
			Robot[] nearGBots = rc.senseNearbyGroundRobots();

			// search for an enemy bot, or tower
			for (int i = 0; i < nearGBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGBots[i]);
				
				if (botInfo.team != myTeam) {
					
					//attack enemy towers first as they will give us the upperhand later
					if (botInfo.type == RobotType.TOWER) {
						return nearGBots[i];
						
					// otherwise find any robot within a mortars attack range
					} else if (enemyBot == null
							|| (distanceFrom(rc.senseRobotInfo(enemyBot).location) > Math
									.sqrt(RobotType.MORTAR
											.attackRadiusMinSquared()) && distanceFrom(rc
									.senseRobotInfo(enemyBot).location) < Math
									.sqrt(RobotType.MORTAR
											.attackRadiusMaxSquared()))) {
						enemyBot = nearGBots[i];
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		
		return enemyBot;
	}
}