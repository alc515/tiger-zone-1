import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.IllegalFormatFlagsException;
import java.util.Scanner;
/* Needs: Tile.java, TileRetriever.java, tileset.txt, AI.java */

//Takes strings read from server and converts them to usable information
public class NetworkAdapter{
	private static final int NUM_EXPECTED_TILES = 77;
	private ArrayList<Tile> shuffledTiles = new ArrayList<Tile>();
	private static ArrayList<Tile> staticTiles = new ArrayList<Tile>();
	String pid; //Our playerID
	private String oid; // Opponent playerID
	private GameController[] gameControllers = new GameController[2];
	private String[] gameIDs = new String[2];
	private boolean gameIDhasBeenReset = false;
	private int moveNum = 1;
	Scanner s = new Scanner(System.in);
	
	//Creates array of local tiles from input file
	public NetworkAdapter(){
		if (staticTiles.isEmpty()){
			String filePath = "tileset.txt";
			staticTiles = new TileRetriever(filePath).tiles;
		}

		gameIDs[0] = "A";
		gameIDs[1] = "B";
	}

	//Parses the string and decides which method to call
	public void parseMatchProtocol(String message){
		String[] tokens = message.split(" ");
		String tileID, gid, x, y, orientation;
		switch(tokens[0]){
			case "BEGIN": //Create the game ctrlrs we are going to use
				gameControllers[0] = new GameController(NUM_EXPECTED_TILES * 2 + 1, NUM_EXPECTED_TILES * 2 + 1, staticTiles);
				gameControllers[1] = new GameController(NUM_EXPECTED_TILES * 2 + 1, NUM_EXPECTED_TILES * 2 + 1, staticTiles);

				break;
			case "STARTING": //Place starting tile on AI's board
				tileID = convertTileToID(tokens[3]);
				Tile startingTile = convertFromTileID(tileID);
				x = tokens[5];
				y = tokens[6];
				orientation = tokens[7];
				//Place on both AI boards
				int rotation = Integer.parseInt(orientation);
				rotation = convertDegreesToRotations(rotation);
				rotation = convertAnticlockwiseToClockwise(rotation);

				for (GameController gameController : gameControllers){
					Location tileLocation = gameController.getBoardCenter();
					tileLocation.Col += Integer.parseInt(x);
					tileLocation.Row += Integer.parseInt(y);
					gameController.processNetworkStart(startingTile, new MoveInformation(tileLocation, rotation, -1));
				}
				break;
				
			//Set opponent's player ID
			case "YOUR": 
				oid = tokens[4];
				break;
				
				// THE REMAINING TILES ARE : - should push array of tiles into AI here
			case "THE": //Create shuffled array of tiles
				initializeShuffledTiles(Integer.parseInt(tokens[2]), tokens);
				break;
		}
	}
	
	// Make Move string is parsed, should probably send AI the tile and GameID we get from parsing here
	public String parseMakeMove(String message){
		String[] tokens = message.split(" ");
		String tileID, gid;
		tileID = convertTileToID(tokens[12]);
		Tile nextTile = convertFromTileID(tileID);
		gid = tokens[5];
		
		if (gid.equals(gameIDs[0])){
			return processMoveInformation(gameControllers[0].processNetworkedPlayerMove(nextTile), gid, tileID);
		}
		else if (gid.equals(gameIDs[1])){
			return processMoveInformation(gameControllers[1].processNetworkedPlayerMove(nextTile), gid, tileID);
		}
		else if (!gameIDhasBeenReset){
			gameIDs[0] = gid;
			gameIDhasBeenReset = true;
			parseMakeMove(message);
		}
		else if (gameIDhasBeenReset){
			gameIDs[1] = gid;
			parseMakeMove(message);
		}

		return "";
	}
	// Update gameboard string for both games is parsed - should update gameboards for our gamemove and opponent's game move on AI's representation of board
	public void parseUpdateGameBoard(String message){
		String[] tokens = message.split(" ");
		String tileID, gid, x, y, orientation;
		//Read in move that was legally made for both players & place tile on board
		gid = tokens[1];
		String playerID = tokens[5]; //player that made the move
		if (tokens[6].equals("PLACED")){
			Tile tilePlaced = convertFromTileID(convertTileToID(tokens[7]));
			x = tokens[9];
			y = tokens[10];
			orientation = tokens[11];
			String piece = "";
			String zone = "";
			if(!tokens[12].equals("NONE")){
				piece = tokens[12];
				zone = tokens[13];
			}

			int gameIndex = (gid.equals(gameIDs[0])) ? 0 : 1;
			int activePlayer = (playerID.equals(pid)) ? 0 : 1;
			int rotation = convertAnticlockwiseToClockwise(convertDegreesToRotations(Integer.parseInt(orientation)));
			Location tileLocation = gameControllers[gameIndex].getBoardCenter();
			tileLocation.Col += Integer.parseInt(x);
			tileLocation.Row += Integer.parseInt(y);


			if (zone.equals("")) {
				gameControllers[gameIndex].processConfirmedNetworkedMove(tilePlaced, new MoveInformation(tileLocation, rotation, -1), activePlayer);
			}
			else{
				int meepleLocation = convertMeepleZoneToNode(tilePlaced, rotation, zone);
				gameControllers[gameIndex].processConfirmedNetworkedMove(tilePlaced, new MoveInformation(tileLocation, rotation, meepleLocation), activePlayer);
			}


		}
		else{
		//Game was forfeited from an illegal move
		//End AI for this match
		}
	}

	private int convertMeepleZoneToNode(Tile tilePlaced, int rotation, String zone) {
		int location = -1;
		tilePlaced.rotateClockwise(rotation);
		switch (Integer.parseInt(zone)){
			case 1:
				//TODO: Refactor this into a function.. case 1 == 3 == 6 == 8, except for which edges and what location they result in
				if (tilePlaced.edges[0].nodes[2].featureType == tilePlaced.edges[1].nodes[0].featureType &&
						tilePlaced.edges[0].nodes[2].featureType.isSameFeature(FeatureTypeEnum.City) &&
						!tilePlaced.citiesAreIndependent){
					location = 3;
				}
				else{
					if(tilePlaced.edges[0].nodes[2].featureType == FeatureTypeEnum.Field){
						location = 2;
					}
					else if (tilePlaced.edges[1].nodes[0].featureType == FeatureTypeEnum.Field){
						location = 3;
					}
					else{ //JLLJ- case
						location = 6;
					}
				}
				break;
			case 2:
				location = 4;
				break;
			case 3:
				if (tilePlaced.edges[1].nodes[2].featureType == tilePlaced.edges[2].nodes[0].featureType &&
						tilePlaced.edges[1].nodes[2].featureType.isSameFeature(FeatureTypeEnum.City) &&
						!tilePlaced.citiesAreIndependent){
					location = 6;
				}
				else{
					if(tilePlaced.edges[1].nodes[2].featureType == FeatureTypeEnum.Field){
						location = 5;
					}
					else if (tilePlaced.edges[2].nodes[0].featureType == FeatureTypeEnum.Field){
						location = 6;
					}
					else{ //JLLJ- case
						location = 9;
					}
				}
				break;
			case 4:
				location = 1;
				break;
			case 5:
				if (tilePlaced.hasMonastery) {
					location = 12;
				}
				else {
					if (tilePlaced.edges[2].nodes[1].featureType.isSameFeature(FeatureTypeEnum.Road)){
						location = 7;
					}
					else if (tilePlaced.edges[3].nodes[1].featureType.isSameFeature(FeatureTypeEnum.Road)) {
						location = 10;
					}
					else if (tilePlaced.edges[2].nodes[1].featureType.isSameFeature(FeatureTypeEnum.Field)){
						location = 7;
					}
					else {
						location = 10;
					}
				}
				break;
			case 6:
				location = 7;
				break;
			case 7:
				if (tilePlaced.edges[2].nodes[2].featureType == tilePlaced.edges[3].nodes[0].featureType &&
						tilePlaced.edges[2].nodes[2].featureType.isSameFeature(FeatureTypeEnum.City) &&
						!tilePlaced.citiesAreIndependent){
					location = 9;
				}
				else{
					if(tilePlaced.edges[2].nodes[2].featureType == FeatureTypeEnum.Field){
						location = 8;
					}
					else if (tilePlaced.edges[3].nodes[0].featureType == FeatureTypeEnum.Field){
						location = 9;
					}
					else{ //JLLJ- case
						location = 0;
					}
				}
				break;
			case 8:
				location = 10;
				break;
			case 9:
				if (tilePlaced.edges[3].nodes[2].featureType == tilePlaced.edges[0].nodes[0].featureType &&
						tilePlaced.edges[3].nodes[2].featureType.isSameFeature(FeatureTypeEnum.City) &&
						!tilePlaced.citiesAreIndependent){
					location = 0;
				}
				else{
					if(tilePlaced.edges[3].nodes[2].featureType == FeatureTypeEnum.Field){
						location = 11;
					}
					else if (tilePlaced.edges[0].nodes[0].featureType == FeatureTypeEnum.Field){
						location = 0;
					}
					else{ //JLLJ- case
						location = 3;
					}
				}
				break;
			default:
				throw new IllegalStateException();
		}

		tilePlaced.rotateClockwise(-rotation);
		return location;
	}

	public void endGame(){
		//Round: Wait for next match, Challenges: wait for next challenge
		//Empty shuffledTiles array
	}

	String processMoveInformation(MoveInformation moveInfo, String gid, String tileID){
		String messageToReturn;

		if (moveInfo.tileLocation.isEqual(new Location(-1, -1))){
			messageToReturn = formatMove(3, gid, tileID, -1, -1, -1, -1);
		}
		else {
			int rotation = moveInfo.tileRotation;
			rotation = convertClockwisetoAnticlockwise(rotation);
			rotation = convertRotationsToDegrees(rotation);

			int x = moveInfo.tileLocation.Col - gameControllers[0].getBoardCenter().Col;
			int y = moveInfo.tileLocation.Row - gameControllers[0].getBoardCenter().Row;

			if(moveInfo.meepleLocation == -1){
				messageToReturn = formatMove(0, gid, tileID, x, y, rotation, moveInfo.meepleZone);
			}
			else{
				messageToReturn = formatMove(2, gid, tileID, x, y, rotation, moveInfo.meepleZone);
			}

		}

		return messageToReturn;
	}

	//Creates message to be sent through the network for a tile placement
	//messageStatus denotes type of move
	public String formatMove(int messageStatus, String gid, String tileID, int x, int y, int orientation, int zone){
		String message = "";
		switch(messageStatus){
			case 0: message = "GAME " + gid + " MOVE " + moveNum + " PLACE " + convertTileToString(tileID) + " AT " + x + " " + y + " " + orientation + " NONE";
					break;
			case 1: message = "GAME " + gid + " MOVE " + moveNum + " PLACE " + convertTileToString(tileID) + " AT " + x + " " + y + " " + orientation + " CROCODILE";
					break;
			case 2: message = "GAME " + gid + " MOVE " + moveNum + " PLACE " + convertTileToString(tileID) + " AT " + x + " " + y + " " + orientation + " TIGER " + zone;
					break;
			case 3: message = "GAME " + gid + " MOVE " + moveNum + " TILE " + convertTileToString(tileID) + " UNPLACEABLE PASS";
					break;
			case 4: message = "GAME " + gid + " MOVE " + moveNum + " TILE " + convertTileToString(tileID) + " UNPLACEABLE RETRIEVE TIGER AT " + x + " " + y;
					break;
			case 5: message = "GAME " + gid + " MOVE " + moveNum + " TILE " + convertTileToString(tileID) + " UNPLACEABLE ADD ANOTHER TIGER TO " + x + " " + y;
					break;
		}
		moveNum++;
		return message;
	}

	//Takes string from server and creates the shuffled tile array from string
	private void initializeShuffledTiles(int numberOfTiles, String[] input){
		for(int i = 0; i < numberOfTiles; i++){
			shuffledTiles.add(convertFromTileID(convertTileToID(input[6 + i])));
		}
	}

	private int convertDegreesToRotations(int degrees){
		return degrees / 90;
	}

	private int convertAnticlockwiseToClockwise(int rotations){
		return Math.abs(rotations - 4);
	}

	private int convertClockwisetoAnticlockwise(int rotations){
		return Math.abs(rotations - 4);
		// I know these are the same but...... I can't think of a good bidirectional name like switchCardinality or something
	}

	private int convertRotationsToDegrees(int rotations){
		return rotations * 90;
	}



	//Converts tile string from server to local tile arrangement
	private String convertTileToID(String tileString){
		switch(tileString){
			case "JJJJ-": return ("A");
			case "JJJJX": return ("B");
			case "JJTJX": return ("C");
			case "TTTT-": return ("D");
			case "TJTJ-": return ("E");
			case "TJJT-": return ("F");
			case "TJTT-": return ("G");
			case "LLLL-": return ("H");
			case "JLLL-": return ("I");
			case "LLJJ-": return ("J");
			case "JLJL-": return ("K");
			case "LJLJ-": return ("L");
			case "LJJJ-": return ("M");
			case "JLLJ-": return ("N");
			case "TLJT-": return ("O");
			case "TLJTP": return ("P");
			case "JLTT-": return ("Q");
			case "JLTTB": return ("R");
			case "TLTJ-": return ("S");
			case "TLTJD": return ("T");
			case "TLLL-": return ("U");
			case "TLTT-": return ("V");
			case "TLTTP": return ("W");
			case "TLLT-": return ("X");
			case "TLLTB": return ("Y");
			case "LJTJ-": return ("Z");
			case "LJTJD": return ("a");
			case "TLLLC": return ("b");
			default: return "";
		}
	}

	//Converts tile string from local tile arrangement to server tile
	private String convertTileToString(String tileID){
		switch(tileID){
			case "A": return ("JJJJ-");
			case "B": return ("JJJJX");
			case "C": return ("JJTJX");
			case "D": return ("TTTT-");
			case "E": return ("TJTJ-");
			case "F": return ("TJJT-");
			case "G": return ("TJTT-");
			case "H": return ("LLLL-");
			case "I": return ("JLLL-");
			case "J": return ("LLJJ-");
			case "K": return ("JLJL-");
			case "L": return ("LJLJ-");
			case "M": return ("LJJJ-");
			case "N": return ("JLLJ-");
			case "O": return ("TLJT-");
			case "P": return ("TLJTP");
			case "Q": return ("JLTT-");
			case "R": return ("JLTTB");
			case "S": return ("TLTJ-");
			case "T": return ("TLTJD");
			case "U": return ("TLLL-");
			case "V": return ("TLTT-");
			case "W": return ("TLTTP");
			case "X": return ("TLLT-");
			case "Y": return ("TLLTB");
			case "Z": return ("LJTJ-");
			case "a": return ("LJTJD");
			case "b": return ("TLLLC");
			default: return "";
		}
	}

	private Tile convertFromTileID(String tileID){
		switch(tileID){
			case "A": return (staticTiles.get(0));
			case "B": return (staticTiles.get(1));
			case "C": return (staticTiles.get(5));
			case "D": return (staticTiles.get(7));
			case "E": return (staticTiles.get(8));
			case "F": return (staticTiles.get(16));
			case "G": return (staticTiles.get(25));
			case "H": return (staticTiles.get(29));
			case "I": return (staticTiles.get(30));
			case "J": return (staticTiles.get(34));
			case "K": return (staticTiles.get(39));
			case "L": return (staticTiles.get(42));
			case "M": return (staticTiles.get(45));
			case "N": return (staticTiles.get(50));
			case "O": return (staticTiles.get(52));
			case "P": return (staticTiles.get(53));
			case "Q": return (staticTiles.get(55));
			case "R": return (staticTiles.get(56));
			case "S": return (staticTiles.get(58));
			case "T": return (staticTiles.get(61));
			case "U": return (staticTiles.get(63));
			case "V": return (staticTiles.get(64));
			case "W": return (staticTiles.get(65));
			case "X": return (staticTiles.get(67));
			case "Y": return (staticTiles.get(70));
			case "Z": return (staticTiles.get(72));
			case "a": return (staticTiles.get(73));
			case "b": return (staticTiles.get(75));
			default: return null;
		}
	}
}