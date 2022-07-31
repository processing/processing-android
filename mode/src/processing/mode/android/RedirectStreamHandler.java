package processing.mode.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;


public class RedirectStreamHandler extends Thread {
    // Streams to redirect from and to
    private final InputStream input;
    private final PrintWriter output;

    RedirectStreamHandler(PrintWriter output, InputStream input) {
      this.input = input;
      this.output = output;
      start();
    }

    @Override
    public void run() {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;
        while ((line = reader.readLine()) != null) {
            output.println(line);
        }
      } catch (IOException ioe) {
        // OK to ignore...
        System.out.println("Level.WARNING____I/O Redirection failure: "+ ioe.toString());
      }
    }
  }
