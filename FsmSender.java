/**
 * Finate State Machine (FSM) Java Example: Woman
 * (lecture slides for first lecture, p. 19)
 */

/**
 * Class which models the state machine itself.
 */
public class FsmSender {
    // all states for this FSM
    enum State {
        WAIT_CALL_0, WAIT_ACK_0, WAIT_CALL_1, WAIT_ACK_1
    };
    // all messages/conditions which can occur
    enum Msg {
        SEND_ALT_0, RCV_ACK_0, SEND_ALT_1, RCV_ACK_1
    }
    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;
    /**
     * constructor
     */
    public FsmSender(){
        currentState = State.WAIT_CALL_0;
// define all valid state transitions for our state machine
// (undefined transitions will be ignored)
        transition = new Transition[State.values().length] [Msg.values().length];
        transition[State.WAIT_CALL_0.ordinal()] [Msg.SEND_ALT_0.ordinal()] = new SentZero();
        transition[State.WAIT_ACK_0.ordinal()] [Msg.RCV_ACK_0.ordinal()] = new RcvZero();
        transition[State.WAIT_CALL_1.ordinal()] [Msg.SEND_ALT_1.ordinal()] = new SentOne();
        transition[State.WAIT_ACK_1.ordinal()] [Msg.RCV_ACK_1.ordinal()] = new RcvOne();
        System.out.println("INFO FSM constructed, current state: "+currentState);
    }
    /**
     * Process a message (a condition has occurred).
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input){
        System.out.println("INFO Received "+input+" in state "+currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if(trans != null){
            currentState = trans.execute(input);
        }
        System.out.println("INFO State: "+currentState);
    }
    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input);
    }
    class SentZero extends Transition {
        @Override
        public State execute(Msg input) {
            // FileSender methods for sending packet with bit 0
            System.out.println("Hi!");
            return State.WAIT_ACK_0;
        }
    }
    class RcvZero extends Transition {
        @Override
        public State execute(Msg input) {
            // FileSender methods after receiving a packet with ack 0
            System.out.println("Time?");
            return State.WAIT_CALL_1;
        }
    }
    class SentOne extends Transition {
        @Override
        public State execute(Msg input) {
            // FileSender methods for sending packet with bit 1
            System.out.println("Thank you.");
            return State.WAIT_CALL_0;
        }
    }
    class RcvOne extends Transition {
        @Override
        public State execute(Msg input) {
            // FileSender methods after receiving a packet with ack 0
            System.out.println("Thank you.");
            return State.WAIT_CALL_0;
        }
    }
}