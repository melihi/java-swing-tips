// -*- mode:java; encoding:utf-8 -*-
// vim:set fileencoding=utf-8:
// @homepage@

package example;

import java.awt.*;
import javax.swing.*;

public final class MainPanel extends JPanel {
  private MainPanel() {
    super(new BorderLayout());
    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() throws InterruptedException {
        Thread.sleep(3000);
        return null;
      }
    }.execute();

    add(new JScrollPane(new JTree()));
    setPreferredSize(new Dimension(320, 240));
  }

  public static void main(String[] args) {
    System.out.println("main start / EDT: " + EventQueue.isDispatchThread());
    createAndShowGui();
    System.out.println("main end");
  }

  private static void createAndShowGui() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
      ex.printStackTrace();
      Toolkit.getDefaultToolkit().beep();
    }
    JFrame frame = new JFrame("@title@");
    JDialog splashScreen = new JDialog(frame, Dialog.ModalityType.DOCUMENT_MODAL);
    JProgressBar progress = new JProgressBar();

    System.out.println(splashScreen.getModalityType());

    EventQueue.invokeLater(() -> {
      splashScreen.setUndecorated(true);
      splashScreen.getContentPane().add(new JLabel(new ImageIcon(MainPanel.class.getResource("splash.png"))));
      splashScreen.getContentPane().add(progress, BorderLayout.SOUTH);
      splashScreen.pack();
      splashScreen.setLocationRelativeTo(null);
      splashScreen.setVisible(true);
    });
    SwingWorker<Void, Void> worker = new BackgroundTask() {
      @Override protected void done() {
        splashScreen.dispose();
      }
    };
    worker.addPropertyChangeListener(e -> {
      if ("progress".equals(e.getPropertyName())) {
        progress.setValue((Integer) e.getNewValue());
      }
    });
    worker.execute();

    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.getContentPane().add(new MainPanel());
    frame.pack();
    frame.setLocationRelativeTo(null);
    EventQueue.invokeLater(() -> frame.setVisible(true));
  }
}

class BackgroundTask extends SwingWorker<Void, Void> {
  @Override protected Void doInBackground() throws InterruptedException {
    int current = 0;
    int lengthOfTask = 120;
    while (current < lengthOfTask && !isCancelled()) {
      doSomething(100 * current++ / lengthOfTask);
    }
    return null;
  }

  protected void doSomething(int progress) throws InterruptedException {
    Thread.sleep(50);
    setProgress(progress);
  }
}
