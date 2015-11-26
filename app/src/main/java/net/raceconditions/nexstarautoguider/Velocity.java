package net.raceconditions.nexstarautoguider;

/**
 * Created by raceconditions on 7/2/15.
 */
public class Velocity {
    double velX;
    double velY;
    long milliseconds;
    public Velocity(double velX, double velY, long milliseconds){
        this.velX = velX;
        this.velY = velY;
        this.milliseconds = milliseconds;
    }

    public double getVelX(){
        return velX;
    }

    public double getVelY(){
        return velY;
    }

    public long getMilliseconds() { return milliseconds; }
}
