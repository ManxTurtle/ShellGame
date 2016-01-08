import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

// // Copyright 2015 theaigames.com (developers@theaigames.com)

//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at

//        http://www.apache.org/licenses/LICENSE-2.0

//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//  
//    For the full copyright and license information, please view the LICENSE
//    file that was distributed with this source code.

 
/**
 * BotStarter class
 * 
 * Magic happens here. You should edit this file, or more specifically
 * the makeTurn() method to make your bot do more than random moves.
 * 
 * @author Jim van Eeden <jim@starapple.nl>, Joost de Meij <joost@starapple.nl>
 */

public class ManxFourBot
	extends
		Thread
	implements
		FourState.AfterParseListener
{
	private static final double[] THINK_RATIO = new double[] {6 / 28.0, 10 / 28.0, 15 / 28.0, 21 / 28.0, 1.0};
	private static final double[] EASY_MARGIN = new double[] {1.06, 1.045, 1.03, 1.015, 1.0};
	private static final String[] EASY_DESCRIPTION = new String[] {"eM(1.060)-", "eM(1.045)-", "eM(1.030)-", "eM(1.015)-", "eM(1.000)-"};
	private static final double EARLY_MOVE_BONUS = 1.045;
	
    public static final Timer time = new Timer();
	public static int round = 0;
    public static byte botId = 0;
	
	private MonteCarloNode mind;
    private ReentrantLock betweenTurnCycle = new ReentrantLock();
	private static final Thread mainThread = Thread.currentThread();
	private byte opponentLastMove = FourState.NO_PRECEDENT;
	
	public ManxFourBot() {
		super(null, null, "ImpatientThread");
		FourState.afterParseListeners.add(this);
	} // end constructor
    
	/**
	 * Provided purely for testing purposes.
	 * It prevents the Thread from starting up.
	 * @version 67
	 * @param g		initial state held at the MCTS root node
	 */
	public ManxFourBot(FourState g) {
		mind = new MonteCarloNode(g);
	} // end constructor

	public static void main(String[] args) {
        System.err.println("Now with more logic!(R)");
        new ManxFourBot().parseCommands();
    } // end main    
    
    public void parseCommands() {
        final FourState gameState = new FourState();
    	final Scanner scan = new Scanner(System.in);
    	
        while(scan.hasNextLine()) {
        	time.reset();
        	
            String line = scan.nextLine();

            if(line.length() == 0)
                continue;

            String[] parts = line.split(" ");
            
            if(parts[0].equals("settings")) {
                if (parts[1].equals("field_columns")) {
                    gameState.setColumns(Byte.parseByte(parts[2]));
                }
                if (parts[1].equals("field_rows")) {
                    gameState.setRows(Byte.parseByte(parts[2]));
                }
                if (parts[1].equals("your_botid")) {
                    botId = Byte.parseByte(parts[2]);
                }
            } else if(parts[0].equals("update")) { /* new field data */
                if (parts[2].equals("field")) {
                    String data = parts[3];
            		lockBetweenTurnCycle();
                    gameState.parseFromString(data); /* Parse Field with data */
                } else if (parts[2].equals("round")) {
                	ManxFourBot.round = Integer.parseInt(parts[3]);
                }
            } else if(parts[0].equals("action")) {
                if (parts[1].equals("move")) { /* move requested */
            		int column = this.makeTurn(gameState, Integer.parseInt(parts[2]));
                    System.out.println("place_disc " + column);
                }
            }
            else { 
                System.out.println("unknown command");
            }
        }
        
        scan.close();
    } // end method
    
	/**
     * Makes a turn. Edit this method to make your bot smarter.
     *
     * @return The column where the turn was made.
     */
    public byte makeTurn(FourState gameState, int timeLimit)
    {
    	FourState direFuture = gameState.clone(null);
    	byte decision = FourState.NO_PRECEDENT;
    	
    	int savedTimeLimit = timeLimit;
    	timeLimit = 465;
    	
    	short opponentSecondLast;
    	exceptionalCases:
        switch (round) {
        case 1:
        	decision = 3;
        	break;
        	
        case 2:
        	opponentLastMove = gameState.precedentMove;
        	switch (opponentLastMove) {
        	case 0:
        	case 2:
        	case 3:
        	case 4:
        	case 6:
            	decision = 3;
            	break exceptionalCases;
            	
        	case 1:
        		decision = 2;
            	break exceptionalCases;            	
        		
        	case 5:
        		decision = 4;
            	break exceptionalCases;
            	
        	default:
        		System.err.print("NPM-"); // No precedent move?? How?
        	}
        	
        case 3:
        	opponentLastMove = gameState.precedentMove;
        	
        	switch (opponentLastMove) {
        	case 0:
        	case 3:
        	case 6:
        		decision = 3;
            	break exceptionalCases;
        		
        	case 1:
        	case 2:
        		decision = 5;
            	break exceptionalCases;
        		
        	case 4:
        	case 5:
        		decision = 1;
            	break exceptionalCases;
        		
        	default:
        		System.err.print("NPM-"); // No precedent move?? How?
        	} // end if: depending on what my opponent did
            
        case 4:
        	opponentSecondLast = opponentLastMove;
        	opponentLastMove = gameState.precedentMove;
        	switch (opponentSecondLast) {
        	case 0: // xxx5xxx
        		if (3 != opponentLastMove)
        			break;        		
    			decision = 5;
        		break exceptionalCases;
        	case 1: // 2123322
        		switch (opponentLastMove) {
        		case 0: 
        		case 2: 
        		case 5: 
        		case 6: 
           			decision = 2;
            		break exceptionalCases;
        		case 3: 
        		case 4: 
           			decision = 3;
            		break exceptionalCases;
        		case 1: 
           			decision = 1;
            		break exceptionalCases;
            	}
        		break;
        	case 2: // xx23xxx
        		switch (opponentLastMove) {
        		case 2: 
        		case 3:
           			decision = opponentLastMove;
            		break exceptionalCases;
            	}
        		break;
        	case 3: // xxx3xxx
        		if (3 == opponentLastMove) {
           			decision = 3;
            		break exceptionalCases;
            	}
        		break;
        	case 4: // xxx34xx
        		switch (opponentLastMove) {
        		case 4: 
        		case 3:
           			decision = opponentLastMove;
            		break exceptionalCases;
            	}
        		break;
        	case 5: // 4433454
        		switch (opponentLastMove) {
        		case 0: 
        		case 1: 
        		case 4: 
        		case 6: 
           			decision = 4;
            		break exceptionalCases;
        		case 3: 
        		case 2: 
           			decision = 3;
            		break exceptionalCases;
        		case 5: 
           			decision = 5;
            		break exceptionalCases;
            	}
        		break;
        	case 6: // xxx1xxx
        		if (3 != opponentLastMove)
        			break;        		
    			decision = 1;
        		break exceptionalCases;
        	} // end if
        	
        	opponentLastMove = FourState.NO_PRECEDENT;
                    
        case 5:
        	opponentSecondLast = opponentLastMove;
        	opponentLastMove = gameState.precedentMove;
        	switch (opponentSecondLast) {
        	case 0: // xxx5xxx
        		if (3 != opponentLastMove)
        			break;        		
    			decision = 5;
        		break exceptionalCases;
        	case 1: // xxxx5x3
        		switch (opponentLastMove) {
        		case 4: 
           			decision = 5;
            		break exceptionalCases;
        		case 6: 
           			decision = 3;
            		break exceptionalCases;
            	}
        		break;
        	case 2: // xx63x65
        		switch (opponentLastMove) {
        		case 2: 
           			decision = 6;
            		break exceptionalCases;
        		case 3: 
           			decision = 3;
            		break exceptionalCases;
        		case 5: 
           			decision = 6;
            		break exceptionalCases;
        		case 6: 
           			decision = 5;
            		break exceptionalCases;
            	}
        		break;
        	case 3: // 4333332
        		switch (opponentLastMove) {
        		case 0: 
           			decision = 4;
            		break exceptionalCases;
        		case 6: 
           			decision = 2;
            		break exceptionalCases;
        		default: 
           			decision = 3;
            		break exceptionalCases;
            	}
        	case 4: // 10x30xx
        		switch (opponentLastMove) {
        		case 0: 
           			decision = 1;
            		break exceptionalCases;
        		case 1: 
           			decision = 0;
            		break exceptionalCases;
        		case 3: 
           			decision = 3;
            		break exceptionalCases;
        		case 4: 
           			decision = 0;
            		break exceptionalCases;
            	}
        		break;
        	case 5: // 3x1xxxx
        		switch (opponentLastMove) {
        		case 0: 
           			decision = 3;
            		break exceptionalCases;
        		case 2: 
           			decision = 1;
            		break exceptionalCases;
            	}
        		break;
        	case 6: // xxx1xxx
        		if (3 != opponentLastMove)
        			break;        		
    			decision = 1;
        		break exceptionalCases;
        	} // end if
        	
        	opponentLastMove = FourState.NO_PRECEDENT;
        
        case 7:
        	if (FourState.NO_PRECEDENT != opponentLastMove && gameState.getDisc((byte)3, (byte)1) == botId) {
        		opponentLastMove = gameState.precedentMove;
        		switch (opponentLastMove) {
        		case 0:
        		case 3: // 3 -> could be 2 or 4
        			decision = 4;
            		break exceptionalCases;
        		case 1:
        		case 2:
        		case 4:
        		case 5:
        			decision = opponentLastMove;
            		break exceptionalCases;
        		case 6:
        			decision = 2;
            		break exceptionalCases;
        		}
        	}
        	
        	opponentLastMove = FourState.NO_PRECEDENT;
        	
        default:
        	int movesLeft = (44 - round) / 2;
        	int totalTime = savedTimeLimit + (movesLeft - 1) * 500;
        	
        	double thisMoveTime = totalTime / Math.max(1, movesLeft - 3);
        	thisMoveTime *= Math.pow(EARLY_MOVE_BONUS, Math.max(0, movesLeft - 4));
        	timeLimit = Math.min((int)thisMoveTime, savedTimeLimit) - 35;
 /*       	
        	byte me = gameState.getActivePlayer();
        	byte you = (byte)(3 - me);
        	for (byte col : gameState.getMoves())
        		if (direFuture.clone(gameState).simulateMove(col, me).gameIsOver()) {
        			timeLimit = 0;
        			decision = col;
        			break exceptionalCases;
        		} // end if
        	for (byte col : gameState.getMoves())
        		if (direFuture.clone(gameState).simulateMove(col, you).gameIsOver()) {
        			decision = col;
        			break exceptionalCases;
        		} // end if
*/        } // end ifs
    	
		System.err.print(String.format("r: %s t: %s ", round, timeLimit));
	
    	if (FourState.NO_PRECEDENT != decision) {
    		System.err.print(String.format("fO(0x%s)-", Long.toHexString(gameState.id())));
			mind.forceOption(direFuture.clone(gameState).simulateMove(decision, botId));
    	} // end if

    	for (int i = 0; i != THINK_RATIO.length; ++i) {
			int thinkPeriod = (int)Math.max(0, timeLimit * THINK_RATIO[i]);
			
			do { mind.buildTree(); } while (time.elapsed() < thinkPeriod);
			
			if (mind.easyMove(EASY_MARGIN[i])) {
				System.err.print(EASY_DESCRIPTION[i]);
				break;
			} // end if: easy move
		}
    		
    	if (FourState.NO_PRECEDENT == decision)
    		decision = mind.chooseOption();
    	
		unlockBetweenTurnCycle();
    	
        return decision;
    } // end method
    
    public void lockBetweenTurnCycle()
    {
		if (!betweenTurnCycle.isHeldByCurrentThread())
			betweenTurnCycle.lock();
    } // end method
    
    public void unlockBetweenTurnCycle()
    {
		if (betweenTurnCycle.isHeldByCurrentThread())
			betweenTurnCycle.unlock();
		
		synchronized (mainThread) {
			mainThread.notify();
		} // end sync
    } // end method
    
    @Override
	public void run() {
		do {
			synchronized (mainThread) {
			try {
				if (betweenTurnCycle.isHeldByCurrentThread())
					betweenTurnCycle.unlock();
				mainThread.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}} // end sync
			
			betweenTurnCycle.lock();
			do {
				mind.buildTree();
			} while (!betweenTurnCycle.hasQueuedThreads());
			
		} while (mainThread.getState() != Thread.State.TERMINATED);
	} // end method

	@Override
	public void fourStateParsed(FourState newState) {
		if (this.getState() == Thread.State.NEW) {
			mind = new MonteCarloNode(newState);
			this.start();
			return;
		} else {
			lockBetweenTurnCycle();
			mind = mind.traverseInto(newState);			
		}
	} // end FourState.AfterParseListener method
 } // end class