package world.impl;

import java.awt.Color;
import java.util.Random;

import world.Agent;

public class ComplexRabbit extends Agent {
	final int deathFromAge = 20;
	Random r = new Random();
	public int energy = 75;
	public int age = 0;

	@Override
	public void go() {

		// move(r.nextInt(5),r.nextInt(5));
		move(1, 0);
		energy = energy - 10;
		age++;

		if (energy >= 100) {
			reproduce();
		}

		if (age == 20) {
			die();
		}
	}

	@Override
	public void reproduce() {
		energy -= 25;
	}

	@Override
	public void die() {

	}

	@Override
	public Color getColor() {
		// TODO Auto-generated method stub
		return null;
	}
}
