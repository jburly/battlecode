package team000;
import java.math.*;
import battlecode.common.*;
import static battlecode.common.GameConstants.*;
public class RobotPlayer implements Runnable{
 private final RobotController rc;
 private Robot tower = null;
 private Direction nearbyTwrDir = null;
 private MapLocation nearTowerLoc = null;
 
 //Individual Squadron Unit Numbers for Archons
 private int archonNum, scoutNum, bomberNum, soldierNum, mortarNum, sniperNum = 0;
 
 private final static int MIN_SOLDIER_NUM = 4;
 private final static int MIN_MORTAR_NUM = 3;
 
 private final static String ATTACK_STRING = "attack";
 private final static String SPAWN_STRING = "spawn";
 
 //whether or not the team has any air units
 //initially, we have none
 private boolean haveAirUnits = false;
 
 //unchanging variables for any robot
 //so I am not always calling the robot controller for these
 
 private Team myTeam;
 private RobotType myType;
 
 private int following = 0;//the ID of the robot you're following
 //private Message[] incomingMsgs; //all the broadcasted messages in range
 private Message leaderMsg = null; //message from the leader
 
 private boolean working;//if the archon is already doing something 
 
 public RobotPlayer(RobotController rC){
  rc = rC;
  myTeam = rc.getTeam();
  myType = rc.getRobotType();
 }
 public void run(){
  while(true){
         try{
            //myType = rc.getRobotType();
            /*** beginning of main loop ***/
         working = rc.isMovementActive() ||
          rc.getRoundsUntilAttackIdle() != 0 ||
          rc.getRoundsUntilMovementIdle() != 0;
         //while(working){rc.yield();}
        
         switch(myType){
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
         }
             rc.yield();
          /*** end of main loop ***/
         }catch(Exception e) {
            System.out.println("caught exception:");
            e.printStackTrace();
         }
  } 
 }
 
 
 /********************
  * Movement Methods
  ********************/
 private Direction goalDir;
 private Robot target;
 private boolean isTracing = false;
 
 //info about the target
 private RobotInfo targetInfo;
 //private boolean canAttack = false;
 
 private void hunt(){
  try{
   //if there is no target, find one
   /*if(target == null)
    designateTarget();
   //if target is an ally, find a new one
   else if(targetInfo.team == myTeam)
    designateTarget();
    */
   
   //move in the direction of the target
   if(rc.canMove(goalDir)){
    isTracing = false;
    if(rc.getDirection() != goalDir)
     rc.setDirection(goalDir);
    else if(rc.canMove(rc.getDirection()))
     rc.moveForward();
   }
   //if there is an obstacle in your way, trace around it
   else{
    isTracing = true;
    if(rc.canMove(goalDir))
     isTracing = false;
    else if(rc.canMove(rc.getDirection()))
     rc.moveForward();
    else
     rc.setDirection(rc.getDirection().rotateRight());
   }
   rc.yield();
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
 }
 
 /*
  * Judges the direction one should travel to reach the goal.
  * Compares current position with position of the target to calculate.
  */
 private Direction calcDirection(MapLocation goal){
  int currX = rc.getLocation().getX();
  int currY = rc.getLocation().getY();
  
  if(currX < goal.getX()){
   if(currY < goal.getY())
    return Direction.SOUTH_EAST;
   else if(currY > goal.getY())
    return Direction.NORTH_EAST;
   else
    return Direction.EAST;
  }
  else if(currX > goal.getX()){
   if(currY < goal.getY())
    return Direction.SOUTH_WEST;
   else if(currY > goal.getY())
    return Direction.NORTH_WEST;
   else
    return Direction.WEST;
  }
  else
   if(currY < goal.getY())
    return Direction.SOUTH;
   else if(currY > goal.getY())
    return Direction.NORTH;
   else
    return Direction.NONE;
  
 }
 
 /*
  * Return the distance from the current bot to a targeted location.
  */
 private double distanceFrom(MapLocation target){
  int xDiff = Math.abs(rc.getLocation().getX() - target.getX());
  int yDiff = Math.abs(rc.getLocation().getY() - target.getY());
  
  return Math.hypot(xDiff, yDiff);
 }
 
 /*
  * Return the distance from one location to target location.
  */
 private double distanceFrom(MapLocation location, MapLocation target){
  int xDiff = Math.abs(location.getX() - target.getX());
  int yDiff = Math.abs(location.getY() - target.getY());
  
  return Math.hypot(xDiff, yDiff);
 }
 
 /*
  * Designates a target for the bots to hunt.
  * Specifically looks for towers and if none are near,
  * defaults movement to direction of closest unknown tower.
  */
 private void designateTarget(){
  try{
   target = searchForTower();
   if(target != null){
    targetInfo = rc.senseRobotInfo(target);
    goalDir = calcDirection(targetInfo.location);
   }
   else
    goalDir = rc.senseClosestUnknownTower();
   rc.yield();
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
 }
 
 /*
  * Just a quick check to see if a target has been designated.
  */
 private boolean haveTarget(){
  return targetInfo != null && target != null;
 }
 
 
 /******************************
  *       SEARCH METHODS
  ******************************/
 
 /*
  * Finds a tower if it is in the sensor range of the robot.
  */
 private Robot searchForTower(){
  try{
   Robot nearbyBots[] = rc.senseNearbyGroundRobots();
   
   for(int i = 0; i < nearbyBots.length; i++){
    RobotInfo botInfo = rc.senseRobotInfo(nearbyBots[i]);
    if(botInfo.team != rc.getTeam() && botInfo.type == RobotType.TOWER)
     return nearbyBots[i];
   }
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
  return null;
 }
 
 //searches for the nearest allied tower
 private MapLocation nearestAlliedTower(){
  //the closest allied tower
  MapLocation closestTower = null;
  
  try{
   MapLocation[] allyTowers = rc.senseAlliedTowers();
   
   for(int i = 0; i < allyTowers.length; i++){
    if(closestTower == null ||
      distanceFrom(closestTower) > distanceFrom(allyTowers[i]))
     closestTower = allyTowers[i];
   }
   
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
  return closestTower;
 }
 
 /*
  * Checks to see if there are any air enemies in the vicinity.
  *
  * Note: Only returns one bot.  May want this to return all bots, or
  *   just make it a boolean statement indicating that air bots are close.
  */
 private Robot searchForAirEnemy(){
  try{
   Robot nearAirBots[] = rc.senseNearbyAirRobots();
   
   for(int i = 0; i < nearAirBots.length; i++){
    RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
    if(botInfo.team != rc.getTeam() && botInfo.type == RobotType.BOMBER ||
      botInfo.type == RobotType.SCOUT)
     return nearAirBots[i];
   }
  }catch(Exception e){
   System.out.println("Caught Exception");
   e.printStackTrace();
  }
  return null;
 }
 
 /*
  *Finds the weakest ally in range.
  *For archons to know who to heal.
  */
 private Robot findWeakestAlly(){
  Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
  Robot weakestBot = null;
  try{
   //search for ground bots no matter what, they should exist
   for(int i = 0; i < nearGroundBots.length; i++){
    RobotInfo botInfo = rc.senseRobotInfo(nearGroundBots[i]);
    if(botInfo.team == rc.getTeam()){
     if(weakestBot == null)
      weakestBot = nearGroundBots[i];
                  else if(rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
                   weakestBot = nearGroundBots[i];
                }
            }
   //air bots might not exist so...
   //might want to make this conditional on whether we still need
   //air bots or not -- no sense healing what we don't need
   if(haveAirUnits){
    Robot[] nearAirBots = rc.senseNearbyAirRobots();
    for(int i = 0; i < nearAirBots.length; i++){
     RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
     if(botInfo.team == rc.getTeam()){
      if(weakestBot == null)
       weakestBot = nearGroundBots[i];
      else if(rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
       weakestBot = nearGroundBots[i];
     }
    }
   }
  }catch(Exception e){
   System.out.println("caught exception:");
   e.printStackTrace();
  }
  return weakestBot;
 }
 
 
 private Robot findWeakestAdjacentBot(){
  Robot[] nearGroundBots = rc.senseNearbyGroundRobots();
  Robot weakestBot = null;
  try{
   //search for ground bots no matter what, they should exist
   for(int i = 0; i < nearGroundBots.length; i++){
    RobotInfo botInfo = rc.senseRobotInfo(nearGroundBots[i]);
    
    //if it is adjacent
    if(botInfo.team == myTeam && botInfo.type != RobotType.ARCHON &&
      distanceFrom(botInfo.location,rc.getLocation()) <= 1){
     if(weakestBot == null)
      weakestBot = nearGroundBots[i];
     else if(rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
      weakestBot = nearGroundBots[i];
    }
   }
   
   //air bots might not exist so...
   //might want to make this conditional on whether we still need
   //air bots or not -- no sense healing what we don't need
   if(haveAirUnits){
    Robot[] nearAirBots = rc.senseNearbyAirRobots();
    for(int i = 0; i < nearAirBots.length; i++){
     RobotInfo botInfo = rc.senseRobotInfo(nearAirBots[i]);
     
     //if it is adjacent
     if(distanceFrom(botInfo.location,rc.getLocation()) <= 1){
      if(botInfo.team == myTeam){
       if(weakestBot == null)
        weakestBot = nearGroundBots[i];
       else if(rc.senseRobotInfo(weakestBot).energonLevel > botInfo.energonLevel)
        weakestBot = nearGroundBots[i];
      }
     }
    }
   }
  }catch(Exception e){
   System.out.println("caught exception:");
   e.printStackTrace();
  }
  return weakestBot;
 }
 
 /*
  * For spawned soldiers to find out who spawned them.
  * They will designate the one that is closest to them as leader.
  */
 //just to make sure the archon does not change every call
 private Robot closestArchon = null;
 private Robot findClosestArchon(){
  //Robot closestArchon = null;
  try{
   MapLocation[] archonLocs = rc.senseAlliedArchons();
   MapLocation archonLoc = null;
   for(int i = 0; i < archonLocs.length; i++){
    if(archonLoc == null)
     archonLoc = archonLocs[i];
    else if(distanceFrom(archonLoc) > distanceFrom(archonLocs[i]))
     archonLoc = archonLocs[i];
   }
   if(archonLoc != null){
    Direction archonDir = calcDirection(archonLoc);
    if(rc.getDirection() == archonDir && rc.canSenseSquare(archonLoc)){
     closestArchon = rc.senseGroundRobotAtLocation(archonLoc);
    }
    else if(!rc.isMovementActive())
     rc.setDirection(archonDir);
   }
   
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
  return closestArchon;
 }
 
 private boolean needsHealing(Robot bot){
  boolean needToHeal = false;
  try{
   RobotInfo botInfo = rc.senseRobotInfo(bot);
   if(botInfo.energonLevel < botInfo.maxEnergon / 2)
    needToHeal = true;
  }catch(Exception e){
   System.out.println("Caught exception:");
   e.printStackTrace();
  }
  return needToHeal;
 }
 
 private Message findLeaderMessage(Message[] incomingMsgs){
     Message leaderMsg = null;
     try{
	 //parse messages
      
	 if(incomingMsgs != null){
	     System.out.println("incoming msgs not null " + incomingMsgs.length);
	     for(int i = 0; i < incomingMsgs.length; i++){
		 //checks if it's a message from the leader
		 if(incomingMsgs[i].ints[0] == following)
		     leaderMsg = incomingMsgs[i];
		 System.out.println("message is" + incomingMsgs[i].ints[0]);
	     }
	 }
     }catch(Exception e){
	 System.out.println("Caught exception:");
	 e.printStackTrace();
     }
     return leaderMsg;
 }
 
 /*************************
  * BOT ROUTINES
  *************************/
 
 //non-archon bot's leader
 private Robot fighterMom = null; //the archon the soldier should follow
 
 private void scout(){
  try{
   if(fighterMom == null)
    findMotherBot();
   
   
   Robot airFoe = searchForAirEnemy();
   if(airFoe != null){
    target = airFoe;
    targetInfo = rc.senseRobotInfo(airFoe);
   }
   if(haveTarget())
    if(rc.canAttackSquare(targetInfo.location) && targetInfo.team != myTeam)
     if(targetInfo.type == RobotType.BOMBER ||
       targetInfo.type == RobotType.SCOUT)
      rc.attackAir(targetInfo.location);
     else
      rc.attackGround(targetInfo.location);
   hunt();
   
  }catch(Exception e){
   System.out.println("caught exception");
   e.printStackTrace();
  }
 }
 private void bomber(){
  try{
  if(haveTarget())
   if(rc.canAttackSquare(targetInfo.location) && targetInfo.team != myTeam)
     rc.attackGround(targetInfo.location);
  hunt();
  }catch(Exception e){
   System.out.println("caught exception");
   e.printStackTrace();
  }
 }
 
 private void soldier(){
  try{
   //get all messages
   Message[] incomingMsgs = rc.getAllMessages();
   
   //top priority is figuring out who to follow
   //this should only be done once
   if(fighterMom == null)
    findMotherBot();
    
   
   //look for mom's message
   else{
    leaderMsg = findLeaderMessage(incomingMsgs);
   
    if(rc.getRobotType().compareTo(RobotType.SOLDIER) == 0 &&
      rc.getEventualEnergonLevel()>=rc.getMaxEnergonLevel()*3/4){
     rc.yield();
     rc.evolve(RobotType.MORTAR);
     myType = RobotType.MORTAR;
    }
   }
   rc.yield();
  }catch(Exception e){
   System.out.println("caught exception:");
   e.printStackTrace();
  }
 }
 
 //mortar variable
 //private boolean movedBack = false;
 private void mortar(){
     while (true) {
	 try{
	     while (rc.isMovementActive() || rc.isAttackActive()){rc.yield();}
	     Message[] incomingMsgs = rc.getAllMessages(); 
	     //boolean canAttack = false; //if bot can attack the target
	     //get all messages
      
	     
	     if(fighterMom != null) {
		 leaderMsg = findLeaderMessage(incomingMsgs);
		 System.out.println(fighterMom.getID());  //+ " " + leaderMsg.ints[0]);
	     }
	     else
		 findMotherBot();
   
	     if(leaderMsg != null) {
		 System.out.println(leaderMsg.locations[1] + " ");
		 if(leaderMsg.strings[1].equalsIgnoreCase("attack")){
		     if (leaderMsg.locations[1] != null)
			 if (rc.canAttackSquare(leaderMsg.locations[1]))
			     rc.attackGround(leaderMsg.locations[1]);
			 else
			     goalDir = calcDirection(leaderMsg.locations[1]);
		     else
		     {
			 goalDir = msgDirection(leaderMsg.strings[2]);
			 if (goalDir != null)
			     hunt();
		     } 
		 }
		 /*
     	goalDir = msgDirection(leaderMsg.strings[2]);
     MapLocation goalLoc = leaderMsg.locations[1];
     if(goalLoc != null && rc.canSenseSquare(goalLoc))
      target = rc.senseGroundRobotAtLocation(goalLoc);
    }
   //move unless needs to be healed
   if(haveTarget() && rc.getEnergonLevel() > rc.getMaxEnergonLevel() / 2){
    boolean canAttack = rc.canAttackSquare(targetInfo.location);
    //targetInfo = rc.senseRobotInfo(target);
    if(canAttack && targetInfo.team != myTeam){
     rc.attackGround(targetInfo.location);
    }*/
//    else if(rc.canMove(rc.getDirection().opposite()) && !movedBack){
//     rc.moveBackward();
//     movedBack = true;
//    }
//    
//    if(targetInfo.team == myTeam){
//     target = null;
//     targetInfo = null;
//    }
   // else
     //hunt();
	     }
//   else if(rc.getRoundsUntilMovementIdle() == 0 && !canAttack)
//    hunt();
    
	     rc.yield();
	 }catch(Exception e){
	     System.out.println("caught exception:");
	     e.printStackTrace();
	 }
     }
 }
 
 private void sniper(){
  try{
   
  }catch(Exception e){
   System.out.println("caught exception:");
   e.printStackTrace();
  }
 }
 
 
 //archon variables
    private boolean leader = false;
    private String behavior = "none";
   
 private void archon(){
  try{
   //establish leadership if it has yet to be done
   if(following == 0 && leader == false){
    designateLeader();
    rc.setIndicatorString(1, " " + following);
    rc.setIndicatorString(0, " " + leader);
   }
   
   //if you are a leader, perform your leader duties
   if(leader == true)
    archonLeader();
   
   //else if you are a follower, act accordingly
   else if(following != 0)
    archonFollower();
   
  }catch(Exception e){
   System.out.println("caught exception:");
   e.printStackTrace();
  }
 }
 
 /******************************
  * ADDITIONAL ARCHON ROUTINES
  ******************************/
 private void designateLeader(){
  Message establishLeaderMsg = null;
  try{
   Message[] incomingMsgs = rc.getAllMessages();
   //Message establishLeaderMsg = null;
   //parse messages
   if(incomingMsgs != null){
    //currentLeaderNum = incomingMsgs.ints[3];
    for(int i = 0; i < incomingMsgs.length; i++){
     //checks if it's an establish leader message
     if(incomingMsgs[i].strings[0].equals("establishLeader"))
                     establishLeaderMsg = incomingMsgs[i];
    }
   }
   
   //initially establish leadership
   if(establishLeaderMsg == null && leader == false && following == 0){
    //the first robot assumes leadership and communicates his ID
    leader = true;
    //make a new message saying "hey I'm chief, who wants to follow me?"
    Message msg = new Message();
    msg.strings = new String[1];
    msg.strings[0] = "establishLeader";
    msg.ints = new int[3];
    msg.ints[0] = rc.getRobot().getID();
    msg.ints[1] = 1; //the number of bots within the unit so far
    rc.broadcast(msg);
   }
   else{
    //other archons decide to follow the leader
    if(leader == false && following == 0 &&
      establishLeaderMsg != null){
     if(establishLeaderMsg.ints[1] < 4){
      following = establishLeaderMsg.ints[0];
      
      //this particular leader has a new follower, so update
      //count of bots within squad
      establishLeaderMsg.ints[1]++;
      
      //broadcast the updated message
      rc.broadcast(establishLeaderMsg); 
     }
    }
   }
   rc.yield();
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
 }
 
 private int alliedSquadLeader = 0;
 
 //archon leader action routines
 private void archonLeader(){
     int wake_delay = 0;
     int start_morph_delay = 0;
     while (true) {
	 try{
	     
	     working = rc.isMovementActive() ||
	          rc.getRoundsUntilAttackIdle() != 0 ||
	          rc.getRoundsUntilMovementIdle() != 0;
	     
	     boolean broadcasted = false;
	     //get msgs	
	     Message[] incomingMsgs = rc.getAllMessages();
	     //parse messages
	     //look for a message from the other leader to establish who it is
	     if(incomingMsgs != null && alliedSquadLeader == 0){
		 for(int i = 0; i < incomingMsgs.length; i++){
		     //checks if it's a message from another leader
		     if(incomingMsgs[i].strings[0].equals("leaderMsg") &&
			     incomingMsgs[i].ints[0] != rc.getRobot().getID())
			 alliedSquadLeader = incomingMsgs[i].ints[0];
		 }
	     }
	     Message msg = new Message();
	     msg.strings = new String[3];
	     msg.locations = new MapLocation[2];
	     msg.ints = new int[2];
	     //you're the leader, add the location to the broadcast
	     msg.strings[0] = "leaderMsg";
	     msg.locations[0] = rc.getLocation();
	     msg.ints[0] = rc.getRobot().getID();
       
	     System.out.println(start_morph_delay + " " + Clock.getRoundNum() + " " + wake_delay);
	     if (Clock.getRoundNum() - start_morph_delay < wake_delay) {
		 msg.strings[1] = "idle";
	     }
	     else {
		 countSquad(); //count the number of troops close by
		 //	System.out.println(soldierNum);
		 int soldiersNeeded = 0;
		 //System.out.println(soldierNum);
		 if(troopNum() < MIN_SOLDIER_NUM){
		     soldiersNeeded = MIN_SOLDIER_NUM - soldierNum;
		     // if(rc.canSpawn() &&
		     //      rc.senseGroundRobotAtLocation(rc.getLocation().add(rc.getDirection())) == null &&
		     //      rc.getEnergonLevel() > 2*RobotType.SOLDIER.spawnCost()) {
		     //rc.spawn(RobotType.SOLDIER);
		     //soldiersNeeded--;
		     msg.ints[1] = soldiersNeeded;
		     msg.strings[1] = SPAWN_STRING;
                
		     if (mortarNum < MIN_MORTAR_NUM) {
			 start_morph_delay = Clock.getRoundNum();
			 wake_delay = 150;
		     }
                
		     rc.setIndicatorString(0, " " + soldiersNeeded);
		     rc.setIndicatorString(1, " " + following);
		 }
		 else {
             
		 designateTarget();
		 msg.strings[1] = ATTACK_STRING;
		 msg.strings[2] = goalDir.toString();
		 
		 if (targetInfo != null && targetInfo.team != myTeam)
		     msg.locations[1] = targetInfo.location;                
		 else 
		     if(!working && furthestArchonDist() < 8)
			 hunt(); 
	     	}
	     }
	     if(!broadcasted) 
		 rc.broadcast(msg);
	     rc.yield();
	     
	 }catch(Exception e){
	     System.out.println("Caught exception:");
	     e.printStackTrace();
	 }
     }
 }
 
 
  //Archon Follower stuff
 MapLocation spawnLocation = null;
 
 /*
  * Routines for Archon Followers
  */
 private void archonFollower(){
  try{
      
      //prioritize healing bots in adjacent squares
      Robot weakAdjAlly = findWeakestAdjacentBot();
      if(weakAdjAlly != null && needsHealing(weakAdjAlly))
       archonHeal(weakAdjAlly);
      
      //get msgs
   Message[] incomingMsgs = rc.getAllMessages();
   
   //get message from leader
   leaderMsg = findLeaderMessage(incomingMsgs);
   
   //get orders
   if(leaderMsg != null){
//    if(distanceFrom(leaderMsg.locations[0]) >= RobotType.ARCHON.broadcastRadius()){
//     goalDir = calcDirection(leaderMsg.locations[0]);
//     behavior = "followLeader";
//     hunt();
//    }
    if(leaderMsg.strings[1].equalsIgnoreCase(SPAWN_STRING)) behavior = "spawn";
    else if(leaderMsg.strings[1].equalsIgnoreCase(ATTACK_STRING)){
     behavior = "attack";
     goalDir = msgDirection(leaderMsg.strings[2]);
     MapLocation goalLoc = leaderMsg.locations[1];
     if(goalLoc != null && rc.canSenseSquare(goalLoc))
      target = rc.senseGroundRobotAtLocation(goalLoc);
    }
   
   
   
   
   
   //spawn
   if(behavior.equals(SPAWN_STRING)){
    MapLocation spawnLoc = spaceToSpawn();
    if(rc.canSpawn() && spawnLoc != rc.getLocation() &&
      rc.getEnergonLevel() > 2*RobotType.SOLDIER.spawnCost()){
      Direction spawnDir = calcDirection(spawnLoc);
      if(spawnDir == rc.getDirection()){
       rc.spawn(RobotType.SOLDIER);
       spawnLocation = rc.getLocation().add(rc.getDirection());
      }
      else
       if (!working) 
	   rc.setDirection(spawnDir);
    }
   }
   else if(behavior.equals(ATTACK_STRING)){
       leaderMsg.ints[0] = rc.getRobot().getID();
       rc.setIndicatorString(2, behavior);
       rc.broadcast(leaderMsg);
       if (!working && leaderMsg.locations[1] == null) hunt();
   }
   }       /*  //heal
            if (behavior.equals("spawn") && spawnLocation != null){
                Robot spawnedRobot = rc.senseGroundRobotAtLocation(spawnLocation);
                if (spawnedRobot != null){
                    archonHeal(spawnedRobot);
                }
            }
            else{
                Robot weakestAdjAlly = findWeakestAdjacentBot();
                if (weakestAdjAlly != null){
                    archonHeal(weakestAdjAlly);
                }
            }
            */
            rc.setIndicatorString(2, behavior);
            //rc.setIndicatorString(1, " " + following);
            rc.yield();
   
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
 }
 
 
 /*
  * Method for the archon to heal a specified bot to the best of its ability.
  */
 private void archonHeal(Robot hurtBot){
  try{
   RobotInfo hurtBotInfo = rc.senseRobotInfo(hurtBot);
   if(rc.getEnergonLevel() > RobotType.ARCHON.maxEnergon() / 8 &&
     hurtBotInfo.eventualEnergon < hurtBotInfo.type.maxEnergon()){
    
     double energonNeeded = hurtBotInfo.maxEnergon - hurtBotInfo.energonLevel;
     //if possible, heal hurt bot completely, else contribute all possible
     // without committing suicide (hence subtracting the archon's energon upkeep)
     double transferAmount = Math.min(energonNeeded,
         rc.getEventualEnergonLevel() -
         RobotType.ARCHON.energonUpkeep());
     rc.transferEnergon(transferAmount, hurtBotInfo.location,
       hurtBot.getRobotLevel());
  
   }
   
  }catch(Exception e){
   System.out.println("Caught exception:");
   e.printStackTrace();
  }
 }
 
 /*
  * Method for count squad.  Resets variables so another count can be taken.
  */
 private void resetCount(){
  archonNum  = 0;
  soldierNum = 0;
  sniperNum  = 0;
  mortarNum  = 0;
  scoutNum   = 0;
  bomberNum  = 0;
 }
 
 /*
  * Count the number of allied bots within radius.
  * This is used to tell if we need to spawn or not.
  */
 private void countSquad(){
  //sense nearby bots and then process the info
        Robot[] nearbyGroundRobots = rc.senseNearbyGroundRobots();
        try{
         resetCount();
         for (int i = 0; i < nearbyGroundRobots.length; i++) {
          RobotInfo roboInfo = rc.senseRobotInfo(nearbyGroundRobots[i]);
          if (roboInfo.team.equals(rc.getTeam())){
           switch(roboInfo.type){
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
        }catch(Exception e){
         System.out.println("caught exception:");
         e.printStackTrace();
        }
 }
 
 private int troopNum(){
  return soldierNum + sniperNum + mortarNum + scoutNum + bomberNum;
 }
 
 /*
  * Have archon check to see if there is room around him to spawn.
  * PROBABLY WILL NOT USE.  HERE ANYWAY. 
  * NEEDS ADJUSTMENT--rc.canMove(direction) -to check for obstacles
  */
 private boolean haveRoomToSpawn(){
  boolean isNEmpty = false, isSEmpty = false, isEEmpty = false, isWEmpty = false,
     isNEEmpty = false, isNWEmpty = false, isSEEmpty = false, isSWEmpty = false;
  try{
  MapLocation currLoc = rc.getLocation(); //current locatoin of archon
  
  //whether or not there is a ground robot in all directions
  isNEmpty  = rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH))      == null;
  isSEmpty  = rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH))      == null;
  isEEmpty  = rc.senseGroundRobotAtLocation(currLoc.add(Direction.EAST))       == null;
  isWEmpty  = rc.senseGroundRobotAtLocation(currLoc.add(Direction.WEST))       == null;
  isNEEmpty = rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH_EAST)) == null;
  isNWEmpty = rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH_WEST)) == null;
  isSEEmpty = rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH_EAST)) == null;
  isSWEmpty = rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH_WEST)) == null;
  
  }catch(Exception e){
   System.out.println("Caught Exception");
   e.printStackTrace();
  }
  return isNEmpty || isSEmpty || isEEmpty || isWEmpty || isNEEmpty || isNWEmpty ||
   isSEEmpty || isSWEmpty;
 }
 
 /*
  * Check to see if any of the adjacent squares are available to spawn into.
  * As soon as an empty space is found, return location.
  */
 private MapLocation spaceToSpawn(){
   MapLocation currLoc = null;
  try{
   currLoc = rc.getLocation(); //current location of archon
   
   //the can move part makes sure there is not a generic obstacle at the
   //particular adjacent location
   if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH)) == null &&
     rc.canMove(Direction.NORTH))
    return currLoc.add(Direction.NORTH);
  
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH)) == null &&
     rc.canMove(Direction.SOUTH))
    return currLoc.add(Direction.SOUTH);
   
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.EAST)) == null &&
     rc.canMove(Direction.EAST))
    return currLoc.add(Direction.EAST);
  
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.WEST)) == null &&
     rc.canMove(Direction.WEST))
    return currLoc.add(Direction.WEST);
   
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH_EAST)) == null &&
     rc.canMove(Direction.NORTH_EAST))
    return currLoc.add(Direction.NORTH_EAST);
  
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.NORTH_WEST)) == null &&
     rc.canMove(Direction.NORTH_WEST))
    return currLoc.add(Direction.NORTH_WEST);
  
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH_EAST)) == null &&
     rc.canMove(Direction.SOUTH_EAST))
    return currLoc.add(Direction.SOUTH_EAST);
  
   else if(rc.senseGroundRobotAtLocation(currLoc.add(Direction.SOUTH_WEST)) == null &&
     rc.canMove(Direction.SOUTH_WEST))
    return currLoc.add(Direction.SOUTH_WEST);
  }catch(Exception e){
   System.out.println("Caught Exception:");
   e.printStackTrace();
  }
  return currLoc;
 }
 
 /**
  * Other Bot SubRoutines
  */
 
 /*
  * For a non-archon bot to select a leader.
  * Selects as leader the archon closest to it.
  * If it cannot find an archon, suicide.  A child is lost without mother.
  */
 private void findMotherBot(){
  try{
   fighterMom = findClosestArchon();
   closestArchon = null;
   
   //this should not be null
   if(fighterMom != null){
    following = fighterMom.getID();
    rc.setIndicatorString(0, "" + following);
   }
   
   //if it is, you are a bastard child with no use, so take an extreme
   //utilitarian approach
   //else
    //rc.suicide();
  }catch(Exception e){
   System.out.println("Caught exception:");
   e.printStackTrace();
  }
 }
 
 /*
  * Takes a string and interprets which direction to move in from the string.
  */
 private Direction msgDirection(String msgDir){
  try{
   if(msgDir.equalsIgnoreCase(Direction.NORTH.toString()))
    return Direction.NORTH;
   if(msgDir.equalsIgnoreCase(Direction.SOUTH.toString()))
    return Direction.SOUTH;
   if(msgDir.equalsIgnoreCase(Direction.EAST.toString()))
    return Direction.EAST;
   if(msgDir.equalsIgnoreCase(Direction.WEST.toString()))
    return Direction.WEST;
   if(msgDir.equalsIgnoreCase(Direction.NORTH_EAST.toString()))
    return Direction.NORTH_EAST;
   if(msgDir.equalsIgnoreCase(Direction.NORTH_WEST.toString()))
    return Direction.NORTH_WEST;
   if(msgDir.equalsIgnoreCase(Direction.SOUTH_EAST.toString()))
    return Direction.SOUTH_EAST;
   if(msgDir.equalsIgnoreCase(Direction.SOUTH_WEST.toString()))
    return Direction.SOUTH_WEST;
  }catch(Exception e){
   System.out.println("Caught exception:");
   e.printStackTrace();
  }
  return null;
 }
 private double furthestArchonDist(){
  double farArchonDist = 0;
  try{
   MapLocation[] archonLocs = rc.senseAlliedArchons();
   for(int i = 0; i < archonLocs.length; i++){
    double distFromArchon = distanceFrom(archonLocs[i]);
    if(distFromArchon > farArchonDist)
     farArchonDist = distFromArchon;
   }
  }catch(Exception e){
   System.out.println("Caught exception:");
   e.printStackTrace();
  }
  return farArchonDist;
 }
}
 
 
 
 
//runArchon();
/*
MapLocation nextLoc = rc.getLocation().add(rc.getDirection());
Robot nearBot = rc.senseGroundRobotAtLocation(nextLoc);
RobotInfo nearBotInfo;
if(nearBot != null){
 nearBotInfo = rc.senseRobotInfo(nearBot);
 if(nearBotInfo.type == RobotType.ARCHON ||
   nearBotInfo.type == RobotType.TOWER){
  rc.setDirection(rc.getDirection().rotateRight());
 }
 //archon heals any weakened bots
 else if(nearBotInfo.type.compareTo(RobotType.ARCHON) !=0 &&
   nearBotInfo.energonLevel < nearBotInfo.maxEnergon / 2){
  rc.transferEnergon(rc.getEnergonLevel()/1.5, nextLoc, nearBot.getRobotLevel());
 }
}
//archon spawns soldiers if not healing and if it has enough energon
else if(rc.getEnergonLevel() > RobotType.SOLDIER.spawnCost() &&
  rc.canSpawn() && !working && rc.getUnitCount(RobotType.SOLDIER) == 0 &&
  nearBot == null && rc.getTeamUnitCount() < 18){
 rc.spawn(RobotType.SOLDIER);
 rc.yield();
}
rc.yield();
*/
 