// -*- mode:java; encoding:utf-8 -*-
// vim:set fileencoding=utf-8:
// @homepage@

package example;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.swing.*;

public final class MainPanel extends JPanel {
  private final JTextArea textArea = new JTextArea();
  private final JButton runButton = new JButton("Load");
  // private transient SwingWorker<String, Chunk> worker;
  private transient ProgressMonitor monitor;

  public MainPanel() {
    super(new BorderLayout(5, 5));
    textArea.setEditable(false);

    runButton.addActionListener(e -> executeWorker(e));

    Box box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    box.add(runButton);

    add(new JScrollPane(textArea));
    add(box, BorderLayout.SOUTH);
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    setPreferredSize(new Dimension(320, 240));
  }

  protected void executeWorker(ActionEvent e) {
    JButton b = (JButton) e.getSource();
    b.setEnabled(false);
    textArea.setText("");

    // Random random = new Random();
    // Charset cs = Charset.forName("EUC-JP");
    int index = 19; // 1 + random.nextInt(27-1);
    String path = String.format("https://docs.oracle.com/javase/8/docs/api/index-files/index-%d.html", index);
    // String path = String.format("https://docs.oracle.com/javase/7/docs/api/index-files/index-%d.html", index);
    // String path = String.format("https://docs.oracle.com/javase/jp/6/api/index-files/index-%d.html", index);
    // String path = "https://ateraimemo.com/";
    append(path);

    URLConnection urlConnection = null;
    try {
      // URLConnection urlConnection = getUrlConnection(path);
      urlConnection = new URL(path).openConnection();
    } catch (IOException ex) {
      ex.printStackTrace();
      textArea.setText("error: " + ex.getMessage());
      return;
    }
    append("urlConnection.getContentEncoding(): " + urlConnection.getContentEncoding());
    append("urlConnection.getContentType(): " + urlConnection.getContentType());

    Charset cs = getCharset(urlConnection, "UTF-8");
    int length = urlConnection.getContentLength();
    SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
    try (InputStream is = urlConnection.getInputStream();
         ProgressMonitorInputStream pmis = new ProgressMonitorInputStream(b.getRootPane(), "Loading", is)) {
      monitor = pmis.getProgressMonitor();
      monitor.setNote(" "); // Need for JLabel#getPreferredSize
      monitor.setMillisToDecideToPopup(0);
      monitor.setMillisToPopup(0);
      monitor.setMinimum(0);
      monitor.setMaximum(length);
      MonitorTask task = new MonitorTask(pmis, cs, length) {
        @Override public void done() {
          super.done();
          loop.exit();
        }
      };
      task.execute();
      loop.enter();
    } catch (IOException ex) {
      ex.printStackTrace();
      textArea.setText("error: " + ex.getMessage());
    }
  }

  private static Charset getCharset(URLConnection urlConnection, String defaultEncoding) {
    Charset cs = Charset.forName(defaultEncoding);
    String encoding = urlConnection.getContentEncoding();
    if (Objects.nonNull(encoding)) {
      cs = Charset.forName(encoding);
    } else {
      String contentType = urlConnection.getContentType();
      Stream.of(contentType.split(";"))
        .map(String::trim)
        .filter(s -> !s.isEmpty() && s.toLowerCase(Locale.ENGLISH).startsWith("charset="))
        .map(s -> s.substring("charset=".length()))
        .findFirst()
        .ifPresent(Charset::forName);
    }
    System.out.println(cs);
    return cs;
  }

  private class MonitorTask extends BackgroundTask {
    protected MonitorTask(ProgressMonitorInputStream pmis, Charset cs, int length) {
      super(pmis, cs, length);
    }

    @Override protected void process(List<Chunk> chunks) {
      if (isCancelled()) {
        return;
      }
      if (!isDisplayable()) {
        cancel(true);
        return;
      }
      processChunks(chunks);
    }

    @Override public void done() {
      updateComponentDone();
      String text;
      try {
        if (Objects.nonNull(pmis)) {
          pmis.close();
        }
        text = isCancelled() ? "Cancelled" : get();
      } catch (InterruptedException ex) {
        text = "Interrupted";
      } catch (IOException | ExecutionException ex) {
        ex.printStackTrace();
        text = "Error:" + ex.getMessage();
      }
      append(text);
    }
  }

  protected void updateComponentDone() {
    runButton.setEnabled(true);
  }

  protected void processChunks(List<Chunk> chunks) {
    chunks.forEach(c -> {
      append(c.line);
      monitor.setNote(c.note);
    });
  }

  protected void append(String str) {
    textArea.append(str + "\n");
    textArea.setCaretPosition(textArea.getDocument().getLength());
  }

  public static void main(String... args) {
    EventQueue.invokeLater(new Runnable() {
      @Override public void run() {
        createAndShowGui();
      }
    });
  }

  public static void createAndShowGui() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
      ex.printStackTrace();
      Toolkit.getDefaultToolkit().beep();
    }
    JFrame frame = new JFrame("@title@");
    // frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.getContentPane().add(new MainPanel());
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}

class Chunk {
  public final String line;
  public final String note;

  protected Chunk(String line, String note) {
    this.line = line;
    this.note = note;
  }
}

class BackgroundTask extends SwingWorker<String, Chunk> {
  protected final ProgressMonitorInputStream pmis;
  private final Charset cs;
  private final int length;

  protected BackgroundTask(ProgressMonitorInputStream pmis, Charset cs, int length) {
    super();
    this.pmis = pmis;
    this.cs = cs;
    this.length = length;
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  @Override public String doInBackground() {
    String ret = "Done";
    // try (BufferedReader reader = new BufferedReader(new InputStreamReader(pmis, cs))) {
    //   int i = 0;
    //   int size = 0;
    //   String line;
    //   while ((line = reader.readLine()) != null) {
    //     if (i % 50 == 0) { // Wait
    //       Thread.sleep(10);
    //     }
    //     i++;
    //     size += line.getBytes(cs).length + 1; // +1: \n
    //     String note = String.format("%03d%% - %d/%d%n", 100 * size / length, size, length);
    //     publish(new Chunk(line, note));
    //   }
    // } catch (InterruptedException | IOException ex) {
    //   append("Exception");
    //   ret = "Exception";
    //   cancel(true);
    // }
    try (Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(pmis, cs)))) {
      int i = 0;
      int size = 0;
      while (scanner.hasNextLine()) {
        if (i % 50 == 0) { // Wait
          Thread.sleep(10);
        }
        i++;
        String line = scanner.nextLine();
        size += line.getBytes(cs).length + 1; // +1: \n
        String note = String.format("%03d%% - %d/%d%n", 100 * size / length, size, length);
        publish(new Chunk(line, note));
      }
    } catch (InterruptedException ex) {
      ret = "Interrupted";
      cancel(true);
    }
    return ret;
  }
}
