package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.Collections;
import java.util.LinkedList;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Array of players threads.
     */
    private Thread[] playerThreads;

    /**
     * The actions from the players the dealer should make.
     */
    ConcurrentLinkedQueue<Integer> pendingRequestsFromPlayers; //package private so we can use in the tests

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.playerThreads = new Thread[env.config.players];
        this.pendingRequestsFromPlayers = new ConcurrentLinkedQueue<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");

        // start the players threads
        for(int i=0; i<players.length; i++){
            String playerName = "player " + i;
            playerThreads[i] = new Thread(players[i], playerName);
            playerThreads[i].start();
        }


        while (!shouldFinish()) {
            shuffle();
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() <= reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            checkRequests();
            freezePlayers();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for(int i=0; i<players.length; i++){
            players[i].terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || ((env.util.findSets(deck, 1).size() == 0) && (!isThereSetOnTable()));
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[]setToRemove) {
        for(int i=0; i<setToRemove.length; i++){
            int slot = table.getSlotFromCard(setToRemove[i]);
            table.slotAvailable[slot] = false;
            removeCard(slot);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        boolean wasPlaced = false;
        for (int i=0; i<env.config.tableSize; i++){
            if(table.getCardFromSlot(i) == null && !deck.isEmpty()){
                placeCard(i);

                table.slotAvailable[i] = true;

                wasPlaced = true;
            }
        }
        table.tableAvailable = true;
        if(wasPlaced){
            updateTimerDisplay(true);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (this) {wait(700);}
        } catch (InterruptedException ignored){}
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else{
            env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.tableAvailable = false;
        for (int i=0; i<env.config.tableSize; i++)
        {
            if(table.getCardFromSlot(i)!=null){
                // return cards to the deck
                deck.add(table.getCardFromSlot(i));
            }

            removeCard(i);

        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = -1;
        int counter = 0;
        for(int i=0; i<players.length; i++){
            if(maxScore < players[i].score()){
                maxScore = players[i].score();
                counter = 1;
            }
            else if(maxScore == players[i].score()){
                counter++;
            }
        }
        int[]winnerID = new int[counter];
        int currIndex = 0;
        for(int i=0; i<players.length; i++){
            if(players[i].score() == maxScore){
                winnerID[currIndex] = i;
                currIndex++;
            }
        }
        env.ui.announceWinner(winnerID);

    }

    /**
     * Shuffles the deck.
     */
    private void shuffle() {
        Collections.shuffle(deck);
    }

    /**
     * adds the request to the queue of requests.
     * @post - the int representing the player that sent the request is inserted to pendingRequestsFromPlayers.
     */
    public void addRequest(Integer playerId){
        pendingRequestsFromPlayers.add(playerId);

        synchronized (this) { notifyAll(); }
    }

    /**
     * takes the requests from the queue and calls the appropriate functions.
     * @post - the pendingRequestsFromPlayers should be empty.
     */
    public void checkRequests(){
        while (!pendingRequestsFromPlayers.isEmpty()){
            Integer playerToCheck;

            playerToCheck = pendingRequestsFromPlayers.poll();

            UtilImpl util = new UtilImpl(env.config);
            if(!table.checkIfHasMaxTokens(playerToCheck)){
                return;
            }

           int[]setToCheck = table.getSetForPlayer(playerToCheck);
           if (util.testSet(setToCheck)){
                removeAllTokensFromSetSlots(setToCheck);
                removeCardsFromTable(setToCheck);
                placeCardsOnTable();
                players[playerToCheck].point();
                players[playerToCheck].setRefreezeTime(env.config.pointFreezeMillis);
           }
           else {
                players[playerToCheck].penalty();
                players[playerToCheck].setRefreezeTime(env.config.penaltyFreezeMillis);

           }
        }
    }

    /**
     * Removes all tokens from set slots.
     */
    private void removeAllTokensFromSetSlots(int[]setToRemove){
        for(int i=0; i<setToRemove.length; i++){
            if(table.getSlotFromCard(i) != null){
                removeAllTokensFromSlot(table.getSlotFromCard(i));
            }
            
        }
    }

    /**
     * Removes all tokens from slot.
     */
    private void removeAllTokensFromSlot(int slot){
        for(int i=0; i < players.length; i++){
            players[i].removeToken(slot);
        }
    }

    /**
     * Removes card from the table and the tokens from it.
     */
    private void removeCard(int slot){
        if(table.getCardFromSlot(slot) != null){
            removeAllTokensFromSlot(slot);

            // remove cards at screen
            env.ui.removeCard(slot);

            // remove from table
            table.removeCard(slot);

        }
    }

    /**
     * Places card on the table.
     */
    private void placeCard(int slot){ 

        // display cards
        env.ui.placeCard(deck.get(0),slot);

        // save at table
        table.placeCard(deck.get(0),slot);

        // remove cards from deck
        deck.remove(0);
    }

    /**
     * Updates freeze timer for all players.
     */
    private void freezePlayers(){
        for(int i=0; i < players.length; i++){
            players[i].updateFreezeDisplay();
        }
    }

    /**
     * Checks if there is a set on the table.
     */
    private boolean isThereSetOnTable(){
        boolean toReturn = false;
        LinkedList<Integer> list = new LinkedList<>();
        for(int i=0; i < env.config.tableSize; i++){
            if(table.getCardFromSlot(i)!=null){
                list.add(table.getCardFromSlot(i));
            }
        }

        if(env.util.findSets(list, 1).size() != 0){
            toReturn = true;
        }
        return toReturn;
    }
}
