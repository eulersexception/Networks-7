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
        transition[State.WAIT_FOR_ZERO.ordinal()][Msg.WRONG_ALTERNATING.ordinal()] = new ResendLastPacket();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.ALL_FINE.ordinal()] = new ExtractPacketAndSendAck();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.IS_CORRUPT.ordinal()] = new DoNothing();
        transition[State.WAIT_FOR_ONE.ordinal()][Msg.WRONG_ALTERNATING.ordinal()] = new ResendLastPacket();
    }


    abstract class Transition {

        abstract public State execute(Msg input);
    }

    class ExtractPacketAndSendAck extends Transition {

        @Override
        public State execute(Msg Input) {
            System.out.println("Packet arrived - data delivered - ACK sent. Waiting for next Packet.");
            return currentState == State.WAIT_FOR_ZERO? State.WAIT_FOR_ONE : State.WAIT_FOR_ZERO;
        }
    }

    class DoNothing extends Transition {

        @Override
        public State execute(Msg input) {
            return currentState;
        }
    }

    class ResendLastPacket extends Transition {

        @Override
        public State execute(Msg Input) {
            return currentState;
        }
    }

}
