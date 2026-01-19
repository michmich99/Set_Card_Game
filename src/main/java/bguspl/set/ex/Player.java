package bguspl.set.ex;

import bguspl.set.Env;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The current slots the player pressed.
     */
   
    ArrayBlockingQueue<Integer> playersQueue; //package private so we can use in the tests

    /**
     * The number of current slots the player pressed.
     */
    private int numOfSlotsPressed;

    /**
     * The Dealer.
     */
    private Dealer dealer;

    /**
     * is the player sleeping.
     */
    private boolean isSleeping;

    /**
     * -1 if should get a point, -2 if should get a penalty.
     */
    int pointOrPenalty; //package private so we can use in the tests

    /**
     * The time when the player can return to play
     */
    long refreezeTime = Long.MAX_VALUE; //package private so we can use in the tests

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        this.dealer = dealer;
        this.playersQueue = new ArrayBlockingQueue<>(env.config.featureSize);
        this.numOfSlotsPressed = 0;

        this.pointOrPenalty = 0;

        this.isSleeping = false;

        this.refreezeTime = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try {
                synchronized (this) {
                wait(); }
            } catch (InterruptedException ignored){}

            doAnAction();   
        }

        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {

                int r = (int) ((Math.random() * (12)));
                keyPressed(r);

                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}

            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * 
     * @post - the int representing the pressed slot is inserted to the playersQueue.
     */
    public void keyPressed(int slot) {
        if(!isSleeping && (numOfSlotsPressed < env.config.featureSize || table.playerHasToken(this.id, slot))){
            try {
                playersQueue.put(slot);

                synchronized (this) { notifyAll();}
                
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Doing the current action that should be made - freezing or putting/removing token.
     */
    private void doAnAction(){
        int currSlot;
        try {
            if(pointOrPenalty == -1){ //got legal set- sleep for PointFreezeSeconds
                if(env.config.pointFreezeMillis != 0){
                    isSleeping = true;
                    Thread.sleep(env.config.pointFreezeMillis);
                    isSleeping = false;
                }
                playersQueue.clear();
                pointOrPenalty = 0;
            }
            else if(pointOrPenalty == -2){ //wrong set- sleep for PenaltyFreezeSeconds
                if(env.config.penaltyFreezeMillis != 0){
                    isSleeping = true;
                    Thread.sleep(env.config.penaltyFreezeMillis);
                    isSleeping = false;
                }
                playersQueue.clear();
                pointOrPenalty = 0;
            }
            else{
                currSlot = playersQueue.take();

                if(!table.playerHasToken(this.id, currSlot)){
                    if(numOfSlotsPressed < env.config.featureSize){
                        placeToken(currSlot);
                    }
                }
                else{
                    removeToken(currSlot);
                }

                if(numOfSlotsPressed == env.config.featureSize){
                    dealer.addRequest(id);
               }
            }

        } catch (InterruptedException ignored) {}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        pointOrPenalty = -1;

        synchronized (this) { notifyAll(); }
    }

    /**
     * Penalize a player and perform other related actions.
     * 
     * @post - the pointOrPenalty variable equals -2.
     */
    public void penalty() {
        pointOrPenalty = -2;

        synchronized (this) { notifyAll(); }

    }

    /**
     * Returns the score of the player.
     */
    public int score() {
        return score;
    }

    /**
     * Placing a token on the slot.
     */
    public void placeToken(int slot){
        if(table.getCardFromSlot(slot) != null && table.tableAvailable && table.slotAvailable[slot]){
            table.placeToken(id,slot);
            env.ui.placeToken(id,slot);
            numOfSlotsPressed++;
        }
    }

    /**
     * Removing the token on the slot.
     */
    public void removeToken(int slot){
        if(table.getCardFromSlot(slot) != null && table.playerHasToken(id, slot)){
            table.removeToken(id,slot);
            env.ui.removeToken(id,slot);
            numOfSlotsPressed--;
        }
        
    }

    /**
     * Removing all players tokens.
     */
    public void removeAllPlayerTokens(){
        for (int i = 0; i < env.config.tableSize; i++){
            removeToken(i);
        }
    }

    /**
     * Updates the freezing display.
     */
    public void updateFreezeDisplay() {
        env.ui.setFreeze(id, refreezeTime-System.currentTimeMillis());
    }

    /**
     * Sets the refreezeTime to be the current time + the number of seconds the freeze should be.
     */
    public void setRefreezeTime(long time){
        refreezeTime = System.currentTimeMillis() + time;
    }
}
