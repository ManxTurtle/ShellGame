# ShellGame
This Github repository begins with Version 67, already in production @ theaigames.com .

Project is written in Java.  There are four essential modules: ManxFourBot, FourState, MonteCarloNode, and Timer.
TestManxCarlo uses Junit to, amongst other things, run a full game of the bot versus itself.

ManxFourBot contains main().  It spawns a thread that is responsible for cultivating the Monte Carlo Tree Search nodes during the opponent's turns.  Most of the magic of the bot happens in makeTurn().  Most of the modeling of the Connect Four board is done in FourState -- if not all of this modeling!
