package fieldbox;

import field.graphics.*;
import field.utility.Options;
import fieldagent.Main;
import fieldbox.io.IO;

import java.awt.*;

/**
 * The main entry-point for Field2.
 */
public class FieldBox {

	static public final FieldBox fieldBox = new FieldBox();


	// TODO --- there needs to be mechanism to set this from someplace other than my home directory
	public final IO io = new IO("/Users/marc/Documents/FirstNewFieldWorkspace/");

	{
		io.addFilespec("code", io.EXECUTION, io.EXECUTION);


	}

	public void go() {
		RunLoop.main.enterMainLoop();
	}


	static public void main(String[] s) {

		if (Main.os== Main.OS.mac)
			Toolkit.getDefaultToolkit();

		// TODO --- get from command line / previous
		Options.parseCommandLine(s);

		Open open = new Open("testFile.field2");

		// TODO --- save automatically on exit
		fieldBox.go();
	}



}
