import java.util.LinkedList;
import java.util.Random;

public class MonteCarloNode {
	public static final double EXPLORATION = Math.sqrt(2);
	public static final double EPSILON = 1e-6;
	private static final double NO_PREVIOUS_WINNER = -Double.MAX_VALUE;
	
	private static MonteCarloNode rootNode;
	private static State<?> rootState;
	private static ThreadLocal<MonteCarloNode> selectNode = new ThreadLocal<>();
	private static ThreadSafeState selectState = new ThreadSafeState();
	private static ThreadSafeState simulateState = new ThreadSafeState();
	private static ThreadSafeState propagateState = new ThreadSafeState();
	private static ThreadSafePath visited = new ThreadSafePath();
	
	private MonteCarloNode[] children;
	private int nVisits;
	private double totValue;
	public final byte move;
    
    public MonteCarloNode(State<?> gameState) {
    	this.move = -1;
    	rootNode = this;
    	rootState = (State<?>)gameState.clone(null);
    	buildTree();
	} // end constructor

    protected MonteCarloNode(byte move) {
		this.move = move;
	} // end constructor
    
	public boolean easyMove(double margin)
    {
        double bestValue = 0;
        double lestValue = 0;
        double candidate;
        
        for (MonteCarloNode child : children)
            if ((candidate = child.totValue / child.nVisits) > bestValue) {
            	lestValue = bestValue;
            	bestValue = candidate;
            } // end if: better choice
        
        return (bestValue >= lestValue * margin);
    } // end method
	
	public void forceOption(State<?> gameState)
    {
		// TODO:67 It wasn't until TestManxCarlo.writePlaybook()
		// that I realized forceOption() didn't select the node.
		selectNode.set(this);
		selectState.get().clone(rootState);
    	if (null == children)
    		expand();
		traverseInto(gameState);
    } // end method
    
    public byte chooseOption()
    {
        byte choice = -1;
        
    	if (this != rootNode)
    		return rootNode.move;
    	
        int bestValue = Integer.MIN_VALUE;
        int candidate;
        for (MonteCarloNode child : children)
            if ((candidate = child.nVisits) > bestValue) {
            	bestValue = candidate;
                choice = child.move;
            } // end if: better choice
        
	    System.err.print(String.format("v: %s ", bestValue));
        
        return choice;
    } // end method
    
    public MonteCarloNode traverseInto(State<?> state)
    {
    	if (rootState.equals(state))
    		return rootNode;
    	
    	for (MonteCarloNode c : children)
    		if (state.equals(c.move)) {
    			rootState.simulateMove(c.move);
    			return (rootNode = c);
    		} // end if
    	
    	System.err.print("tI()-");
    	return (rootNode = new MonteCarloNode(state)); // should never happen if the caller knew what they're doing
    } // end method

	public void buildTree()
	{
		propagateState.get().clone(rootState);
		selectState.get().clone(rootState);
        selectNode.set(rootNode);
        visited.get().clear();
        
        MonteCarloNode cn;
        while ((cn = selectNode.get()).isBranch())
        	cn.select();
        
        if (cn.expand())
        	cn.select().simulate();
        
        backPropagate();
    } // end method
    
    protected MonteCarloNode select() {
        MonteCarloNode selected = null;
        double bestValue = NO_PREVIOUS_WINNER;
        
        for (MonteCarloNode c : children) {
            double uctValue = c.totValue / (c.nVisits + EPSILON)
            	+ EXPLORATION * Math.sqrt(
            			Math.log(nVisits + 1) / (c.nVisits + EPSILON))
            	+ State.rng.nextDouble() * EPSILON;

            if (uctValue > bestValue) {
                selected = c;
                bestValue = uctValue;
            }
        } // end loop
        
        traverse(selected);
        return selected;
    } // end method
    
    protected boolean expand() {
    	State<?> cs = selectState.get();
    	
    	if (cs.gameIsOver())
    		return false;
    	
        byte[] moves = cs.getMoves();
        
        int i = moves.length;
        children = new MonteCarloNode[i];
        while (0 != i--)
            children[i] = new MonteCarloNode(moves[i]);
        
        return true;
    } // end method

    protected void simulate() {
    	State<?> cs = selectState.get();
    	State<?> ss = simulateState.get();
    	ss.clone(cs);
    	while(cs.gameIsLive()) {
        	byte perspective = cs.getActivePlayer();
        	double value = cs.getValue(perspective);
        	
        	// Set up circular traversal of the given move options.
        	// ASSUMPTION: cs.gameIsLive() <-> 0 != cs.getMoves().length
    		byte[] moves = cs.getMoves();
    		int i = State.rng.nextInt(moves.length); // startIndex
    		int j = i; // currentIndex
    		do
    			// TODO:67 This version introduced One Turn Lookahead.
    			// Zero-sum game theory: If an upcoming turn is good for an opponent,
    			// then it's bad for me, which means that they'll obviously select it.
    			// The following analysis is equivalent to a one-ply Minimax Tree.
    			if (((State<?>) ((State<?>) ss.clone(cs)).simulateMove(moves[i]))
    				.getValue(perspective) > value
				) break;
    		while (j != (i = (i + 1) % moves.length));

    		cs.clone(ss);
    	} // end loop: simulation    	
    } // end method

    protected void backPropagate()
	{
    	State<?> cs = selectState.get();
    	State<?> ss = simulateState.get();
    	ss.clone(propagateState.get());
        for (MonteCarloNode node : visited.get()) {
        	++node.nVisits;
        	node.totValue += cs.getValue(ss.getActivePlayer());
        	ss = (State<?>) ss.simulateMove(node.move);
        } // end loop
        
        ++rootNode.nVisits;
	} // end method

    private void traverse(MonteCarloNode next)
    {
    	selectNode.set(next);
        selectState.get().simulateMove(next.move);
    	visited.get().add(next);
    } // end method

    public boolean isBranch() {
        return (null != children);
    } // end method

    public int arity() {
        return children == null ? 0 : children.length;
    } // end method
    
    public String toString()
    {
    	String result = "{(" + this.totValue + "," + this.nVisits + ")\n";
    	if (null != children)
	    	for (MonteCarloNode c : children)
	    		result += "(" + c.totValue + "," + c.nVisits + "),";
    	
    	return result + "}";
    } // end Object method
	
    // TODO:67 This version began the parameterization of State,
    // permitting the return value of clone() and simulateMove()
    // to be FourStates.  Given how this forced <?> throughout
    // MonteCarloNode, I'm not sure the casting convenience
    // was worth it..
	public static interface State<GameState>
	{
	    Random rng = new Random();
	    
	    GameState	clone(State<?> rootState);
	    default
	    boolean		gameIsLive() { return !gameIsOver(); }
	    boolean		gameIsOver();
	    byte		getActivePlayer();
	    byte[]		getMoves();
	    double		getValue(byte playerId);
	    GameState	simulateMove(byte moveIndex);
	} // end inner interface
	
	// TODO:67 The only reason to subclass these things at all
	// is to provide the initialValue(), really.
	// Not all subclasses are used in multiple places.
	// I prefer them to be located here, instead of in declaration lines.
	protected static class ThreadSafeState extends ThreadLocal<State<?>>
	{
		@Override
		protected State<?> initialValue() {
			return (State<?>)rootState.clone(null);
		} // end method
	} // end inner class
	
	protected static class ThreadSafePath extends ThreadLocal<LinkedList<MonteCarloNode>>
	{
		@Override
		protected LinkedList<MonteCarloNode> initialValue() {
			return new LinkedList<>();
		} // end method
	} // end inner class
} // end class