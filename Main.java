/**
 * Main.java
 * 程序入口
 */
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SDES_GUI gui = new SDES_GUI();
            gui.setVisible(true);
        });
    }
}
