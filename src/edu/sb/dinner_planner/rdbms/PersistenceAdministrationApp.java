package edu.sb.dinner_planner.rdbms;

import java.io.IOException;
//import tool.Copyright;


/**
 * Persistence administration application type.
 */
//@Copyright(year=2022, holders="Sascha Baumeister")
public class PersistenceAdministrationApp {

	/**
	 * Application entry point.
	 * @param args the runtime arguments
	 * @throws IOException if there is an I/O related problem
	 */
	static public void main (final String[] args) throws IOException {
		final PersistenceAdministrationController controller = new PersistenceAdministrationController();
		controller.terminal().processCommands();
		System.out.println("bye!");
	}
}