import java.lang.reflect.Array;
import java.util.*;

public class ScoreController {
    
    // W,N,E,S,NW,NE,SW,SE
    private static int[] dxFULL = {0,-1,0,1,-1,-1,1,1};
    private static int[] dyFULL = {-1,0,1,0,-1,1,-1,1};
    
    GameBoard localBoard;
    int player1Score;
    int player2Score;
    private static int lakeIdentifier = 0;
    private static int denIdentifier = 0;
    private static int roadIdentifier = 0;
    ArrayList<Tile> gameTileReference;
    
    ScoreController(ArrayList<Tile> gameTileReference, Location boardDimensions){
        this.player1Score = 0;
        this.player2Score = 0;
        this.gameTileReference = gameTileReference;
        this.localBoard = new GameBoard(boardDimensions.Row, boardDimensions.Col);
    }
    
    
    ScoreController(ArrayList<Tile> gameTileReference, GameBoard board){
        this.player1Score = 0;
        this.player2Score = 0;
        this.gameTileReference = gameTileReference;
        this.localBoard = board;
    }
    
    ArrayList<Meeple> processConfirmedMove(Tile confirmedTile, MoveInformation moveInfo, int playerConfirmed, boolean usingLocalBoard){
        
        
        ArrayList<Meeple> meeplesToReturn = new ArrayList<>();
        
        meeplesToReturn.addAll(handleDens(confirmedTile, moveInfo));
        
        for (Edge edge : confirmedTile.edges){
            if ((edge.nodes[1].featureType == FeatureTypeEnum.Road || edge.nodes[1].featureType == FeatureTypeEnum.RoadEnd)
                && isRoadScorable(edge.nodes[1]))
            {
                meeplesToReturn.addAll(scoreRoad(edge.nodes[1]));
            }
            
            for(int cornerNodeIndex = 0; cornerNodeIndex < edge.nodes.length; cornerNodeIndex += 2){
                ArrayList<Node> cycleBuffer = getWallCycleNodes(edge.nodes[cornerNodeIndex]);
                
                if (cycleBuffer.isEmpty()) continue;
                else {
                    meeplesToReturn.addAll(scoreCompleteCity(edge.nodes[cornerNodeIndex]));
                }
            }
        }
        return meeplesToReturn;
    }
    
    void processFreedMeeple(int ownerID, int meepleID){
        localBoard.freeMeeple(ownerID, meepleID);
    }
    
    public ArrayList<Meeple> handleDens(Tile confirmedTile, MoveInformation moveInfo){
        ArrayList<Meeple> meeplesToReturn = new ArrayList<>();
        //**************************************************************************************************************
        //FACILITATE CHECK FOR ANY SURROUNDING DENS AND CHECK IF THIS IS A SCORABLE TILE IN CASE IT IS A TILE WITH A DEN
        int row = moveInfo.tileLocation.Row;
        int col = moveInfo.tileLocation.Col;
        
        boolean fullySurrounded = true;
        
        ArrayList<Location> denLocations = new ArrayList<Location>();
        for(int direction = 0; direction < 8; direction++){
            if(row + dxFULL[direction] >= 0 && row + dxFULL[direction] < localBoard.board.length && col + dyFULL[direction] >= 0 && col + dyFULL[direction] < localBoard.board[0].length){ //Checks within board boundary
                if(localBoard.board[row+dxFULL[direction]][col+dyFULL[direction]] == null) fullySurrounded = false;
                else if(localBoard.board[row+dxFULL[direction]][col+dyFULL[direction]].hasMonastery) denLocations.add(new Location(row+dxFULL[direction],col+dyFULL[direction]));
            }
        }
        
        if(fullySurrounded && confirmedTile.hasMonastery && confirmedTile.middle.meeple != null){
            scoreCompleteDen(confirmedTile.middle);
            meeplesToReturn.add(new Meeple(confirmedTile.middle.meeple.owner, confirmedTile.middle.meeple.ID));
        }
        
        while(!denLocations.isEmpty()){
            Location buffer = denLocations.remove(0);
            row = buffer.Row;
            col = buffer.Col;
            
            fullySurrounded = true;
            for(int direction = 0; direction < 8; direction++){
                if(row + dxFULL[direction] >= 0 && row + dxFULL[direction] < localBoard.board.length && col + dyFULL[direction] >= 0 && col + dyFULL[direction] < localBoard.board[0].length){ //Checks within board boundary
                    if(localBoard.board[row+dxFULL[direction]][col+dyFULL[direction]] == null) fullySurrounded = false;
                }
            }
            
            //kinda redundant to check if these have monastery here since I am adding to the arrayList only tiles with monasteries
            if(fullySurrounded && localBoard.board[row][col].hasMonastery && localBoard.board[row][col].middle.meeple != null){
                scoreCompleteDen(localBoard.board[row][col].middle);
                meeplesToReturn.add(new Meeple(confirmedTile.middle.meeple.owner, confirmedTile.middle.meeple.ID));
            }
            
        }
        
        return meeplesToReturn;
    }
    
    void scoreField(Node start){
        ArrayDeque<Node> nodeQueue = new ArrayDeque<>();
        ArrayDeque<Node> visitedNodes = new ArrayDeque<>();
        
        HashSet<Integer> uniqueCities = new HashSet<Integer>();
        HashSet<Integer> uniqueDens = new HashSet<Integer>();
        
        int[] meeplesReturned = new int[2];
        
        nodeQueue.add(start);
        
        while (!nodeQueue.isEmpty()){
            Node currNode = nodeQueue.removeFirst();
            visitedNodes.add(currNode);
            currNode.visited = true;
            if (currNode.meeple != null) meeplesReturned[currNode.meeple.owner]++;
            
            for (Node neighbor : currNode.neighbors){
                if (!neighbor.visited){
                    if (neighbor.featureType == FeatureTypeEnum.Field){
                        nodeQueue.add(neighbor);
                    }
                    else if (neighbor.featureType == FeatureTypeEnum.Monastery && neighbor.featureID != -1){
                        if (!uniqueDens.contains(neighbor.featureID)){
                            uniqueDens.add(neighbor.featureID);
                        }
                    }
                    else if (neighbor.featureType == FeatureTypeEnum.Wall && neighbor.featureID != -1){
                    	
                    	System.out.println("MET A CITY IN TRAVERSAL");
                    	
                        if (!uniqueCities.contains(neighbor.featureID)){
                            uniqueCities.add(neighbor.featureID);
                        }
                    }
                }
            }
        }
        
        if(meeplesReturned[0] != 0 || meeplesReturned[1] != 0){
            int featureValue = 3*uniqueCities.size() + 5*uniqueDens.size();
            if(meeplesReturned[0] == meeplesReturned[1]){
            	
            	
                this.player1Score += featureValue;
                this.player2Score += featureValue;
            }
            else if(meeplesReturned[0] > meeplesReturned[1]){
                this.player1Score += featureValue;
            }
            else{
                this.player2Score += featureValue;
            }
        }
    }
    
    void scoreCompleteDen(Node den){
        //handleDens will take care of returning meeples appropriately
        denIdentifier++;
        den.featureID = denIdentifier;
        
        if(den.meeplePlacedInFeature && den.meeple != null){
            if(den.meeple.owner == 0){
                player1Score += 9;
            }
            else if(den.meeple.owner == 1){
                player2Score += 9;
            }
        }
        
    }
    
    void scoreIncompleteDen(Location denLocation){
    	
        int row = denLocation.Row;
        int col = denLocation.Col;
        
        int numSurroundingTiles = 0;
        for(int direction = 0; direction < 8; direction++){
            if(row + dxFULL[direction] >= 0 && row + dxFULL[direction] < localBoard.board.length && col + dyFULL[direction] >= 0 && col + dyFULL[direction] < localBoard.board[0].length){ //Checks within board boundary
                if(localBoard.board[row+dxFULL[direction]][col+dyFULL[direction]] != null) numSurroundingTiles++;
            }
        }
        
        
        if(localBoard.board[row][col].middle.meeple.owner == 0){
            player1Score += numSurroundingTiles + 1;
        }
        else if (localBoard.board[row][col].middle.meeple.owner == 1){
            player2Score += numSurroundingTiles + 1;
        }
    }
    
    
    public ArrayList<Meeple> scoreRoad(Node start){
        
        System.out.println("REACHED SCORING ROAD");
        
        ArrayList<Meeple> meeplesToReturn = new ArrayList<>();
        int[] meeplesReturned = new int[2];
        roadIdentifier++;
        start.featureID = roadIdentifier;
        
        HashSet<Integer> uniqueTiles = new HashSet<Integer>();
        HashSet<Integer> uniqueAnimals = new HashSet<Integer>();
        
        Queue<Node> bfsQueue = new LinkedList<Node>();
        bfsQueue.add(start);
        
        uniqueTiles.add(start.owningTileId);
        if(start.meeple != null){
            meeplesReturned[start.meeple.owner]++;
            meeplesToReturn.add(new Meeple(start.meeple.owner, start.meeple.ID));
        }
        
        uniqueTiles.add(start.owningTileId);
        if(start.meeple != null){
            meeplesReturned[start.meeple.owner]++;
            meeplesToReturn.add(new Meeple(start.meeple.owner, start.meeple.ID));
        }
        
        while(!bfsQueue.isEmpty()){
            Node buffer = bfsQueue.poll();
            uniqueTiles.add(buffer.owningTileId);
            buffer.visited = true;
            buffer.featureID = roadIdentifier;
            for(int i = 0; i < buffer.neighbors.size(); i++){
                if(!buffer.neighbors.get(i).visited &&
                   (buffer.neighbors.get(i).featureType.toChar() == 'R' || buffer.neighbors.get(i).featureType.toChar() == 'E'))
                {
                    bfsQueue.add(buffer.neighbors.get(i));
                    uniqueTiles.add(buffer.neighbors.get(i).owningTileId);
                    
                    if(buffer.neighbors.get(i).meeple != null){
                        
                        meeplesReturned[buffer.neighbors.get(i).meeple.owner]++;
                        meeplesToReturn.add(new Meeple(buffer.neighbors.get(i).meeple.owner, buffer.neighbors.get(i).meeple.ID));
                        
                    }
                }
            }
        }
        
        if(meeplesReturned[0] != 0 || meeplesReturned[1] != 0){
            if(meeplesReturned[0] == meeplesReturned[1]){
                this.player1Score += uniqueTiles.size();
                this.player2Score += uniqueTiles.size();
            }
            else if(meeplesReturned[0] > meeplesReturned[1]){
                this.player1Score += uniqueTiles.size();
            }
            else{
                this.player2Score += uniqueTiles.size();
            }
        }
        
        
        return meeplesToReturn;
    }
    
    boolean isRoadScorable(Node start){
        if (start.featureID != -1) return false; //Already been scored, skip
        
        ArrayDeque<Node> nodeQueue = new ArrayDeque<>();
        ArrayDeque<Node> nodeCameFrom = new ArrayDeque<>(); //This goes hand in hand with nodequeue and indicates the previous node for each node in the nodequeue.
        ArrayDeque<Node> visitedNodes = new ArrayDeque<>();
        ArrayList<Integer> visitedTiles = new ArrayList<>();
        int endpointsReached = 0;
        boolean cycleDetected = false;
        
        nodeQueue.add(start);
        nodeCameFrom.add(start);
        if (start.featureType == FeatureTypeEnum.RoadEnd) endpointsReached++;
        
        while (!nodeQueue.isEmpty()){
            Node currNode = nodeQueue.removeFirst();
            Node currParent = nodeCameFrom.removeFirst();
            visitedNodes.add(currNode);
            if (!visitedTiles.contains(currNode.owningTileId)){
                visitedTiles.add(currNode.owningTileId);
            }
            
            for (Node neighbor : currNode.neighbors){
                if (visitedNodes.contains(neighbor)){
                    if (neighbor != currParent) {
                        System.out.println ("road cycle");
                        cycleDetected = true;
                    }
                    continue;
                }
                
                if (neighbor.featureType == FeatureTypeEnum.Road){
                    nodeQueue.add(neighbor);
                    nodeCameFrom.add(currNode);
                }
                else if (neighbor.featureType == FeatureTypeEnum.RoadEnd){
                    System.out.println("reached endpt");
                    endpointsReached++;
                    if (!visitedTiles.contains(neighbor.owningTileId)){ //this should never be false, since endpoints are always only one node in
                        visitedTiles.add(neighbor.owningTileId);
                    }
                }
            }
        }
        // Commented out to free console space.
        //System.out.println("endpoints: " + endpointsReached);
        
        return ((endpointsReached == 0 && cycleDetected) || endpointsReached == 2); //Either 2 endpoints need to have been reached or a cycle was detected.
        /* Old code used to return tile ids instead of boolean
         switch (endpointsReached){
         case 0:
         if (cycleDetected) return visitedTiles;
         else return new ArrayList<>();
         case 1: return new ArrayList<>(); // if only one endpoint was found, cant be a cycle and isnt complete
         case 2: return visitedTiles;
         default: throw new IllegalStateException();
         }
         */
    }
    
    public ArrayList<Meeple> scoreIncompleteCity(Node start){
        
        ArrayList<Meeple> meeplesToReturn = new ArrayList<>();
        lakeIdentifier++;
        int[] meeplesReturned = new int[2];
        
        
        HashSet<Integer> uniqueTiles = new HashSet<Integer>();
        HashSet<Integer> uniqueAnimals = new HashSet<Integer>();
        
        Queue<Node> bfsQueue = new LinkedList<Node>();
        bfsQueue.add(start);
        
        uniqueTiles.add(start.owningTileId);
        if(start.meeple != null){
            meeplesReturned[start.meeple.owner]++;
            meeplesToReturn.add(new Meeple(start.meeple.owner, start.meeple.ID));
        }
        
        while(!bfsQueue.isEmpty()){
            Node buffer = bfsQueue.poll();
            uniqueTiles.add(buffer.owningTileId);
            buffer.visited = true;
            buffer.featureID = lakeIdentifier;
            for(int i = 0; i < buffer.neighbors.size(); i++){
                if(!buffer.neighbors.get(i).visited &&
                   (buffer.neighbors.get(i).featureType.toChar() == 'W' || buffer.neighbors.get(i).featureType.toChar() == 'I'
                    ||buffer.neighbors.get(i).featureType.toChar() == 'C'))
                {
                    bfsQueue.add(buffer.neighbors.get(i));
                    uniqueTiles.add(buffer.neighbors.get(i).owningTileId);
                    
                    if(buffer.neighbors.get(i).meeple != null){
                        
                        meeplesReturned[buffer.neighbors.get(i).meeple.owner]++;
                        meeplesToReturn.add(new Meeple(buffer.neighbors.get(i).meeple.owner, buffer.neighbors.get(i).meeple.ID));
                        
                    }
                }
            }
        }
        
        if(meeplesReturned[0] != 0 || meeplesReturned[1] != 0){
            if(meeplesReturned[0] == meeplesReturned[1]){
                this.player1Score += uniqueTiles.size(); //*(1+uniqueAnimals.size());
                this.player2Score += uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
            else if(meeplesReturned[0] > meeplesReturned[1]){
                this.player1Score += uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
            else{
                this.player2Score += uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
        }
        
        
        return meeplesToReturn;
    }
    
    
    public ArrayList<Meeple> scoreCompleteCity(Node start){
        
        System.out.println("CALLED SCORING CITY");
        
        ArrayList<Meeple> meeplesToReturn = new ArrayList<>();
        lakeIdentifier++;
        int[] meeplesReturned = new int[2];
        
        
        HashSet<Integer> uniqueTiles = new HashSet<Integer>();
        HashSet<Integer> uniqueAnimals = new HashSet<Integer>();
        
        HashSet<Node> toUnvisitSet = new HashSet<Node>();
        Queue<Node> bfsQueue = new LinkedList<Node>();
        bfsQueue.add(start);
        toUnvisitSet.add(start);
        uniqueTiles.add(start.owningTileId);
        if(start.meeple != null){
            meeplesReturned[start.meeple.owner]++;
            meeplesToReturn.add(new Meeple(start.meeple.owner, start.meeple.ID));
        }
        
        while(!bfsQueue.isEmpty()){
            Node buffer = bfsQueue.poll();
            uniqueTiles.add(buffer.owningTileId);
            toUnvisitSet.add(buffer);
            buffer.visited = true;
            buffer.featureID = lakeIdentifier;
            for(int i = 0; i < buffer.neighbors.size(); i++){
                if(!buffer.neighbors.get(i).visited &&
                   (buffer.neighbors.get(i).featureType.toChar() == 'W' || buffer.neighbors.get(i).featureType.toChar() == 'I'
                    ||buffer.neighbors.get(i).featureType.toChar() == 'C'))
                {
                    bfsQueue.add(buffer.neighbors.get(i));
                    uniqueTiles.add(buffer.neighbors.get(i).owningTileId);
                    
                    if(buffer.neighbors.get(i).meeple != null){
                        
                        meeplesReturned[buffer.neighbors.get(i).meeple.owner]++;
                        meeplesToReturn.add(new Meeple(buffer.neighbors.get(i).meeple.owner, buffer.neighbors.get(i).meeple.ID));
                        
                    }
                }
            }
        }
        
        if(meeplesReturned[0] != 0 || meeplesReturned[1] != 0){
            if(meeplesReturned[0] == meeplesReturned[1]){
                this.player1Score += 2*uniqueTiles.size(); //*(1+uniqueAnimals.size());
                this.player2Score += 2*uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
            else if(meeplesReturned[0] > meeplesReturned[1]){
                this.player1Score += 2*uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
            else{
                this.player2Score += 2*uniqueTiles.size();// *(1+uniqueAnimals.size());
            }
        }
        
        for(Node nodeToUnvisitBuffer : toUnvisitSet){
        	nodeToUnvisitBuffer.visited = false;
        }
        
        
        return meeplesToReturn;
    }
    
    public ArrayList<Node> getWallCycleNodes(Node start){
        if (start.featureID != -1) return new ArrayList<>(); //Already been scored, skip
        
        //attempting to find a wall cycle from a non-wall node
        if (start.featureType != FeatureTypeEnum.InnerWall && start.featureType != FeatureTypeEnum.Wall) return new ArrayList<>();
        
        //tentative list of nodesInCycle
        ArrayList<Node> nodesInCycle = new ArrayList<Node>();
        
        //first mark start as visited, and add it in the cycle list
        start.visited = true;
        nodesInCycle.add(start);
        //System.out.println("START\n" + start.featureType.toChar() + " " + start.owningTileId + " " + start.hashCode()); Freeing console space, uncomment as needed.
        
        //get one of start node's neighbors of the same feature type and add it in the cycle list
        for(int neighborIndex = 0; neighborIndex < start.neighbors.size(); neighborIndex++)
        {
            if(start.neighbors.get(neighborIndex).featureType.toChar() == 'W' || start.neighbors.get(neighborIndex).featureType.toChar() == 'I')
            {
                nodesInCycle.add(start.neighbors.get(neighborIndex));
                // Freeing console space, uncomment as needed.
                //System.out.println(start.neighbors.get(neighborIndex).featureType.toChar() + " " + start.neighbors.get(neighborIndex).owningTileId + " " + start.neighbors.get(neighborIndex).hashCode());
                start.neighbors.get(neighborIndex).visited = true;
                break;
            }
            
        }
        
        //if there were no neighbors of the same feature type cycle is impossible,
        // mark start node as unvisited and return an empty cycle List
        if(nodesInCycle.size() < 2)
        {
            nodesInCycle.remove(0).visited = false;				//this would be the starting node; remove it from the list, unvisit, and return
            return nodesInCycle;
        }
        
        //get last node added (one node away from starting node)
        Node oneLevelDepthNode = nodesInCycle.get(nodesInCycle.size()-1);
        
        //get one of the one level depth node's neighbors of the same feature type and add it in the cycle list
        for(int neighborIndex = 0; neighborIndex < oneLevelDepthNode.neighbors.size(); neighborIndex++)
        {
            if(!oneLevelDepthNode.neighbors.get(neighborIndex).visited &&
               (oneLevelDepthNode.neighbors.get(neighborIndex).featureType.toChar() == 'W'
                ||oneLevelDepthNode.neighbors.get(neighborIndex).featureType.toChar() == 'I'))
            {
                nodesInCycle.add(oneLevelDepthNode.neighbors.get(neighborIndex));
                oneLevelDepthNode.neighbors.get(neighborIndex).visited = true;
                break;
            }
        }
        
        //if there were no neighbors of the same feature type cycle is impossible,
        //mark everything in cycle List unvisited and return an empty List
        if(nodesInCycle.size() < 3){
            while(!nodesInCycle.isEmpty()){
                nodesInCycle.remove(0).visited = false;
            }
            return nodesInCycle;						//empty
        }
        
        //get last node added(two nodes away from starting node)
        //we will start searching at this point, so we will remove it from the nodesInCycle list
        //so that it doesn't exist two times in the list when it gets added back in the traversal
        Node twoLevelDepthNode = nodesInCycle.remove(nodesInCycle.size()-1);
        
        //create queue to traverse along edges
        Queue<Node> queue = new LinkedList<Node>();
        queue.add(twoLevelDepthNode);
        
        boolean cycle = false;
        while(!queue.isEmpty()){
            Node buffer = queue.poll();
            buffer.visited = true;
            nodesInCycle.add(buffer);
            // Commented out to free console space.
            //System.out.println(buffer.featureType.toChar() + " "+ buffer.owningTileId + " " + buffer.hashCode());
            for(int i = 0; i < buffer.neighbors.size(); i++){
                if(buffer.neighbors.get(i).visited && buffer.neighbors.get(i) == start)
                {
                    cycle = true;
                }
                else if(!buffer.neighbors.get(i).visited &&
                        (buffer.neighbors.get(i).featureType.toChar() == 'W' || buffer.neighbors.get(i).featureType.toChar() == 'I'))
                {
                    queue.add(buffer.neighbors.get(i));
                }
            }
        }
        
        if(!cycle){
            while(!nodesInCycle.isEmpty()){
                nodesInCycle.remove(0).visited = false;
            }
            return nodesInCycle;
        }
        
        //INNER WALL CYCLE EDGE CASE BEGIN ******************************************************************
        boolean innerWallCycle = true;
        if(cycle){
            for(int nodeIndex = 0; nodeIndex < nodesInCycle.size(); nodeIndex++){
                if(nodesInCycle.get(nodeIndex).featureType.toChar() !='I'){
                    innerWallCycle = false;
                    break;
                }
            }
        }
        if(innerWallCycle){
            System.out.println("INNER WALL CYCLE DETECTED");
            while(!nodesInCycle.isEmpty()){
                nodesInCycle.remove(0).visited = false;
            }
        }
        //INNER WALL CYCLE EDGE CASE END ******************************************************************
        
        System.out.println("CYCLE");
        if(cycle){
            for(Node nodeToUnvisit : nodesInCycle){
                nodeToUnvisit.visited = false;
            }
        }
        
        return nodesInCycle;		//actual cycle;
        
    }
    
    public ArrayList<Node> getRoadCycleNodes(Node start){
        
        //tentative list of nodesInCycle
        ArrayList<Node> nodesInCycle = new ArrayList<Node>();
        
        //first mark start as visited, and add it in the cycle list
        start.visited = true;
        nodesInCycle.add(start);
        
        //get one of start node's neighbors of the same feature type and add it in the cycle list
        for(int neighborIndex = 0; neighborIndex < start.neighbors.size(); neighborIndex++)
        {
            if(start.neighbors.get(neighborIndex).featureType.toChar() == 'R')
            {
                nodesInCycle.add(start.neighbors.get(neighborIndex));
                start.neighbors.get(neighborIndex).visited = true;
                break;
            }
            
        }
        
        //if there were no neighbors of the same feature type cycle is impossible,
        // mark start node as unvisited and return an empty cycle List
        if(nodesInCycle.size() < 2)
        {
            nodesInCycle.remove(0).visited = false;				//this would be the starting node; remove it from the list, unvisit, and return
            return nodesInCycle;
        }
        
        //get last node added (one node away from starting node)
        Node oneLevelDepthNode = nodesInCycle.get(nodesInCycle.size()-1);
        
        //get one of the one level depth node's neighbors of the same feature type and add it in the cycle list
        for(int neighborIndex = 0; neighborIndex < oneLevelDepthNode.neighbors.size(); neighborIndex++)
        {
            if(!oneLevelDepthNode.neighbors.get(neighborIndex).visited && oneLevelDepthNode.neighbors.get(neighborIndex).featureType.toChar() == 'R')
            {
                nodesInCycle.add(oneLevelDepthNode.neighbors.get(neighborIndex));
                oneLevelDepthNode.neighbors.get(neighborIndex).visited = true;
                break;
            }
        }
        
        //if there were no neighbors of the same feature type cycle is impossible,
        //mark everything in cycle List unvisited and return an empty List
        if(nodesInCycle.size() < 3){
            while(!nodesInCycle.isEmpty()){
                nodesInCycle.remove(0).visited = false;
            }
            return nodesInCycle;						//empty
        }
        
        //get last node added(two nodes away from starting node)
        //we will start searching at this point, so we will remove it from the nodesInCycle list
        //so that it doesn't exist two times in the list when it gets added back in the traversal
        Node twoLevelDepthNode = nodesInCycle.remove(nodesInCycle.size()-1);
        
        //create queue to traverse along edges
        Queue<Node> queue = new LinkedList<Node>();
        queue.add(twoLevelDepthNode);
        
        boolean cycle = false;
        while(!queue.isEmpty()){
            Node buffer = queue.poll();
            buffer.visited = true;
            nodesInCycle.add(buffer);
            for(int i = 0; i < buffer.neighbors.size(); i++){
                if(buffer.neighbors.get(i).visited && buffer.neighbors.get(i) == start){
                    cycle = true;
                }
                else if(!buffer.neighbors.get(i).visited && buffer.neighbors.get(i).featureType.toChar() == 'R'){
                    queue.add(buffer.neighbors.get(i));
                }
            }
        }
        
        if(!cycle){
            while(!nodesInCycle.isEmpty()){
                nodesInCycle.remove(0).visited = false;
            }
        }
        return nodesInCycle;
    }
}