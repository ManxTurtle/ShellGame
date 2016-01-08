import java.util.Arrays;
import java.util.HashSet;

// https://tromp.github.io/c4/c4.html

// Copyright 2015 theaigames.com (developers@theaigames.com)

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
 * Field class
 * 
 * Field class that contains the field status data and various helper functions.
 * 
 * @author Jim van Eeden <jim@starapple.nl>, Joost de Meij <joost@starapple.nl>
 */

public class FourState implements
    MonteCarloNode.State<FourState>
{	
//    private static final byte TOO_MANY_PRECEDENTS = -2;
    public static final byte NO_PRECEDENT = -1;
    public static final byte TIE_GAME = 0;
    public static final byte P1_WON = 1;
    public static final byte P2_WON = 2;
	public static final byte P1_ENDGAME = 3;
    public static final byte P2_ENDGAME = 4;
	public static final byte P1_MIDGAME = 5;
    public static final byte P2_MIDGAME = 6;
	public static final byte NO_PLAYER = 0;
	public static final byte PLAYER1_NEXT = 1;
    public static final byte PLAYER2_NEXT = 2;
    private static final byte[] ENCODE_GAME_IS_OVER = {TIE_GAME, P1_WON, P2_WON};
    private static final byte[] ENCODE_GAME_IS_LIVE = {P2_ENDGAME, P1_ENDGAME, P2_ENDGAME};
    private static final byte[] ENCODE_NEXT_PLAYER = {P2_MIDGAME, P1_MIDGAME, P2_MIDGAME};
    private static final byte[] DECODE_ACTIVE_PLAYER = {NO_PLAYER, NO_PLAYER, NO_PLAYER, PLAYER2_NEXT, PLAYER1_NEXT, PLAYER2_NEXT, PLAYER1_NEXT};
    private static final byte[] DECODE_WINNER = {NO_PLAYER, P1_WON, P2_WON, NO_PLAYER, NO_PLAYER};
    private static final boolean[] DECODE_WINNER_UNDETERMINED = {false, false, false, false, false, true, true};
    private static final boolean[] DECODE_GAME_IS_OVER = {true, true, true, false, false};
    private static final double VALUE_OF_WIN = 1.0;
    private static final double VALUE_OF_TIE = 0.5;
    private static final double VALUE_OF_LOSS = 0.0;

    public static final HashSet<AfterParseListener> afterParseListeners = new HashSet<>();
    
    // These were made static to reduce the memory footprint of making many copies of a single Field object.
    // All Fields will have the same number of rows and columns.
    // (Note: technically, it's inappropriate to name these variables with an m prefix.  We don't care.)
    private static byte COL_COUNT = 0;
	private static byte ROW_COUNT = 0;
    
    private static final byte PLAYER_COUNT = 2;
    private static byte COLUMN_SHIFT;
    private static byte CHECK_WIN_PIPE;
    private static byte CHECK_WIN_HYPHEN;
    private static byte CHECK_WIN_BKSLASH;
    private static byte CHECK_WIN_FWSLASH;
    private static int CHECK_WIN_PIPE2;
    private static int CHECK_WIN_HYPHEN2;
    private static int CHECK_WIN_BKSLASH2;
    private static int CHECK_WIN_FWSLASH2;
    
    private long[] player;
	private byte[] columnHeight;
    public byte precedentMove;
    private byte winnerOrActivePlayer;
    
    public FourState()
    {
        this.precedentMove = NO_PRECEDENT; // NONE;
        this.winnerOrActivePlayer = ENCODE_GAME_IS_LIVE[NO_PLAYER];
        
        player = new long[PLAYER_COUNT]; Arrays.fill(player, 0L);
        columnHeight = new byte[COL_COUNT];
    } // end constructor
    
    public FourState clone(MonteCarloNode.State<?> donor)
    {
    	FourState dst;
    	FourState src;
    	
    	if (donor instanceof FourState) {
    		dst = this;
    		src = (FourState)donor;
    	} else {
    		dst = new FourState();
    		src = this;
    	} // end if: src, dst definition
    	
    	dst.precedentMove = src.precedentMove;
    	dst.winnerOrActivePlayer = src.winnerOrActivePlayer;
        System.arraycopy(src.columnHeight, 0, dst.columnHeight, 0, COL_COUNT);
        System.arraycopy(src.player, 0, dst.player, 0, PLAYER_COUNT);
        
		return dst;
    } // end MonteCarloState method
    
    public boolean gameIsOver()
    {
    	return (DECODE_WINNER_UNDETERMINED[this.winnerOrActivePlayer]
			? determineWinner()
			: DECODE_GAME_IS_OVER[winnerOrActivePlayer]);
    } // end MonteCarloState method

	@Override
	public byte getActivePlayer() {
		return DECODE_ACTIVE_PLAYER[this.winnerOrActivePlayer];
	} // end MonteCarloState method

	@Override
    public byte[] getMoves()
    {
		// TODO:67 When bitboards were introduced, Validity was calculated every getMoves().
		// As we seem to have experienced quite a drop in cycles, this may be a change worth reversing.
		return Column.Validity.movesFor(this);
    } // end MonteCarloState method

	@Override
	public double getValue(byte playerId) {
		gameIsOver();
		final byte winner = DECODE_WINNER[this.winnerOrActivePlayer];
		if (winner == NO_PLAYER)	return VALUE_OF_TIE;
		if (winner == playerId)		return VALUE_OF_WIN;
									return VALUE_OF_LOSS;
	} // end CarloGameState method
    
	@Override
    public FourState simulateMove(byte col)
    {
        return simulateMove(col, this.getActivePlayer());
    } // end MonteCarloState method

	public FourState simulateMove(byte col, byte playerId) {
        return addDisc(col, playerId);
	} // end method
    
    private boolean determineWinner()
    {
    	final boolean GAME_IS_OVER = true;
    	final boolean GAME_IS_LIVE = false;
    	
    	// TODO:67 This row parameter could easily be replaced by Column.lastRowOccupied.
    	// @see #addDisc() for an explanation as to why Column was removed.
        byte playerId = getDisc(precedentMove, (byte)(columnHeight[precedentMove] - 1));
    	long bitboard = player[(byte)(playerId - 1)];
    	winnerOrActivePlayer = ENCODE_GAME_IS_OVER[playerId];
    	long overlap;
    	
		overlap = bitboard & (bitboard >> CHECK_WIN_HYPHEN);
		if (0 != (overlap & (overlap >> CHECK_WIN_HYPHEN2)))
		return GAME_IS_OVER;
        
		overlap = bitboard & (bitboard >> CHECK_WIN_PIPE);
		if (0 != (overlap & (overlap >> CHECK_WIN_PIPE2)))
		return GAME_IS_OVER;
		
		overlap = bitboard & (bitboard >> CHECK_WIN_BKSLASH);
		if (0 != (overlap & (overlap >> CHECK_WIN_BKSLASH2)))
			return GAME_IS_OVER;
			
		overlap = bitboard & (bitboard >> CHECK_WIN_FWSLASH);
		if (0 != (overlap & (overlap >> CHECK_WIN_FWSLASH2)))
			return GAME_IS_OVER;
		
        this.winnerOrActivePlayer = ENCODE_GAME_IS_LIVE[playerId];
		return GAME_IS_LIVE;
    } // end method
    
    /**
     * Sets the number of columns (this clears the board)
     * @param args : int cols
     */
    public void setColumns(byte colCount) {
		if (colCount < 1) return;
		FourState.COL_COUNT = colCount;
		if (0 == ROW_COUNT) return;
	    
	    // To adjust position by a column, step through rowCount powers of two.
	    // The plus-one represents a row of 'air' that forces diagonal win-checking to behave.
	    COLUMN_SHIFT = ROW_COUNT; ++COLUMN_SHIFT;
	    // The check-win variables are powers of two by which to shift the player state
	    // and determine if they have won.  The shifting is combined with the AND operation
	    // to conceptually overlap the bits 1 position away from each other on one axis
	    // (two CR diagonals:BKSLASH/FWSLASH, one C vertical:PIPE, one R horizontal:HYPHEN).
	    // This operation is done in two parts, first to overlap adjacents,
	    // and then to overlap the first overlaps that are now 2 positions away.
	    CHECK_WIN_PIPE2 = (CHECK_WIN_PIPE = 1) << 1;
	    CHECK_WIN_HYPHEN2 = (CHECK_WIN_HYPHEN = COLUMN_SHIFT) << 1;
	    CHECK_WIN_BKSLASH = COLUMN_SHIFT; --CHECK_WIN_BKSLASH;
	    CHECK_WIN_BKSLASH2 = CHECK_WIN_BKSLASH << 1;
	    CHECK_WIN_FWSLASH = COLUMN_SHIFT; ++CHECK_WIN_FWSLASH;
	    CHECK_WIN_FWSLASH2 = CHECK_WIN_FWSLASH << 1;
	    
        columnHeight = new byte[COL_COUNT]; Arrays.fill(columnHeight, (byte)0);
	    Column.Validity.reset();
	    
	} // end method

    /**
     * Sets the number of rows (this clears the board)
     * @param args : int rows
     */
    public void setRows(byte rowCount) {
    	if (rowCount < 1) return;
    	FourState.ROW_COUNT = rowCount;
    	if (0 == COL_COUNT) return;
        setColumns(COL_COUNT);
    } // end method

    public FourState addDisc(byte column, short playerId) {
    	this.precedentMove = column;
    	
    	// TODO:67 The Column class was lost when bitboards came in.
    	// It was a nice alternative to some mathematics, so I should probably bring it back.
    	player[playerId - 1] |= 1L << (column * COLUMN_SHIFT + columnHeight[column]++);
        
        winnerOrActivePlayer = (0 == getMoves().length)
    		? TIE_GAME
			: ENCODE_NEXT_PLAYER[playerId];
    	
        return this;
    } // end method

	public String parseableString()
    {
    	final StringBuilder output = new StringBuilder();
    	
    	// Build one row of columns.
    	int col = COL_COUNT;
    	while(0 != col--) output.append("0,");
    	output.deleteCharAt(output.length() - 1);
    	
    	// Build the remaining rows.
    	final String EMPTY_ROW_TEXT = ";" + output.toString();
    	for (int row = ROW_COUNT - 1; 0 != row--;) output.append(EMPTY_ROW_TEXT);    	
    	
    	// Insert the column data.
    	long player1 = player[0];
    	long player2 = player[1];
    	for (int c = 0; COL_COUNT != c; ++c) {
    		for (int r = ROW_COUNT; 0 != r--; player1 >>= 1, player2 >>= 1) {
    			int charIndex = (c << 1) + r * (COL_COUNT << 1);
    			if (0 != (player1 & 1)) output.setCharAt(charIndex, '1');
        		else if (0 != (player2 & 1)) output.setCharAt(charIndex, '2'); 
    		} // end loop: descending rows
    		
    		player1 >>= 1;
    		player2 >>= 1;
    	} // end loop: ascending columns
    	
    	return output.toString();
    } // end method

   public void parseFromString(String s) {
    	String[] rows = s.replaceAll(",", "").split(";");
    	int discsAdded = 0;
    	atNextColumn:
    	for (byte c = 0; COL_COUNT != c; ++c) {
    		for (byte rRows = ROW_COUNT, r = 0; 0 != rRows--; ++r) {
    			char playerToken = rows[rRows].charAt(c);
    			if ('0' == playerToken)
    				continue atNextColumn;
    			
    			// TODO:67 Prior to this version, parseFromString() was more resilient
    			// in the case that an un-precedented move was supplied.
    			// To just get the work done, easily, I settled for an assumption
    			// that either precedent exists, or the board's being built from Turn 0.
    			if (0 == getDisc(c, r)) {
    				this.addDisc(c, (byte)(playerToken - '0'));
    				++discsAdded;
    			} // end if
    		} // end loop: rows
    	} // end loop: columns
    	
    	if (0 == discsAdded)
    		System.err.print("R-");
    	else if (1 != discsAdded) {
    		this.precedentMove = NO_PRECEDENT;
    		this.winnerOrActivePlayer = ENCODE_GAME_IS_LIVE[(0 == discsAdded % 2 ? 2 : 1)];
    	} // end if
    	
    	System.err.println(String.format("mv: %s", this.precedentMove));
    	
    	System.err.print(String.format("p: %s ", DECODE_ACTIVE_PLAYER[this.winnerOrActivePlayer]));		
		
    	// TODO:67 Technically, this event isn't necessary when 0 == discsAdded.
		for (AfterParseListener apl : afterParseListeners)
			apl.fourStateParsed(this);
    } // end method
    
    public byte getDisc(byte column, byte row) {
    	long bitPosition = 1L << (COLUMN_SHIFT * column + row);
    	if (0 != (player[0] & bitPosition)) return 1;
    	if (0 != (player[1] & bitPosition)) return 2;
    	return 0;
    } // end method

    public boolean columnIsOpen(byte column) {
    	// TODO:67 This method and the next must be modified when Column is reintroduced.
        return (ROW_COUNT != columnHeight[column]);
    } // end method

    public boolean columnIsFull(byte column) {
        return (ROW_COUNT == columnHeight[column]);
    } // end method
	
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof FourState) {
			FourState other = (FourState)o;
			return (other.player[0] == this.player[0] && other.player[1] == this.player[1]);
		} else if (o instanceof Byte)
			return ((Byte)o).byteValue() == this.precedentMove;
		
		return false;
	} // end Object method
	
	public long id()
	{
		return this.player[0] + new java.util.Random(player[1]).nextLong();//this.player[0] ^ (player[1] << 1);
	} // end method
	
	@Override
	public String toString()
	{
		return parseableString().replaceAll(";", System.getProperty("line.separator"));
	} // end Object method
	
	public static interface AfterParseListener
	{
		void fourStateParsed(FourState newState);
	} // end interface
		
	protected static class Column
	{
		public static class Validity
		{
			private static byte SHIFT_TOP;
		    private static long ISOLATE_TOP;
			private static long CONDENSE_HASHCODE;
			private static long ISOLATE_HASHCODE;
			private static int SHIFT_HASHCODE;
			
			static Validity[] byHashCode;
			byte[] validColumns;
			
			private Validity(int hashCode)
			{
				// M.I.T. HAKMEM bitcount in O(1) algorithm
				long uCount = hashCode - ((hashCode >> 1) & 033333333333) - ((hashCode >> 2) & 011111111111);
				this.validColumns = new byte[(int)(((uCount + (uCount >> 3)) & 030707070707) % 63)];
				
				int colIndex = validColumns.length;
				int bitMask = 1 << COL_COUNT;
				for (byte col = COL_COUNT; 0 != col--;)
					if (0 != (hashCode & (bitMask >>= 1)))
						validColumns[--colIndex] = col;
			} // end constructor
			
			public static void reset()
			{
				int permutationCount = 1 << COL_COUNT;
				byHashCode = new Validity[permutationCount];
				for (int hashCode = permutationCount; 0 != hashCode--;)
					Validity.byHashCode[hashCode] = new Validity(permutationCount - hashCode - 1);

			    // A row mask is the number that you AND with a player state to isolate the bottom row.
			    // It's obtained by mathematically putting a bit in position cr(1,0),
			    // and then multiplying that number by a quantity of adjacent ones
			    // equal to the number of columns so as to copy cr(1,0) into cr(2,0), cr(3,0) etc..
			    // After the AND operation, a number can be divided into the result
			    // to map the bottom row into a number between 0 and 127 (assuming a board of 7 columns).
			    SHIFT_TOP = ROW_COUNT; --SHIFT_TOP;
			    ISOLATE_TOP = 1L;
			    CONDENSE_HASHCODE = 0L;
			    for (int col = COL_COUNT; 0 != col--;) {
			    	ISOLATE_TOP = (ISOLATE_TOP << COLUMN_SHIFT) + 1;
			    	CONDENSE_HASHCODE = (CONDENSE_HASHCODE << ROW_COUNT) + 1;
			    } // end loop
			    SHIFT_HASHCODE = COL_COUNT * ROW_COUNT - ROW_COUNT;
			    ISOLATE_HASHCODE = ((1L << COL_COUNT) - 1) << (SHIFT_HASHCODE);
			} // end method
			
			public static byte[] movesFor(FourState fourState) {
				long hashCode = fourState.player[0] | fourState.player[1];
				hashCode >>= SHIFT_TOP;
				hashCode &= ISOLATE_TOP;
				hashCode *= CONDENSE_HASHCODE;
				hashCode &= ISOLATE_HASHCODE;
				hashCode >>= SHIFT_HASHCODE;
				return byHashCode[(int)hashCode].validColumns;
			} // end method
		} // end inner inner class
	} // end inner class
} // end class