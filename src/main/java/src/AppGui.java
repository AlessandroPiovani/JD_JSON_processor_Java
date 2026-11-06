package src;



import src.ProcessingRunner;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Interfaccia grafica per eseguire la destagionalizzazione e la verifica di coerenza
 * (stessa elaborazione di Main_with_checks) senza usare NetBeans.
 *
 * Avvio: doppio clic sul JAR, oppure  java -jar JD_JSON_processor.jar
 *
 * @author alessandro.piovani@istat.it
 */
public class AppGui extends JFrame {

    private static final Color HINT = new Color(130, 130, 130);
    private final Preferences prefs = Preferences.userNodeForPackage(AppGui.class);

    private final JTextField rawField  = new JTextField(40);
    private final JTextField regrField = new JTextField(40);
    private final JTextField specField = new JTextField(40);
    private final JTextField siticField = new JTextField(40);
    private final JTextField wsField   = new JTextField(40);
    private final JTextField outField  = new JTextField(40);
    private final JTextField absTolField = new JTextField("0.01", 8);
    private final JTextField relTolField = new JTextField("0.001", 8);

    private final JRadioButton onlySitic = new JRadioButton("Solo TS+");
    private final JRadioButton onlyWs    = new JRadioButton("Solo workspace");
    private final JRadioButton both      = new JRadioButton("Entrambe", true);
    private final JCheckBox saveBox      = new JCheckBox("Salva workspace e report", true);

    private final JButton siticBrowse = new JButton("Sfoglia...");
    private final JButton wsBrowse    = new JButton("Sfoglia...");
    private final JButton outBrowse   = new JButton("Sfoglia...");
    private final JButton runButton   = new JButton("Esegui");
    private final JButton openReport  = new JButton("Apri report");
    private final JLabel  status      = new JLabel(" ");
    private final JTextArea console   = new JTextArea(22, 100);

    private String lastReport;

    public AppGui() {
        super("Destagionalizzazione JD+ - elaborazione e verifica");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        int row = 0;
        row = addFileRow(form, row, "Dati grezzi:", rawField, "(es. grezzi.csv)", fileButton(rawField, false, "csv"));
        row = addFileRow(form, row, "Cartella regressori:", regrField, "(es. cartella regr)", fileButton(regrField, true, null));
        row = addFileRow(form, row, "Specifiche JSON:", specField, "(es. specifications_new.txt)", fileButton(specField, false, null));

        // Scelta dei confronti
        JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ButtonGroup group = new ButtonGroup();
        for (JRadioButton r : new JRadioButton[]{onlySitic, onlyWs, both}) {
            group.add(r);
            radios.add(r);
            r.addActionListener(e -> updateEnabled());
        }
        addRow(form, row++, new JLabel("Verifica con:"), radios, null, null);

        siticBrowse.addActionListener(e -> choose(siticField, false, "csv"));
        row = addFileRow(form, row, "SA di TS+:", siticField, "(es. sa_new.csv)", siticBrowse);
        wsBrowse.addActionListener(e -> choose(wsField, false, "xml"));
        row = addFileRow(form, row, "Workspace di confronto:", wsField, "(es. percorso/workspace.xml)", wsBrowse);

        // Tolleranze
        JPanel tol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tol.add(new JLabel("assoluta"));
        tol.add(absTolField);
        tol.add(new JLabel("relativa (%)"));
        tol.add(relTolField);
        JLabel tolHint = new JLabel("un periodo e' coerente se rientra in almeno una delle due");
        tolHint.setForeground(HINT);
        tol.add(tolHint);
        addRow(form, row++, new JLabel("Tolleranze:"), tol, null, null);

        // Salvataggio
        saveBox.addActionListener(e -> updateEnabled());
        addRow(form, row++, new JLabel(""), saveBox, null, null);
        outBrowse.addActionListener(e -> choose(outField, true, null));
        row = addFileRow(form, row, "Cartella di output:", outField, "(workspace e report)", outBrowse);

        // Pulsanti
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runButton.addActionListener(e -> runProcessing());
        openReport.setEnabled(false);
        openReport.addActionListener(e -> openFile(lastReport));
        JButton clear = new JButton("Pulisci console");
        clear.addActionListener(e -> console.setText(""));
        buttons.add(runButton);
        buttons.add(openReport);
        buttons.add(clear);
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        buttons.add(status);
        addRow(form, row++, new JLabel(""), buttons, null, null);

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(BorderFactory.createTitledBorder("Console"));

        getContentPane().add(form, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);

        loadPrefs();
        updateEnabled();
        redirectConsole();
        pack();
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private int addFileRow(JPanel p, int row, String label, JTextField field, String hint, JButton browse) {
        JLabel h = new JLabel(hint);
        h.setForeground(HINT);
        addRow(p, row, new JLabel(label), field, browse, h);
        return row + 1;
    }

    private void addRow(JPanel p, int row, JLabel label, java.awt.Component main, JButton browse, JLabel hint) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.gridy = row;
        g.gridx = 0;
        g.anchor = GridBagConstraints.LINE_END;
        p.add(label, g);
        g.gridx = 1;
        g.anchor = GridBagConstraints.LINE_START;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        p.add(main, g);
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        if (browse != null) {
            g.gridx = 2;
            p.add(browse, g);
        }
        if (hint != null) {
            g.gridx = 3;
            p.add(hint, g);
        }
    }

    private JButton fileButton(JTextField field, boolean dir, String ext) {
        JButton b = new JButton("Sfoglia...");
        b.addActionListener(e -> choose(field, dir, ext));
        return b;
    }

    private void choose(JTextField field, boolean dir, String ext) {
        JFileChooser fc = new JFileChooser();
        String current = field.getText().trim();
        if (!current.isEmpty()) {
            File f = new File(current);
            fc.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
        }
        if (dir) {
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else if (ext != null) {
            fc.setFileFilter(new FileNameExtensionFilter("*." + ext, ext));
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getPath());
        }
    }

    private void updateEnabled() {
        boolean sitic = onlySitic.isSelected() || both.isSelected();
        boolean ws = onlyWs.isSelected() || both.isSelected();
        siticField.setEnabled(sitic);
        siticBrowse.setEnabled(sitic);
        wsField.setEnabled(ws);
        wsBrowse.setEnabled(ws);
        outField.setEnabled(saveBox.isSelected());
        outBrowse.setEnabled(saveBox.isSelected());
    }

    // ------------------------------------------------------------------ esecuzione

    private void runProcessing() {
        final ProcessingRunner.Config c;
        try {
            c = readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dati mancanti o non validi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        savePrefs();
        runButton.setEnabled(false);
        openReport.setEnabled(false);
        status.setForeground(Color.DARK_GRAY);
        status.setText("Elaborazione in corso...");

        new SwingWorker<ProcessingRunner.Result, Void>() {
            @Override
            protected ProcessingRunner.Result doInBackground() throws Exception {
                return ProcessingRunner.run(c);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    ProcessingRunner.Result r = get();
                    status.setForeground(r.allOk ? new Color(0, 128, 0) : new Color(200, 100, 0));
                    status.setText(r.allOk ? "VERIFICA SUPERATA" : "VERIFICA CON DIFFERENZE (vedi console/report)");
                    lastReport = r.reportTxt;
                    openReport.setEnabled(lastReport != null);
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    StringWriter sw = new StringWriter();
                    cause.printStackTrace(new PrintWriter(sw));
                    System.err.println(sw);
                    status.setForeground(Color.RED);
                    status.setText("ERRORE");
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    JOptionPane.showMessageDialog(AppGui.this, msg, "Elaborazione interrotta", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private ProcessingRunner.Config readConfig() {
        ProcessingRunner.Config c = new ProcessingRunner.Config();
        c.rawDataFile   = requireFile(rawField, "Dati grezzi", false);
        c.regressorsDir = requireFile(regrField, "Cartella regressori", true);
        c.specFile      = requireFile(specField, "Specifiche JSON", false);
        boolean sitic = onlySitic.isSelected() || both.isSelected();
        boolean ws = onlyWs.isSelected() || both.isSelected();
        c.siticSaFile   = sitic ? requireFile(siticField, "SA di TS+", false) : null;
        c.compareWsFile = ws ? requireFile(wsField, "Workspace di confronto", false) : null;
        c.saveOutputs   = saveBox.isSelected();
        c.outputDir     = c.saveOutputs ? requireFile(outField, "Cartella di output", true) : null;
        c.absTol        = parseNumber(absTolField, "Tolleranza assoluta");
        c.relTolPercent = parseNumber(relTolField, "Tolleranza relativa (%)");
        return c;
    }

    private static String requireFile(JTextField f, String name, boolean dir) {
        String s = f.getText().trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Indicare: " + name);
        }
        File file = new File(s);
        if (dir ? !file.isDirectory() : !file.isFile()) {
            throw new IllegalArgumentException(name + ": " + (dir ? "cartella" : "file") + " non trovato\n" + s);
        }
        return s;
    }

    private static double parseNumber(JTextField f, String name) {
        try {
            double v = Double.parseDouble(f.getText().trim().replace(',', '.'));
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + ": numero non valido (" + f.getText() + ")");
        }
    }

    private void openFile(String path) {
        if (path == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Impossibile aprire " + path + "\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ console

    /** Mostra nell'area "Console" tutto quello che il programma scrive su System.out / System.err. */
    private void redirectConsole() {
        OutputStream os = new OutputStream() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public synchronized void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                final String s = new String(b, off, len, StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    console.append(s);
                    console.setCaretPosition(console.getDocument().getLength());
                });
            }
        };
        PrintStream ps;
        try {
            ps = new PrintStream(os, true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            ps = new PrintStream(os, true);
        }
        System.setOut(ps);
        System.setErr(ps);
    }

    // ------------------------------------------------------------------ preferenze (ultimi percorsi usati)

    private void loadPrefs() {
        rawField.setText(prefs.get("raw", ""));
        regrField.setText(prefs.get("regr", ""));
        specField.setText(prefs.get("spec", ""));
        siticField.setText(prefs.get("sitic", ""));
        wsField.setText(prefs.get("ws", ""));
        outField.setText(prefs.get("out", jarDirectory()));
        absTolField.setText(prefs.get("absTol", "0.01"));
        relTolField.setText(prefs.get("relTol", "0.001"));
        String mode = prefs.get("mode", "both");
        onlySitic.setSelected("sitic".equals(mode));
        onlyWs.setSelected("ws".equals(mode));
        both.setSelected("both".equals(mode));
        saveBox.setSelected(prefs.getBoolean("save", true));
    }

    private void savePrefs() {
        prefs.put("raw", rawField.getText().trim());
        prefs.put("regr", regrField.getText().trim());
        prefs.put("spec", specField.getText().trim());
        prefs.put("sitic", siticField.getText().trim());
        prefs.put("ws", wsField.getText().trim());
        prefs.put("out", outField.getText().trim());
        prefs.put("absTol", absTolField.getText().trim());
        prefs.put("relTol", relTolField.getText().trim());
        prefs.put("mode", onlySitic.isSelected() ? "sitic" : onlyWs.isSelected() ? "ws" : "both");
        prefs.putBoolean("save", saveBox.isSelected());
    }

    /** Cartella in cui si trova il JAR (default per l'output); se non disponibile, la cartella corrente. */
    private static String jarDirectory() {
        try {
            File f = new File(AppGui.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return (f.isFile() ? f.getParentFile() : f).getPath();
        } catch (Exception ex) {
            return System.getProperty("user.dir");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // look and feel di default
        }
        SwingUtilities.invokeLater(() -> new AppGui().setVisible(true));
    }
}
