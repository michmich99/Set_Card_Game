# Set_Card_Game

This project implements a concurrent Java version of the Set card game, built to practice threads, synchronization, and unit testing. The game runs with a dedicated Dealer thread and multiple Player threads (human and/or AI), coordinated over a shared Table while maintaining correctness and fairness (FIFO handling of set claims).

## Features

* Multi-threaded game engine: one Dealer thread + one thread per Player (+ optional AI input thread).
* Thread-safe Table shared between dealer and players (cards + tokens).
* Bounded action queue per player (size = 3) to model “select 3 cards” behavior.
* Fair set validation: players’ set claims are processed in FIFO order.
* Penalty/point freeze logic: players are temporarily blocked after incorrect/correct claims.
* Keyboard input with explicit UI updates.
* JUnit testing (with optional Mockito) for core components.

## How to Run

### Build

From the project root  (where the pom.xml file is):

```
mvn clean compile test
```

### Start the Game

```
java -cp target/classes bguspl.set.Main
```

## Configuration

Game behavior is controlled via `config.properties` (loaded through `Config`):

* Number of human/computer players
* Turn timeout behavior
* Freeze durations (penalty/point)
* Table delays

## Implementation Notes

* Dealer controls the game loop: dealing, reshuffling, timing, set verification, scoring, and termination.
* Players consume actions from a bounded queue, place/remove tokens, and block while waiting for dealer validation.
* Synchronization is applied only where needed to avoid:
  * race conditions on shared state (table/cards/tokens)
  * deadlocks
  * excessive wakeups / busy waiting
