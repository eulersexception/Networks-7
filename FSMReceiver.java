/**
 * Networks 2018/2019
 * Lab 7:
 * - file transmission via UDP Alternating-Bit-Protocol compared to TCP
 * - unreliable channel
 * - finite state machine for FileSender and FileReceiver
 *
 * @author Erwin Kupris, kupris@hm.edu // Bahadir Süzer, suezer@hm.edu
 * @version 2019-01-13
 */

public class FSMReceiver {

    enum State {
        WAIT_FOR_ZERO, WAIT_FOR_ONE
    }

    enum Msg {
        ALL_FINE, IS_CORRUPT, WRONG_ALTERNATING
    }


    private State currentState;

    private Transition[][] transition;

    public FSMReceiver() {
        currentState = State.WAIT_FOR_ZERO;

        transition = new Transition[State.values().length][Msg.values().length];
        transition[State.WAIT_FOR_ZERO.ordinal()][Msg.ALL_FINE.ordinal()] = new ExtractPacketAndSendAck();
        transition[State.WAIT_FOR_ZERO.ordinal()][Msg.IS_CORRUPT.ordinal()] = new DoNothing();
        transition[State.WAIT_FOR_ZERO.ordinal()][Msg.WRONG_ALTERNATING.ordinal()] = new DoNothing();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.ALL_FINE.ordinal()] = new ExtractPacketAndSendAck();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.IS_CORRUPT.ordinal()] = new DoNothing();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.WRONG_ALTERNATING.ordinal()] = new DoNothing();
        System.out.println("INFO: FSM Receiver constructed, current state: "+currentState);
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

    abstract class Transition {
        abstract public State execute(Msg input);
    }

    class ExtractPacketAndSendAck extends Transition {

        @Override
        public State execute(Msg Input) {
            if(currentState == State.WAIT_FOR_ZERO) {
                System.out.println("Packet arrived - data delivered - ACK sent. Waiting for next packet with Alternating Bit = 1.");
                currentState = State.WAIT_FOR_ONE;
            }
            else {
                System.out.println("Packet arrived - data delivered - ACK sent. Waiting for next packet with Alternating Bit = 0.");
                currentState = State.WAIT_FOR_ZERO;
            }
            return currentState;
        }
    }

    class DoNothing extends Transition {

        @Override
        public State execute(Msg input) {
            if(currentState == State.WAIT_FOR_ZERO) {
                System.out.println("Corrupt packet - no further action - waiting for retransmission of packet with Alternating Bit = 1.");
            }
            else {
                System.out.println("Corrupt packet - no further action - waiting for retransmission of packet with Alternating Bit = 0.");
            }
            return currentState;
        }
    }

    class ResendLastACK extends Transition {

        @Override
        public State execute(Msg Input) {
            if(currentState == State.WAIT_FOR_ZERO) {
                System.out.println("Wrong alternating bit - retransmission of last ACK - waiting for retransmission of packet with Alternating Bit = 1.");
            }
            else {
                System.out.println("Wrong alternating bit - retransmission of last ACK - waiting for retransmission of packet with Alternating Bit = 0.");
            }
            return currentState;
        }
    }

}
