package net.raceconditions.nexstarautoguider;

/**
 * Created by raceconditions on 7/2/15.
 */
public class Velocity {
    double velX;
    double velY;
    double milliseconds;
    public Velocity(double velX, double velY, double milliseconds){
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

    public double getMilliseconds() { return milliseconds; }
}
