import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestManxCarlo
{
	PrintStream e = System.err;
    ManxFourBot b = new ManxFourBot();
    FourState f = new FourState();

    /**
     * Sets up the test fixture.
     *
     * Called before every test case method.
     */
    @Before
    public void setUp()
    {
    	ManxFourBot.round = 0;
    	ManxFourBot.botId = 1;
        f.setColumns((byte)7);
        f.setRows((byte)6);
    } // end initializer
    
    @After
    public void tearDown()
    {
    	System.setErr(e);
		FourState.afterParseListeners.clear();
    } // end cleanup
    
//    @Test
    public void bottomLeftBackslashDetection()
    {
    	f.parseFromString("0,0,1,2,2,2,0;0,0,2,1,1,2,0;1,0,2,2,2,1,1;2,0,2,1,1,2,1;2,2,1,2,1,1,2;1,1,2,1,1,2,1");
    	f.simulateMove((byte)1);
    	assert(f.gameIsOver());
    } // end test
    
//    @Test
    public void firstMoveIsThree()
    {
    	ManxFourBot.round = 1;
        f.parseFromString("0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0");
    	ManxFourBot.time.reset();
        assertEquals(3, b.makeTurn(f, 10000));
    } // end test
    
//    @Test
    public void chooseFiveToBecomeUnblockable()
    {
        f.parseFromString("0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;2,2,0,1,1,0,0");
        assertEquals(5, b.makeTurn(f, 10000));
    } // end test
    
    @Test
    public void simulateFillingColumns()
    {
        f.parseFromString("0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0");
        for (byte col : f.getMoves()) {
        	while ((f = (FourState)f.simulateMove(col)).columnIsOpen(col));
        	System.out.println(f);
            System.out.println(Arrays.toString(f.getMoves()));       
        }
    } // end method

//    @Test
    public void writePlayBook()
    {
			System.setErr(new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
					
				}}));
    	
    	java.util.ArrayList<Object> stateMoves = new java.util.ArrayList<>();
    	int index = 0;
    	ManxFourBot.round = 2;
		FourState.afterParseListeners.clear();
    	f.parseFromString("0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0");
    	do {
			int nextRound = ManxFourBot.round + 2;
    		for (byte move : f.getMoves()) {
    			FourState g = f.clone(null).simulateMove(move);
    			b = new ManxFourBot(g);
            	ManxFourBot.time.reset();
    			byte counterMove = b.makeTurn(g, 50000);
    			stateMoves.add(new Object() {
					@Override
					public String toString() {
						String result = "\n0x" + Long.toHexString(g.id()) + "," + counterMove;
						f.clone(g).simulateMove(counterMove);
		    			if (f.gameIsOver())
		    				fail("The opponent won.\n" + g);
						ManxFourBot.round = nextRound;
						return result;
					} // end method
				});
    		} // end loop
    		
    		do
    			stateMoves.get(index++).toString();
    		while (f.gameIsOver());
    	} while (stateMoves.size() < (Math.pow(7, 3) - 1));
    	System.out.println(stateMoves);
    } // end test
    
//    @Test
    public void playAgainstSelf()
    {
    	long t1 = 10000;
    	long t2 = 10000;
    	long t3;
    	
    	String serverBoard = "0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,0,0,0,0";
    	FourState nextRound;
        do {
        	++ManxFourBot.round;
        	b.lockBetweenTurnCycle();
        	f.parseFromString(serverBoard);
        	ManxFourBot.time.reset();
        	byte choice = b.makeTurn(f, (int)t1);
        	t3 = Math.max(500, Math.min(10000, t1 - ManxFourBot.time.elapsed() + 500));
        	System.err.print(String.format("e: %s ", ManxFourBot.time.elapsed()));
        	t1 = t2;
        	t2 = t3;
        	nextRound = f.clone(null).simulateMove(choice);
        	serverBoard = nextRound.parseableString();
        	b.unlockBetweenTurnCycle();
        } while (nextRound.gameIsLive());
        
        System.err.println(String.format("mv: %s" , nextRound.precedentMove, ManxFourBot.time.elapsed()));
        System.err.println(nextRound);
    } // end test
    
//    @Test
    public void timeoutRecovery()
    {
    	f.parseFromString("0,0,0,0,0,0,0;0,0,2,2,2,0,0;0,0,1,1,1,0,0;0,0,2,2,2,0,0;0,0,1,1,1,0,0;0,0,2,1,2,0,1");
    	System.out.println();
    	System.out.println(f);
    	f.parseFromString("0,0,0,0,0,0,0;0,0,2,2,2,0,0;0,0,1,1,1,0,0;0,0,2,2,2,0,0;0,0,1,1,1,0,0;0,0,2,1,2,0,1");
    	System.out.println();
    	System.out.println();
    	System.out.println(f);
    }
} // end class
