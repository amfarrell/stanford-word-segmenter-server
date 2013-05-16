package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.io.EncodingPrintWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;


/*****************************************************************************
 * A named-entity recognizer server for Stanford's NER.
 * Runs on a socket and waits for text to annotate and returns the
 * annotated text.  (Internally, it uses the <code>classifyString()</code>
 * method on a classifier, which can be either the default CRFClassifier
 * which is serialized inside the jar file from which it is called, or another
 * classifier which is passed as an argument to the main method.
 *
 * @version $Id: NERServer.java 23962 2009-01-17 02:02:32Z manning $
 * @author
 *      Bjorn Aldag<BR>
 *      Copyright &copy; 2000 - 2004 Cycorp, Inc.  All rights reserved.
 *      Permission granted for Stanford to distribute with their NER code
 *      by Bjorn Aldag
 * @author Christopher Manning 2006 (considerably rewritten)
 *
*****************************************************************************/

public class NERServer {

  //// Variables

  /**
   * Debugging toggle.
   */
  private boolean DEBUG = true;

  private final String charset;

  /**
   * The listener socket of this server.
   */
  private final ServerSocket listener;

  /**
   * The classifier that does the actual tagging.
   */
  private final AbstractSequenceClassifier ner;

  /**
   * Output format of tagged text.
   */
  private final String format;
  
  /**
   * Flag indicating whether spacing is preserved.
   */
  private final boolean spacing;

  //// Constructors

  /**
   * Creates a new named entity recognizer server on the specified port.
   *
   * @param port the port this NERServer listens on.
   * @param asc The classifier which will do the tagging
   * @param charset The character set for encoding Strings over the socket stream, e.g., "utf-8"
   * @throws IOException If there is a problem creating a ServerSocket
   */
  public NERServer(int port, AbstractSequenceClassifier asc, String charset, String format, boolean spacing) throws IOException {
    ner = asc;
    listener = new ServerSocket(port);
    this.charset = charset;
	this.format = format;
	this.spacing = spacing;
  }

  //// Public Methods

  /**
   * Runs this named entity recognizer server.
   */
  @SuppressWarnings({"InfiniteLoopStatement", "ConstantConditions"})
  public void run() {
    Socket client = null;
    while (true) {
      try {
        client = listener.accept();
        if (DEBUG) {
          System.err.print("Accepted request from ");
          System.err.println(client.getInetAddress().getHostName());
        }
        new Session(client);
      } catch (Exception e1) {
        System.err.println("NERServer: couldn't accept");
        e1.printStackTrace(System.err);
        try {
          client.close();
        } catch (Exception e2) {
          System.err.println("NERServer: couldn't close client");
          e2.printStackTrace(System.err);
        }
      }
    }
  }


  //// Inner Classes

  /**
   * A single user session, accepting one request, processing it, and
   * sending back the results.
   */
  private class Session extends Thread {

  //// Instance Fields

    /**
     * The socket to the client.
     */
    private final Socket client;

    /**
     * The input stream from the client.
     */
    private final BufferedReader in;

    /**
     * The output stream to the client.
     */
    private PrintWriter out;


    //// Constructors

    private Session(Socket socket) throws IOException {
      client = socket;
      in = new BufferedReader(new InputStreamReader(client.getInputStream(), charset));
      out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), charset));
      start();
    }


    //// Public Methods

    /**
     * Runs this session by reading a string, tagging it, and writing
     * back the result.  The input should be a single line (no embedded
     * newlines), which represents a whole sentence or document.
     */
    @Override
    public void run() {
      if (DEBUG) {System.err.println("Created new session");}
      String input = null;
      try {
        input = in.readLine();
        //if (DEBUG) {
        if (true) {
          EncodingPrintWriter.err.println("Receiving: \"" + input + '\"', charset);
        }
      }
      catch (IOException e) {
        System.err.println("NERServer:Session: couldn't read input");
        e.printStackTrace(System.err);
      }
      catch (NullPointerException npe) {
        System.err.println("NERServer:Session: connection closed by peer");
        npe.printStackTrace(System.err);
      }
      if (! (input == null)) {
        //String output = ner.classifyToString(input);
        String output = ner.classifyToString(input, format, spacing);
        if (DEBUG) {
          EncodingPrintWriter.err.println("Sending: \"" + output + '\"', charset);
        }
        out.print(output);
        out.flush();
      }
      close();
    }

    /**
     * Terminates this session gracefully.
     */
    private void close() {
      try {
        in.close();
        out.close();
        client.close();
      } catch (Exception e) {
        System.err.println("NERServer:Session: can't close session");
        e.printStackTrace(System.err);
      }
    }

  } // end class Session

  /** This example sends material to the NER server one line at a time.
   *  Each line should be at least a whole sentence, or can be a whole
   *  document.
   */
  private static class NERClient {

    private NERClient() {}

    private static void communicateWithNERServer(String host, int port, String charset) throws IOException {

      if (host == null) {
        host = "localhost";
      }

      BufferedReader stdIn = new BufferedReader(
              new InputStreamReader(System.in, charset));
      System.out.println("Input some text and press RETURN to NER tag it, or just RETURN to finish.");

      for (String userInput; (userInput = stdIn.readLine()) != null && ! userInput.matches("\\n?"); ) {
        try {
          Socket socket = new Socket(host, port);
          PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset), true);
          BufferedReader in = new BufferedReader(new InputStreamReader(
                  socket.getInputStream(), charset));
          // send material to NER to socket
          out.println(userInput);
          // Print the results of NER
          EncodingPrintWriter.out.println(in.readLine(), charset);
          in.close();
          socket.close();
        } catch (UnknownHostException e) {
          System.err.print("Cannot find host: ");
          System.err.println(host);
          return;
        } catch (IOException e) {
          System.err.print("I/O error in the connection to: ");
          System.err.println(host);
          return;
        }
      }
      stdIn.close();
    }
  } // end static class NERClient


  private static final String USAGE = 
    "Usage: NERServer [-loadClassifier file|-loadJarClassifier resource|-client] " +
	"-outputFormat [slashTags|xml|inlineXML] -preserveSpacing [true|false] -port portNumber";

  /**
   * Starts this server on the specified port.  The classifier used can be
   * either a default one stored in the jar file from which this code is
   * invoked or you can specify it as a filename or as another classifier
   * resource name, which must correspond to the name of a resource in the
   * /classifiers/ directory of the jar file.
   * <p>
   * Usage: <code>java edu.stanford.nlp.ie.NERServer [-loadClassifier file|-loadJarClassifier resource|-client] 
   * -outputFormat [slashTags|xml|inlineXML] -preserveSpacing [true|false] -port portNumber</code>
   *
   * @param args Command-line arguments (described above)
   * @throws Exception If file or Java class problems with serialized classifier
   */
  @SuppressWarnings({"StringEqualsEmptyString"})
  public static void main (String[] args) throws Exception {
    //defaults
    int port = 1234;
    String charset = "utf-8";
    String outputFormat = "slashTags";
    boolean preserveSpacing = true;

    Properties props = StringUtils.argsToProperties(args);
    String loadFile = props.getProperty("loadClassifier");
    String loadJarFile = props.getProperty("loadJarClassifier");
    String client = props.getProperty("client");
    String portStr = props.getProperty("port");
    String outputFormatStr = props.getProperty("outputFormat");
    String preserveSpacingStr = props.getProperty("preserveSpacing");
    String encoding = props.getProperty("encoding");

    if (portStr == null || portStr.equals("")) {
        System.err.println(USAGE);
        return;
    } else {
          try {
        port = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        System.err.println("Non-numerical port");
        System.err.println(USAGE);
        System.exit(1);
      }
      }

    if (encoding != null && ! "".equals(encoding)) {
        charset = encoding;
      }

    if (outputFormatStr != null && !"".equals(outputFormatStr) && !"slashTags".equals(outputFormatStr)
        && !"xml".equals(outputFormatStr) && !"inlineXML".equals(outputFormatStr)) {
      System.err.println(USAGE);
      return;
    } else {
      outputFormat = outputFormatStr;
    }

    if (preserveSpacingStr != null && ! "".equals(preserveSpacingStr)) {
      preserveSpacing = Boolean.valueOf(preserveSpacingStr);
    }

    AbstractSequenceClassifier asc;
    if (loadFile != null && ! loadFile.equals("")) {
      System.out.println(String.format("Using classifier loaded from %s", loadFile));
      asc = CRFClassifier.getClassifier(loadFile, props);
    } else if (loadJarFile != null && ! loadJarFile.equals("")) {
      asc = CRFClassifier.getJarClassifier(loadJarFile, props);
    } else {
      asc = CRFClassifier.getDefaultClassifier();
    }

    new NERServer(port, asc, charset, outputFormat, preserveSpacing).run();
  }

}
