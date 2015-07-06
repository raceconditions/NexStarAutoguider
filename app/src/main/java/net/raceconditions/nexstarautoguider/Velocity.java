package net.raceconditions.nexstarautoguider;

/**
 * Created by raceconditions on 7/2/15.
 */
public class Velocity {
    double velX;
    double velY;
    public Velocity(double velX, double velY){
        this.velX = velX;
        this.velY = velY;
    }

    public double getVelX(){
        return velX;
    }

    public double getVelY(){
        return velY;
    }
}
