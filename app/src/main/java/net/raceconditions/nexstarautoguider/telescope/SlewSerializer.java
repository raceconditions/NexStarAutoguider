package net.raceconditions.nexstarautoguider.telescope;


public class SlewSerializer {

    /**
     * Serialize Slew direction and speed
     *
     * @param Axis axis of movement
     * @param Direction based on axis
     * @param int arcSeconds per second velocity
     * @return Byte array containing NexStar dms
     */
    public static byte[] serialize(Axis axis, Direction direction, int arcSeconds) {
        int highByte = (int)Math.floor((arcSeconds * 4) / 256);
        int lowByte = (arcSeconds * 4) % 256;
        byte[] slew = new byte[]{'P', (byte) 3, (byte) axis.value, (byte) direction.value, (byte) highByte,
                (byte) lowByte, (byte) 0, (byte) 0, (byte) 0};
        return slew;
    }

    public enum Axis {
        AZM(16),
        ALT(17);

        private int value;

        Axis(int value) {
            this.value = value;
        }
    }

    public enum Direction {
        POS(36),
        NEG(37);

        private int value;

        Direction(int value) {
            this.value = value;
        }
    }

}
