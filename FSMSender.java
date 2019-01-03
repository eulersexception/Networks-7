/**
 * Finate State Machine (FSM) Java Example: Woman
 * (lecture slides for first lecture, p. 19)
 */

/**
 * Class which models the state machine itself.
 */
public class FSMSender {
    // all states for this FSM
    enum State {
        WAIT_CALL_0, WAIT_ACK_0, WAIT_CALL_1, WAIT_ACK_1
    }
    // all messages/conditions which can occur
    enum Msg {
        SEND, ALL_FINE, TIMEOUT, CORRUPT_OR_WRONG_BIT
    }
    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;
    /**
     * constructor
     */
    public FSMSender(){
        currentState = State.WAIT_CALL_0;
        transition = new Transition[State.values().length] [Msg.values().length];

        transition[State.WAIT_CALL_0.ordinal()] [Msg.SEND.ordinal()] = new SendPacket();
        //transition[State.WAIT_CALL_0.ordinal()] [Msg.RCV.ordinal()] = new DoNothing();

        transition[State.WAIT_ACK_0.ordinal()] [Msg.ALL_FINE.ordinal()] = new ReceiveACK();
        transition[State.WAIT_ACK_0.ordinal()] [Msg.CORRUPT_OR_WRONG_BIT.ordinal()] = new DoNothing();
        transition[State.WAIT_ACK_0.ordinal()] [Msg.TIMEOUT.ordinal()] = new ResendAfterTimeout();

        transition[State.WAIT_CALL_1.ordinal()] [Msg.SEND.ordinal()] = new SendPacket();
        //transition[State.WAIT_CALL_1.ordinal()] [Msg.RCV.ordinal()] = new DoNothing();

        transition[State.WAIT_ACK_1.ordinal()] [Msg.ALL_FINE.ordinal()] = new ReceiveACK();
        transition[State.WAIT_ACK_1.ordinal()] [Msg.CORRUPT_OR_WRONG_BIT.ordinal()] = new DoNothing();
        transition[State.WAIT_ACK_1.ordinal()] [Msg.TIMEOUT.ordinal()] = new ResendAfterTimeout();

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
    class SendPacket extends Transition {
        @Override
        public State execute(Msg input) {
            int bitNumber = 0;
            if(currentState == State.WAIT_CALL_0) {
                System.out.println("Packet with bit 0 sent, wait for ACK 0");
                currentState = State.WAIT_ACK_0;
            }
            else {
                System.out.println("Packet sent, wait for ACK 1");
                bitNumber = 1;
                currentState = State.WAIT_ACK_1;
            }

            // send(bitNumber)
            return currentState;
        }
    }

    class ReceiveACK extends Transition {
        @Override
        public State execute(Msg input) {
            if(currentState == State.WAIT_ACK_0) {
                System.out.println("ACK 0 received.");
                currentState = State.WAIT_CALL_0;
            }
            else {
                System.out.println("ACK 1 received.");
                currentState = State.WAIT_CALL_1;
            }
            return currentState;
        }
        // stoptimer();
    }

    class DoNothing extends Transition {
        @Override
        public State execute(Msg input) {
            System.out.println("ACK was received, but is either corrupt or has wrong bit.");
            return currentState;
        }
    }

    class ResendAfterTimeout extends Transition {
        @Override
        public State execute(Msg input) {
            int bitNumber = 0;
            if(currentState == State.WAIT_ACK_0) {
                System.out.println("Timeout occured: Resend packet 0.");
            }
            else {
                System.out.println("Timeout occured: Resend packet 1.");
                bitNumber = 1;
            }
            // resend(bitNumber)
            return currentState;
        }
    }
}