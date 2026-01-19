package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

//import java.util.LinkedList;
//import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;

// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;


    void assertInvariants() {
    }

    @BeforeEach
    void setUp() {
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player[] players = new Player[2];
        Player player1 = new Player(env, dealer, table, 0, false);
        Player player2 = new Player(env, dealer, table, 1, false);
        players[0] = player1;
        players[1] = player2;
        dealer = new Dealer(env, table, players);

        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void addRequest(){ //our test
        int expectedPendingRequestsFromPlayersSize = dealer.pendingRequestsFromPlayers.size()+1;
        int expectedElementPendingRequestsFromPlayers = 1;

        // call the method we are testing
        dealer.addRequest(1);

        // check that the element was added correctly
        assertEquals(expectedPendingRequestsFromPlayersSize, dealer.pendingRequestsFromPlayers.size());
        assertEquals(expectedElementPendingRequestsFromPlayers, dealer.pendingRequestsFromPlayers.poll());
    }

    @Test
    void checkRequests(){
        int expectedPendingRequestsFromPlayersSize = 0;

        // call the method we are testing
        dealer.checkRequests();

        // check that the dealer took all the requests from the queue
        assertEquals(expectedPendingRequestsFromPlayersSize, dealer.pendingRequestsFromPlayers.size());
    }



}