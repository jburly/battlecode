package team000;

import java.math.*;
import java.util.Vector;
import battlecode.common.*;
import static battlecode.common.GameConstants.*;

public class RobotPlayer implements Runnable {
	private final RobotController rc;

	// Individual Squadron Unit Numbers for Archons
	private int archonNum, scoutNum, bomberNum, soldierNum, mortarNum,
			sniperNum = 0;

	private final static int MIN_SOLDIER_NUM = 4;
	private final static int MIN_MORTAR_NUM = 3;

	private final static int ARCHONS_PER_TEAM = 4;
	private final static int MAX_DIST_FROM_LEADER = 8;

	private final static String ATTACK_STRING = "attack";
	private final static String SPAWN_STRING = "spawn";

	private final static double CRIT_HEALTH_MULTIPLIER = .2;
	private final static double LOW_HEALTH_MULTIPLIER = .4;

	// whether or not the team has any air units
	// initially, we have none
	private boolean haveAirUnits = false;

	// unchanging variables for any robot
	// so I am not always calling the robot controller for these

	private Team myTeam;
	private RobotType myType;

	private int following = 0;// the ID of the robot you're following
	// private Message[] incomingMsgs; //all the broadcasted messages in range
	private Message leaderMsg = null; // message from the leader
	private int squadID = 0;

	private boolean working;// if the archon is already doing something

	public RobotPlayer(RobotController rC) {
		rc = rC;
		myTeam = rc.getTeam();
		myType = rc.getRobotType();
	}

	public void run() {
		while (true) {
			try {
				// myType = rc.getRobotType();
				/** * beginning of main loop ** */
				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				switch (myType) {
				case ARCHON:
					archon();
					break;
				case SCOUT:
					scout();
					break;
				case BOMBER:
					bomber();
					break;
				case SOLDIER:
					soldier();
					break;
				case MORTAR:
					mortar();
					break;
				case SNIPER:
					sniper();
					break;
				case TOWER:
					tower();
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
	boolean isTracing = false;
	MapLocation traceStart = null;
	// main movement method (uses bug algorithm)
	// takes in a direction and tries to move towards it
	private void hunt(Direction goalDir) {
		try {
			if (goalDir != Direction.NONE && goalDir != null) {
				// if you aren't tracing, move in the direction of the target
				if (!isTracing) {
					if (rc.getDirection() != goalDir)
						rc.setDirection(goalDir);
					else if (rc.canMove(goalDir))
						rc.moveForward();
					else{
						isTracing = true;
						traceStart = rc.getLocation();
					}
				} else {
					if (rc.canMove(goalDir) && calcDirection(traceStart) != goalDir) {
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

	/*
	 * Judges the direction one should travel to reach the goal. Compares
	 * current position with position of the target to calculate.
	 */
	private Direction calcDirection(MapLocation goal) {
		int currX = rc.getLocation().getX();
		int currY = rc.getLocation().getY();

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
		else
			return Direction.NONE;

	}

	/*
	 * Return the distance from the current bot to a targeted location.
	 */
	private double distanceFrom(MapLocation target) {
		int xDiff = Math.abs(rc.getLocation().getX() - target.getX());
		int yDiff = Math.abs(rc.getLocation().getY() - target.getY());

		return Math.hypot(xDiff, yDiff);
	}

	/*
	 * Return the distance from one location to target location.
	 */
	private double distanceFrom(MapLocation location, MapLocation target) {
		int xDiff = Math.abs(location.getX() - target.getX());
		int yDiff = Math.abs(location.getY() - target.getY());

		return Math.hypot(xDiff, yDiff);
	}

	/*
	 * Designates a target for the bots to hunt. Specifically looks for towers
	 * and if none are near, defaults movement to direction of closest unknown
	 * tower.
	 */
	/*
	 * private void designateTarget(Direction goalDir, RobotInfo targetInfo){
	 * try{ //try to search for a tower in sensor range Robot target =
	 * searchForTower();
	 * 
	 * if(target != null){ goalDir =
	 * calcDirection(rc.senseRobotInfo(target).location); targetInfo =
	 * rc.senseRobotInfo(target); } else goalDir =
	 * rc.senseClosestUnknownTower();
	 * 
	 * }catch(Exception e){ System.out.println("Caught Exception:");
	 * e.printStackTrace(); } }
	 */

	/*
	 * Just a quick check to see if a target has been designated.
	 */
	/*
	 * private boolean haveTarget(){ return targetInfo != null && target !=
	 * null; }
	 */

	/***************************************************************************
	 * SEARCH METHODS
	 **************************************************************************/

	/*
	 * Finds a tower if it is in the sensor range of the robot.
	 */
	private Robot searchForTower() {
		try {
			Robot nearbyBots[] = rc.senseNearbyGroundRobots();

			for (int i = 0; i < nearbyBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearbyBots[i]);
				if (botInfo.team != rc.getTeam()
						&& botInfo.type == RobotType.TOWER)
					return nearbyBots[i];
			}
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
		return null;
	}

	// searches for the nearest allied tower
	private MapLocation nearestAlliedTower() {
		// the closest allied tower
		MapLocation closestTower = null;

		try {
			MapLocation[] allyTowers = rc.senseAlliedTowers();

			for (int i = 0; i < allyTowers.length; i++) {
				if (closestTower == null
						|| distanceFrom(closestTower) > distanceFrom(allyTowers[i]))
					closestTower = allyTowers[i];
			}

		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
		return closestTower;
	}

	// sees if a tower at a maplocation is allied or not
	public boolean isAlliedTower(MapLocation m) {
		try {
			MapLocation[] allyTowers = rc.senseAlliedTowers();
			if (m != null)
				for (int i = 0; i < allyTowers.length; i++)
					if (m.equals(allyTowers[i]))
						return true;

		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * Checks to see if there are any air enemies in the vicinity.
	 * 
	 * Note: Only returns one bot. May want this to return all bots, or just
	 * make it a boolean statement indicating that air bots are close.
	 */
	private Robot searchForAirEnemy() {
		try {
			Robot nearAirBots[] = rc.senseNearbyAirRobots();

			for (int i = 0; i < nearAirBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
				if (botInfo.team != rc.getTeam()
						&& botInfo.type == RobotType.BOMBER
						|| botInfo.type == RobotType.SCOUT)
					return nearAirBots[i];
			}
		} catch (Exception e) {
			System.out.println("Caught Exception");
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * Finds the weakest ally in range.For archons to know who to heal.
	 */
	private Robot[] findCritHealthAllies() {
		Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
		Vector<Robot> weakestBots = new Vector<Robot>(2, 1);
		try {
			// search for ground bots no matter what, they should exist
			for (int i = 0; i < nearGroundBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGroundBots[i]);
				if (botInfo.team == rc.getTeam()
						&& !(botInfo.type == RobotType.ARCHON)) {
					if (botInfo.eventualEnergon <= botInfo.maxEnergon
							* CRIT_HEALTH_MULTIPLIER) {
						weakestBots.add(nearGroundBots[i]);
					}
				}
			}
			// air bots might not exist so...
			// might want to make this conditional on whether we still need
			// air bots or not -- no sense healing what we don't need
			if (haveAirUnits) {
				Robot[] nearAirBots = rc.senseNearbyAirRobots();
				for (int i = 0; i < nearAirBots.length; i++) {
					RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
					if (botInfo.team == rc.getTeam()
							&& !(botInfo.type == RobotType.ARCHON)) {
						if (botInfo.eventualEnergon <= botInfo.maxEnergon
								* CRIT_HEALTH_MULTIPLIER) {
							weakestBots.add(nearGroundBots[i]);
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
		Robot[] a = new Robot[0];
		return weakestBots.toArray(a);
	}

	/*
	 * Finds the weakest ally in range.For archons to know who to heal.
	 */
	private Robot[] findLowHealthAllies() {
		Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
		Vector<Robot> weakestBots = new Vector<Robot>(3, 1);
		try {
			// search for ground bots no matter what, they should exist
			for (int i = 0; i < nearGroundBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGroundBots[i]);
				if (botInfo.team == rc.getTeam()
						&& !(botInfo.type == RobotType.ARCHON)) {
					if (botInfo.eventualEnergon > botInfo.maxEnergon
							* CRIT_HEALTH_MULTIPLIER
							&& botInfo.eventualEnergon <= botInfo.maxEnergon
									* LOW_HEALTH_MULTIPLIER) {
						weakestBots.add(nearGroundBots[i]);
					}
				}
			}
			// air bots might not exist so...
			// might want to make this conditional on whether we still need
			// air bots or not -- no sense healing what we don't need
			if (haveAirUnits) {
				Robot[] nearAirBots = rc.senseNearbyAirRobots();
				for (int i = 0; i < nearAirBots.length; i++) {
					RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
					if (botInfo.team == rc.getTeam()
							&& !(botInfo.type == RobotType.ARCHON)) {
						if (botInfo.eventualEnergon > botInfo.maxEnergon
								* CRIT_HEALTH_MULTIPLIER
								&& botInfo.eventualEnergon <= botInfo.maxEnergon
										* LOW_HEALTH_MULTIPLIER) {
							weakestBots.add(nearGroundBots[i]);
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
		Robot[] a = new Robot[0];
		return weakestBots.toArray(a);
	}

	private Robot findWeakestAdjacentBot() {
		Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
		Robot weakestBot = null;
		try {
			// search for ground bots no matter what, they should exist
			for (int i = 0; i < nearGroundBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGroundBots[i]);

				// if it is adjacent
				if (botInfo.team == myTeam
						&& botInfo.type != RobotType.ARCHON
						&& distanceFrom(botInfo.location, rc.getLocation()) <= 1) {
					if (weakestBot == null)
						weakestBot = nearGroundBots[i];
					else if (rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
						weakestBot = nearGroundBots[i];
				}
			}

			// air bots might not exist so...
			// might want to make this conditional on whether we still need
			// air bots or not -- no sense healing what we don't need
			if (haveAirUnits) {
				Robot[] nearAirBots = rc.senseNearbyAirRobots();
				for (int i = 0; i < nearAirBots.length; i++) {
					RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);

					// if it is adjacent
					if (distanceFrom(botInfo.location, rc.getLocation()) <= 1) {
						if (botInfo.team == myTeam) {
							if (weakestBot == null)
								weakestBot = nearGroundBots[i];
							else if (rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
								weakestBot = nearGroundBots[i];
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
		return weakestBot;
	}

	/*
	 * For spawned soldiers to find out who spawned them. They will designate
	 * the one that is closest to them as leader.
	 */
	// just to make sure the archon does not change every call
	private Robot closestArchon = null;

	private Robot findClosestArchon() {
		// Robot closestArchon = null;
		try {
			MapLocation[] archonLocs = rc.senseAlliedArchons();
			MapLocation archonLoc = null;
			for (int i = 0; i < archonLocs.length; i++) {
				if (archonLoc == null)
					archonLoc = archonLocs[i];
				else if (distanceFrom(archonLoc) > distanceFrom(archonLocs[i]))
					archonLoc = archonLocs[i];
			}
			if (archonLoc != null) {
				Direction archonDir = calcDirection(archonLoc);
				if (rc.getDirection() == archonDir
						&& rc.canSenseSquare(archonLoc)) {
					closestArchon = rc.senseGroundRobotAtLocation(archonLoc);
				} else if (!rc.isMovementActive())
					rc.setDirection(archonDir);
			}

		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
		return closestArchon;
	}

	private Message findLeaderMessage(Message[] incomingMsgs) {
		Message leaderMsg = null;
		try {
			// parse messages

			if (incomingMsgs != null) {
				for (int i = 0; i < incomingMsgs.length; i++) {
					// checks if it's a message from the leader
					if (incomingMsgs[i].ints != null
							&& incomingMsgs[i].ints[0] == following)
						leaderMsg = incomingMsgs[i];
				}
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		return leaderMsg;
	}

	public Robot findRobotWithID(int ID) {
		try {
			Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
			// search for ground bots no matter what, they should exist
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

	private void tower() {
		while (true) {
			try {
				System.out.println("I am a tower, yo ho ho");
			} catch (Exception e) {
				System.out.println("caught exception");
				e.printStackTrace();
			}
		}
	}

	// non-archon bot's leader
	private Robot fighterMom = null; // the archon the soldier should follow

	private void scout() {
		Robot myMortar = null;
		while (true) {
			try {
				while (rc.isMovementActive() || rc.isAttackActive()) {
					rc.yield();
				}

				Message[] incomingMsgs = rc.getAllMessages();

				if (fighterMom == null || myMortar == null) {
					if (incomingMsgs != null)
						for (int i = 0; i < incomingMsgs.length; i++) {
							if (incomingMsgs[i].ints != null
									&& incomingMsgs[i].ints[2] == rc.getRobot()
											.getID()) {
								following = incomingMsgs[i].ints[0];
								fighterMom = findRobotWithID(following);
								if (incomingMsgs[i].ints[0] != 0)
									myMortar = findRobotWithID(incomingMsgs[i].ints[1]);
							}
						}
					/*
					 * if(fighterMom == null || myMortar == null){ Message m =
					 * new Message(); m.strings = new String[1]; m.strings[0] =
					 * "needMom"; rc.broadcast(m); }
					 */
				} else {

					if (rc.getEnergonLevel() > rc.getMaxEnergonLevel()
							* LOW_HEALTH_MULTIPLIER
							&& rc.canSenseObject(myMortar)) {
						MapLocation mortarLoc = rc.senseRobotInfo(myMortar).location;
						if (distanceFrom(mortarLoc) < 1.75)
							scoutHeal(myMortar);
						else
							hunt(calcDirection(mortarLoc));
					} else {
						if (rc.canSenseObject(fighterMom)) {
							MapLocation archonLoc = rc
									.senseRobotInfo(fighterMom).location;
							if (distanceFrom(archonLoc) > 1.75)
								hunt(calcDirection(archonLoc));
						}
					}
				}

				Robot target = searchForTower();
				if (target != null) {
					Message m = new Message();
					m.strings = new String[1];
					m.strings[0] = "scoutTowerLocation";
					m.locations = new MapLocation[1];

					m.locations[0] = rc.senseRobotInfo(target).location;
					rc.broadcast(m);
				}

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

	private void bomber() {
		try {/*
				 * if(haveTarget()) if(rc.canAttackSquare(targetInfo.location) &&
				 * targetInfo.team != myTeam)
				 * rc.attackGround(targetInfo.location); hunt();
				 */
		} catch (Exception e) {
			System.out.println("caught exception");
			e.printStackTrace();
		}
	}

	private void soldier() {
		try {
			// get all messages
			Message[] incomingMsgs = rc.getAllMessages();

			// top priority is figuring out who to follow
			// this should only be done once
			if (fighterMom == null)
				findMotherBot();

			// look for mom's message
			else {
				leaderMsg = findLeaderMessage(incomingMsgs);

				if (rc.getRobotType().compareTo(RobotType.SOLDIER) == 0
						&& rc.getEventualEnergonLevel() >= rc
								.getMaxEnergonLevel() * 3 / 4) {
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

	// mortar variable
	// private boolean movedBack = false;
	private void mortar() {
		while (true) {
			try {
				while (rc.isMovementActive() || rc.isAttackActive()) {
					rc.yield();
				}
				// while (rc.getEventualEnergonLevel() < rc.getMaxEnergonLevel()
				// * CRIT_HEALTH_MULTIPLIER)
				// rc.yield();

				Message[] incomingMsgs = rc.getAllMessages();

				if (fighterMom != null)
					leaderMsg = findLeaderMessage(incomingMsgs);
				else
					findMotherBot();

				if (leaderMsg != null) {
					if (rc.getEventualEnergonLevel() <= rc.getMaxEnergonLevel()
							* LOW_HEALTH_MULTIPLIER) {
						if (leaderMsg.locations[1] != null) {
							if (rc.canAttackSquare(leaderMsg.locations[1])
									&& rc.canMove(rc.getDirection().opposite()))
								rc.moveBackward();
						}
					} else if (leaderMsg.strings[1].equalsIgnoreCase("attack")
							|| leaderMsg.strings[1].equalsIgnoreCase("DEFENSE")) {
						if (leaderMsg.locations[1] != null) {
							if (rc.canAttackSquare(leaderMsg.locations[1])) {
								rc.attackGround(leaderMsg.locations[1]);
							} else {
								Direction goalDir = calcDirection(leaderMsg.locations[1]);
								hunt(goalDir);
							}
						} else {
							Direction goalDir = msgDirection(leaderMsg.strings[2]);
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

	private void sniper() {
		try {

		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
	}

	// archon variables
	private boolean leader = false;

	private void archon() {
		MapLocation goalLoc = null;
		boolean startOfGame = true;
		Robot myMortar = null;
		Robot myScout = null;

		boolean inDefense = false;

		while (true)
			try {
				String behavior = "nothing";
				MapLocation alliedLeaderTower = null;

				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				Message[] incomingMsgs = rc.getAllMessages();
				if (incomingMsgs != null) {
					for (int i = 0; i < incomingMsgs.length; i++) {
						if (incomingMsgs[i].strings != null
								&& incomingMsgs[i].strings[0]
										.equals("leaderMsg")) {
							if (incomingMsgs[i].strings[1]
									.equals(ATTACK_STRING)
									&& incomingMsgs[i].locations[1] != null) {
								alliedLeaderTower = incomingMsgs[i].locations[1];
							}
						} else if (incomingMsgs[i].strings != null
								&& incomingMsgs[i].strings[0]
										.equals("scoutTowerLocation"))
							alliedLeaderTower = incomingMsgs[i].locations[0];
					}
				}

				// prepare the message
				Message msg = new Message();
				msg.strings = new String[3];
				msg.locations = new MapLocation[2];
				msg.ints = new int[3];

				// you're the leader, add the location to the broadcast
				msg.strings[0] = "leaderMsg";
				msg.strings[1] = "idle";
				msg.locations[0] = rc.getLocation();
				msg.ints[0] = rc.getRobot().getID();
				if (myMortar != null)
					msg.ints[1] = myMortar.getID();
				if (myScout != null)
					msg.ints[2] = myScout.getID();

				if (startOfGame == true) {
					msg.strings[1] = "idle";
					int time = Clock.getRoundNum();
					if (time < 50)
						behavior = SPAWN_STRING;
					if (time >= 170) {
						behavior = ATTACK_STRING;
					}
				}

				if (myScout != null && rc.canSenseObject(myScout)) {
					RobotInfo scoutInfo = rc.senseRobotInfo(myScout);
					if (distanceFrom(scoutInfo.location) < 1.75) {
						archonHeal(myScout);
					}
				} else if (rc.canSpawn())
					behavior = "spawnScout";

				if (myMortar != null && rc.canSenseObject(myMortar)) {
					RobotInfo mortarInfo = rc.senseRobotInfo(myMortar);
					if (distanceFrom(mortarInfo.location) < 1.75) {
						archonHeal(myMortar);
					} else if (mortarInfo.eventualEnergon <= mortarInfo.maxEnergon * .8)
						if (!working) {
							hunt(calcDirection(mortarInfo.location));
							working = true;
						}
				} else if (!behavior.equals("spawnScout"))
					behavior = "spawnMortar";

				if (behavior.equals("spawnMortar")) {
					MapLocation spawnLoc = spaceToSpawn();
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
							} else if (!working)
								rc.setDirection(spawnDir);
						}
					} else if (!working && nearestAlliedTower() != null)
						hunt(calcDirection(nearestAlliedTower()));
				}

				if (behavior.equals("spawnScout")) {
					MapLocation spawnLoc = airSpaceToSpawn();
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
							} else if (!working)
								rc.setDirection(spawnDir);
						}
					}
				}

				if (inDefense) {
					goalLoc = null;
					inDefense = false;
				}
				if (behavior.equals(ATTACK_STRING)) {
					msg.strings[1] = ATTACK_STRING;
					Direction goalDir = null;
					// if the leader has no idea where the goal is located
					// then let's try to search for a tower ourselves
					if (goalLoc == null) {
						Robot target = targetEnemyGBot();
						if (target == null) {
							target = searchForTower();
						} else {
							msg.strings[1] = "DEFENSE";
							inDefense = true;
						}
						// if successful, pass on the info
						if (target != null) {
							RobotInfo targetInfo = rc.senseRobotInfo(target);
							// msg.ints[1] = rc.getRobot().getID();
							// isFinder = true;
							if (targetInfo.team != myTeam) {
								goalLoc = targetInfo.location;
							}
						}
					}

					if (goalLoc == null && alliedLeaderTower != null)
						goalLoc = alliedLeaderTower;

					// check for allied tower
					if (isAlliedTower(goalLoc))
						goalLoc = null;
					// broadcast that

					if (goalLoc == null) {
						goalDir = rc.senseClosestUnknownTower();
					} else
						goalDir = calcDirection(goalLoc);
					msg.locations[1] = goalLoc;
					msg.strings[2] = goalDir.toString();

					// if nobody senses the tower, move towards it
					if (!working && msg.locations[1] == null && goalDir != null)
						hunt(goalDir);

				}

				rc.setIndicatorString(0, behavior);
				rc.broadcast(msg);
				rc.yield();

			} catch (Exception e) {
				System.out.println("caught exception:");
				e.printStackTrace();
			}
	}

	/***************************************************************************
	 * ADDITIONAL ARCHON ROUTINES
	 **************************************************************************/
	private void designateLeader() {
		Message establishLeaderMsg = null;
		try {
			Message[] incomingMsgs = rc.getAllMessages();
			// Message establishLeaderMsg = null;
			// parse messages
			if (incomingMsgs != null) {
				// currentLeaderNum = incomingMsgs.ints[3];
				for (int i = 0; i < incomingMsgs.length; i++) {
					// checks if it's an establish leader message
					if (incomingMsgs[i].strings[0].equals("establishLeader"))
						establishLeaderMsg = incomingMsgs[i];
				}
			}

			// initially establish leadership
			// other archons decide to follow the leader
			if (establishLeaderMsg != null && leader == false && following == 0
					&& establishLeaderMsg.ints[0] < ARCHONS_PER_TEAM) {
				following = establishLeaderMsg.ints[1];
				squadID = establishLeaderMsg.ints[5];

				// this particular leader has a new follower, so update
				// count of bots within squad
				establishLeaderMsg.ints[0]++;
				// and add your ID to the pile
				establishLeaderMsg.ints[establishLeaderMsg.ints[0]] = rc
						.getRobot().getID();

				// broadcast the updated message
				rc.broadcast(establishLeaderMsg);
			} else {
				if (leader == false && following == 0) {
					// the first robot assumes leadership and communicates his
					// ID
					leader = true;
					// make a new message saying
					// "hey I'm chief, who wants to follow me?"
					Message msg = new Message();
					msg.strings = new String[1];
					msg.strings[0] = "establishLeader";
					msg.ints = new int[6];
					msg.ints[1] = rc.getRobot().getID(); // leader ID
					msg.ints[0] = 1; // number of bots in the unit so far
					if (establishLeaderMsg != null)
						msg.ints[5] = 2;
					else
						msg.ints[5] = 1; // the number of the squad
					squadID = msg.ints[5];
					rc.broadcast(msg);
				}
			}
			rc.yield();
		} catch (Exception e) {
			System.out.println("Caught Exception:");
			e.printStackTrace();
		}
	}

	// archon leader action routines
	private void archonLeader() {

		boolean startOfGame = true;
		int[] commanders = new int[ARCHONS_PER_TEAM - 1];

		while (true) {
			try {

				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				// prioritize healing bots in adjacent squares
				Robot weakAdjAlly = findWeakestAdjacentBot();
				if (weakAdjAlly != null && needsHealing(weakAdjAlly))
					archonHeal(weakAdjAlly);

				MapLocation commanderTargetLocation = null;

				// parse messages
				Message[] incomingMsgs = rc.getAllMessages();
				if (incomingMsgs != null) {
					for (int i = 0; i < incomingMsgs.length; i++) {
						// checks for the "commanders"
						if (incomingMsgs[i].strings[0]
								.equals("establishLeader")
								&& incomingMsgs[i].ints[1] == rc.getRobot()
										.getID())
							for (int j = 2; j <= ARCHONS_PER_TEAM; j++) {
								commanders[j - 2] = incomingMsgs[i].ints[j];
							}
						else {
							if (incomingMsgs[i].strings[0].equals("leaderMsg")
									&& incomingMsgs[i].strings[1]
											.equals(ATTACK_STRING)
									&& isCommander(commanders,
											incomingMsgs[i].ints[0])
									&& incomingMsgs[i].locations[1] != null) {
								commanderTargetLocation = incomingMsgs[i].locations[1];
							}
						}
					}
				}

				// prepare the message
				Message msg = new Message();
				msg.strings = new String[3];
				msg.locations = new MapLocation[2];
				msg.ints = new int[2];

				// you're the leader, add the location to the broadcast
				msg.strings[0] = "leaderMsg";
				msg.strings[1] = "idle";
				msg.locations[0] = rc.getLocation();
				msg.ints[0] = rc.getRobot().getID();

				if (startOfGame == true) {
					msg.strings[1] = "idle";
					int time = Clock.getRoundNum();
					if (time < 70)
						msg.strings[1] = SPAWN_STRING;
					if (time >= 150)
						startOfGame = false;
				} else {

					countSquad(); // count the number of troops close by

					int soldiersNeeded = 0;

					if (troopNum() < MIN_SOLDIER_NUM) {
						soldiersNeeded = MIN_SOLDIER_NUM - soldierNum;
						msg.ints[1] = soldiersNeeded;
						msg.strings[1] = SPAWN_STRING;
						/*
						 * if (mortarNum < MIN_MORTAR_NUM) { start_morph_delay =
						 * Clock.getRoundNum(); wake_delay = 80; }
						 */
						rc.setIndicatorString(0, " " + squadID);
						rc.setIndicatorString(1, " " + following);
					} else {
						// ///////////////////////// attack tower mode
						// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
						msg.strings[1] = ATTACK_STRING;

						Direction goalDir = null;

						// try to search for a tower in sensor range
						Robot target = searchForTower();

						// if we found it - yay
						if (target != null) {
							RobotInfo targetInfo = rc.senseRobotInfo(target);
							goalDir = calcDirection(targetInfo.location);
							msg.ints[1] = rc.getRobot().getID();
							// we now know where it is - pass it in the order
							if (targetInfo.team != myTeam) {
								msg.locations[1] = targetInfo.location;
							}
						}
						// else we didn't find it, so keep moving towards it
						else {
							if (commanderTargetLocation != null)
								msg.locations[1] = commanderTargetLocation;

							goalDir = rc.senseClosestUnknownTower();
							if (!working && furthestArchonDist() < 8)
								hunt(goalDir);
						}
						// we for sure know the direction of the target, so we
						// pass it on
						msg.strings[2] = goalDir.toString();
						if (isAlliedTower(msg.locations[1]))
							msg.locations[1] = null;
					}

				}
				// broadcast the message to minions
				if (!rc.hasBroadcastMessage())
					rc.broadcast(msg);

				rc.yield();

			} catch (Exception e) {
				System.out.println("Caught exception:");
				e.printStackTrace();
			}
		}
	}

	boolean isFinder = false;

	/*
	 * Routines for Archon Followers
	 */
	private void archonFollower() {
		Direction goalDir = null;
		MapLocation goalLoc = null;
		int[] commanders = new int[ARCHONS_PER_TEAM - 1];
		MapLocation lastLeaderLocation = null;
		while (true) {
			try {
				working = rc.isMovementActive()
						|| rc.getRoundsUntilAttackIdle() != 0
						|| rc.getRoundsUntilMovementIdle() != 0;

				// prioritize healing bots in adjacent squares

				if (isFinder) {
					Robot weakAdjAlly = findWeakestAdjacentBot();
					if (weakAdjAlly != null && needsHealing(weakAdjAlly))
						archonHeal(weakAdjAlly);
				} else {
					Robot[] critBots = findCritHealthAllies();
					Robot closestCritBot = null;
					double closestDist = 0;
					// find the closest bot with crit health
					for (int i = 0; i < critBots.length; i++) {
						if (critBots[i] != null) {
							System.out.println(critBots[i].getID() + " ");
							double x = distanceFrom(rc
									.senseRobotInfo(critBots[i]).location);
							if (closestCritBot == null) {
								closestCritBot = critBots[i];
								closestDist = x;
							} else if (x < closestDist) {
								closestCritBot = critBots[i];
								closestDist = x;
							}
						}
					}
					if (closestCritBot != null) {
						if (closestDist < 1.75)
							archonHeal(closestCritBot);
					} else {
						// search for medium health allies
						Robot[] lowBots = findLowHealthAllies();
						Robot closestLowBot = null;
						// find the closest bot with crit health
						for (int i = 0; i < lowBots.length; i++) {
							if (lowBots[i] != null) {
								System.out.println(lowBots[i].getID() + " ");
								double x = distanceFrom(rc
										.senseRobotInfo(lowBots[i]).location);
								if (closestLowBot == null) {
									closestLowBot = lowBots[i];
									closestDist = x;
								} else {
									if (x < closestDist) {
										closestLowBot = lowBots[i];
										closestDist = x;
									}
								}
							}
						}
					}

				}

				// get all msgs
				Message[] incomingMsgs = rc.getAllMessages();
				MapLocation commanderTargetLocation = null;
				String behavior = "none";

				if (incomingMsgs != null) {
					for (int i = 0; i < incomingMsgs.length; i++) {
						// checks if it's a message from the leader
						if (incomingMsgs[i].ints[0] == following)
							leaderMsg = incomingMsgs[i];
						else if (incomingMsgs[i].strings[0]
								.equals("establishLeader")
								&& incomingMsgs[i].ints[1] == following)
							for (int j = 2; j <= ARCHONS_PER_TEAM; j++) {
								if (commanders[j - 2] == 0)
									commanders[j - 2] = incomingMsgs[i].ints[j];
							}
						else if (incomingMsgs[i].strings[0].equals("leaderMsg")
								&& incomingMsgs[i].strings[1]
										.equals(ATTACK_STRING)
								&& isCommander(commanders,
										incomingMsgs[i].ints[0])
								&& incomingMsgs[i].locations[1] != null) {
							commanderTargetLocation = incomingMsgs[i].locations[1];
						}
					}
				}
				// get orders and set behaviors accordingly
				if (leaderMsg != null) {
					// memorize the last location of the leader (so we don;t get
					// lost)
					lastLeaderLocation = leaderMsg.locations[0];
					if (distanceFrom(lastLeaderLocation) > MAX_DIST_FROM_LEADER) {
						if (!working)
							hunt(calcDirection(lastLeaderLocation));
					} else
					// spawn
					if (leaderMsg.strings[1].equalsIgnoreCase(SPAWN_STRING))
						behavior = SPAWN_STRING;
					else
					// attack
					if (leaderMsg.strings[1].equalsIgnoreCase(ATTACK_STRING)) {

						behavior = ATTACK_STRING;
						// where we're headed from what the leader tells us
						if (goalLoc == null)
							goalLoc = leaderMsg.locations[1];

						// check if we get a new direction
						if (leaderMsg.strings[2] != null)
							goalDir = msgDirection(leaderMsg.strings[2]);
					} else
					// idle
					if (leaderMsg.strings[1].equalsIgnoreCase("idle"))
						behavior = "idle";

					// spawn
					if (behavior.equals(SPAWN_STRING)) {
						MapLocation spawnLoc = spaceToSpawn();
						if (rc.canSpawn()
								&& spawnLoc != rc.getLocation()
								&& rc.getEnergonLevel() > 2 * RobotType.SOLDIER
										.spawnCost()) {
							Direction spawnDir = calcDirection(spawnLoc);
							if (spawnDir == rc.getDirection()) {
								rc.spawn(RobotType.SOLDIER);
								spawnLocation = rc.getLocation().add(
										rc.getDirection());
							} else if (!working)
								rc.setDirection(spawnDir);
						}
					} else
					// ////////////////////////////attack\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
					if (behavior.equals(ATTACK_STRING)) {

						// if the leader has no idea where the goal is located
						// then let's try to search for a tower ourselves
						if (goalLoc == null || isFinder) {
							Robot target = searchForTower();
							// if successful, pass on the info
							if (target != null) {
								RobotInfo targetInfo = rc
										.senseRobotInfo(target);
								leaderMsg.ints[1] = rc.getRobot().getID();
								isFinder = true;
								if (targetInfo.team != myTeam) {
									leaderMsg.locations[1] = targetInfo.location;
								}
							} else {
								isFinder = false;
								if (commanderTargetLocation != null) {
									leaderMsg.locations[1] = commanderTargetLocation;
								}
							}
						}
						// splice in the new ID
						leaderMsg.ints[0] = rc.getRobot().getID();
						// check for allied tower
						if (isAlliedTower(leaderMsg.locations[1]))
							leaderMsg.locations[1] = null;
						// broadcast that
						rc.broadcast(leaderMsg);

						// if nobody senses the tower, move towards it
						if (!working && leaderMsg.locations[1] == null
								&& goalDir != null)
							hunt(goalDir);

					}
				}
				// else there is no leader Msg (maybe we are out of range?)
				else if (distanceFrom(lastLeaderLocation) > MAX_DIST_FROM_LEADER)
					hunt(calcDirection(lastLeaderLocation));

				rc.yield();

			} catch (Exception e) {
				System.out.println("Caught Exception:");
				e.printStackTrace();
			}
		}
	}

	// looks through the array of commander IDs and sees if the ID is in there
	// (sees if the robot is a commander)
	public boolean isCommander(int[] commanders, int ID) {
		for (int i = 0; i < commanders.length; i++)
			if (ID == commanders[i])
				return true;
		return false;
	}

	// Archon Follower stuff
	MapLocation spawnLocation = null;

	/*
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
				// if possible, heal hurt bot completely, else contribute all
				// possible
				// without committing suicide (hence subtracting the archon's
				// energon upkeep)
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
						.getEnergonLevel() * .8);
				rc.transferEnergon(transferAmount, hurtBotInfo.location,
						hurtBot.getRobotLevel());

			}

		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
	}

	private boolean needsHealing(Robot bot) {
		boolean needToHeal = false;
		try {
			RobotInfo botInfo = rc.senseRobotInfo(bot);
			if (botInfo.energonLevel < botInfo.maxEnergon / 2)
				needToHeal = true;
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		return needToHeal;
	}

	/*
	 * Method for count squad. Resets variables so another count can be taken.
	 */
	private void resetCount() {
		archonNum = 0;
		soldierNum = 0;
		sniperNum = 0;
		mortarNum = 0;
		scoutNum = 0;
		bomberNum = 0;
	}

	/*
	 * Count the number of allied bots within radius. This is used to tell if we
	 * need to spawn or not.
	 */
	private void countSquad() {
		// sense nearby bots and then process the info
		Robot[] nearbyGroundRobots = rc.senseNearbyGroundRobots();
		try {
			resetCount();
			for (int i = 0; i < nearbyGroundRobots.length; i++) {
				RobotInfo roboInfo = rc.senseRobotInfo(nearbyGroundRobots[i]);
				if (roboInfo.team.equals(rc.getTeam())) {
					switch (roboInfo.type) {
					case ARCHON:
						archonNum++;
						break;
					case SOLDIER:
						soldierNum++;
						break;
					case SNIPER:
						sniperNum++;
						break;
					case MORTAR:
						mortarNum++;
						break;
					case SCOUT:
						scoutNum++;
						break;
					case BOMBER:
						bomberNum++;
						break;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("caught exception:");
			e.printStackTrace();
		}
	}

	private int troopNum() {
		return soldierNum + sniperNum + mortarNum + scoutNum + bomberNum;
	}

	/*
	 * Have archon check to see if there is room around him to spawn. PROBABLY
	 * WILL NOT USE. HERE ANYWAY. NEEDS ADJUSTMENT--rc.canMove(direction) -to
	 * check for obstacles
	 */
	private boolean haveRoomToSpawn() {
		boolean isNEmpty = false, isSEmpty = false, isEEmpty = false, isWEmpty = false, isNEEmpty = false, isNWEmpty = false, isSEEmpty = false, isSWEmpty = false;
		try {
			MapLocation currLoc = rc.getLocation(); // current locatoin of
			// archon

			// whether or not there is a ground robot in all directions
			isNEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.NORTH)) == null;
			isSEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.SOUTH)) == null;
			isEEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.EAST)) == null;
			isWEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.WEST)) == null;
			isNEEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.NORTH_EAST)) == null;
			isNWEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.NORTH_WEST)) == null;
			isSEEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.SOUTH_EAST)) == null;
			isSWEmpty = rc.senseGroundRobotAtLocation(currLoc
					.add(Direction.SOUTH_WEST)) == null;

		} catch (Exception e) {
			System.out.println("Caught Exception");
			e.printStackTrace();
		}
		return isNEmpty || isSEmpty || isEEmpty || isWEmpty || isNEEmpty
				|| isNWEmpty || isSEEmpty || isSWEmpty;
	}

	/*
	 * Check to see if any of the adjacent squares are available to spawn into.
	 * As soon as an empty space is found, return location.
	 */
	private MapLocation spaceToSpawn() {
		MapLocation currLoc = null;
		try {
			currLoc = rc.getLocation(); // current location of archon

			// the can move part makes sure there is not a generic obstacle at
			// the
			// particular adjacent location
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
		return currLoc;
	}

	private MapLocation airSpaceToSpawn() {
		MapLocation currLoc = null;
		try {
			currLoc = rc.getLocation(); // current location of archon

			// the can move part makes sure there is not a generic obstacle at
			// the
			// particular adjacent location
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
		return currLoc;
	}

	/**
	 * Other Bot SubRoutines
	 */

	/*
	 * For a non-archon bot to select a leader. Selects as leader the archon
	 * closest to it. If it cannot find an archon, suicide. A child is lost
	 * without mother.
	 */
	private void findMotherBot() {
		try {
			fighterMom = findClosestArchon();
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

	/*
	 * Takes a string and interprets which direction to move in from the string.
	 */
	private Direction msgDirection(String msgDir) {
		try {
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
		return null;
	}

	private double furthestArchonDist() {
		double farArchonDist = 0;
		try {
			MapLocation[] archonLocs = rc.senseAlliedArchons();
			for (int i = 0; i < archonLocs.length; i++) {
				double distFromArchon = distanceFrom(archonLocs[i]);
				if (distFromArchon > farArchonDist)
					farArchonDist = distFromArchon;
			}
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		return farArchonDist;
	}

	private boolean isEnemyNear() {
		try {
			Robot[] nearGBots = rc.senseNearbyGroundRobots();
			// Robot[] nearABots = rc.senseNearbyAirRobots();
			for (int i = 0; i < nearGBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGBots[i]);
				if (botInfo.team != myTeam && // botInfo.type !=
						// RobotType.ARCHON &&
						botInfo.type != RobotType.TOWER)
					return true;
			}

			// for (int i = 0; i < nearABots.length; i++) {
			// if (rc.senseRobotInfo(nearABots[i]).team != myTeam)
			// return true;
			// }
		} catch (Exception e) {
			System.out.println("Caught exception:");
			e.printStackTrace();
		}
		return false;
	}

	private Robot targetEnemyGBot() {
		Robot enemyBot = null;
		try {
			Robot[] nearGBots = rc.senseNearbyGroundRobots();

			for (int i = 0; i < nearGBots.length; i++) {
				RobotInfo botInfo = rc.senseRobotInfo(nearGBots[i]);
				if (botInfo.team != myTeam){
					if(botInfo.type == RobotType.TOWER) {
						return nearGBots[i];
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