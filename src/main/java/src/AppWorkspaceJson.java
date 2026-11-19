package src;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.algorithm.ProcessingContext;
import ec.tstoolkit.jdr.ws.MultiProcessing;
import ec.tstoolkit.jdr.ws.Workspace;
import ec.tstoolkit.timeseries.simplets.TsData;
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Applicazione da usare al posto di R (a migrazione da TS+ ultimata), con due schede:
 *
 *  - "Workspace -> JSON": estrae le specifiche JSON da un workspace di JDemetra+, come
 *    JD_JSON_from_materialized_workspace (JSON ridotto) + from_reduced_to_full_JD_JSON_file
 *    (JSON completo) in R. Il lavoro lo fa WorkspaceToJson.
 *  - "JSON -> Workspace": destagiona le serie con le specifiche JSON (completo o ridotto), i dati
 *    grezzi e i regressori, e salva il workspace e i report diagnostici, come
 *    JD_JSON_file_processor in R. E' la stessa elaborazione di AppGui, senza la verifica.
 *
 * Output della scheda "JSON -> Workspace" (nella cartella di output):
 *   workspace_java/workspace.xml                            workspace generato
 *   processing_problems.out, processing_model_details.out   report diagnostici (se richiesti)
 *
 * Avvio: doppio clic sul JAR, oppure  java -jar JD_JSON_workspace_json.jar
 *
 * @author alessandro.piovani@istat.it
 */
public class AppWorkspaceJson extends JFrame {

    private static final Color HINT = new Color(130, 130, 130);
    private static final Color OK = new Color(0, 128, 0);
    // nodo separato da AppGui: le due app ricordano percorsi diversi
    private final Preferences prefs = Preferences.userRoot().node("JD_JSON_processor/AppWorkspaceJson");

    // ---- scheda Workspace -> JSON
    private final JTextField wsField      = new JTextField(40);
    private final JTextField wsRegrField  = new JTextField(40);
    private final JTextField jsonOutField = new JTextField(40);
    private final JTextField redName      = new JTextField(WorkspaceToJson.DEFAULT_REDUCED_FILE, 22);
    private final JTextField fullName     = new JTextField(WorkspaceToJson.DEFAULT_FULL_FILE, 22);
    private final JCheckBox  redBox       = new JCheckBox("JSON ridotto", true);
    private final JCheckBox  fullBox      = new JCheckBox("JSON completo", true);
    private final JButton    toJsonButton = new JButton("Estrai JSON");
    private final JButton    openJsonDir  = new JButton("Apri cartella di output");
    private final JLabel     jsonStatus   = new JLabel(" ");

    // ---- scheda JSON -> Workspace
    private final JTextField rawField     = new JTextField(40);
    private final JTextField regrField    = new JTextField(40);
    private final JTextField specField    = new JTextField(40);
    private final JTextField wsOutField   = new JTextField(40);
    private final JCheckBox  diagBox      = new JCheckBox("Report diagnostici (come in R)", true);
    private final JButton    toWsButton   = new JButton("Crea workspace");
    private final JButton    openDiag     = new JButton("Apri report diagnostici");
    private final JButton    openWsDir    = new JButton("Apri cartella di output");
    private final JLabel     wsStatus     = new JLabel(" ");

    private final JTextArea console = new JTextArea(16, 100);
    private String lastDiag;

    public AppWorkspaceJson() {
        super("JDemetra+ - specifiche JSON e workspace");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Workspace → JSON", buildToJsonTab());
        tabs.addTab("JSON → Workspace", buildToWsTab());

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(BorderFactory.createTitledBorder("Console"));
        JButton clear = new JButton("Pulisci console");
        clear.addActionListener(e -> console.setText(""));
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.add(scroll, BorderLayout.CENTER);
        JPanel clearRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        clearRow.add(clear);
        consolePanel.add(clearRow, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, consolePanel);
        split.setResizeWeight(0);
        getContentPane().add(split, BorderLayout.CENTER);

        loadPrefs();
        tabs.setSelectedIndex(prefs.getInt("tab", 0));
        tabs.addChangeListener(e -> prefs.putInt("tab", tabs.getSelectedIndex()));
        redirectConsole();
        pack();
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ schede

    private JPanel buildToJsonTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        int row = 0;
        row = addFileRow(form, row, "Workspace:", wsField, "(file .xml del workspace)", fileButton(wsField, false, "xml"));
        row = addFileRow(form, row, "Cartella regressori:", wsRegrField, "(facoltativa: controlla solo che i file esistano)", fileButton(wsRegrField, true, null));
        row = addFileRow(form, row, "Cartella di output:", jsonOutField, "(dove salvare i JSON)", fileButton(jsonOutField, true, null));

        JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        redBox.addActionListener(e -> redName.setEnabled(redBox.isSelected()));
        fullBox.addActionListener(e -> fullName.setEnabled(fullBox.isSelected()));
        names.add(redBox);
        names.add(redName);
        names.add(fullBox);
        names.add(fullName);
        addRow(form, row++, new JLabel("File da produrre:"), names, null, null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toJsonButton.addActionListener(e -> runToJson());
        openJsonDir.setEnabled(false);
        openJsonDir.addActionListener(e -> openFile(jsonOutField.getText().trim()));
        jsonStatus.setFont(jsonStatus.getFont().deriveFont(Font.BOLD));
        buttons.add(toJsonButton);
        buttons.add(openJsonDir);
        buttons.add(jsonStatus);
        addRow(form, row++, new JLabel(""), buttons, null, null);
        return wrap(form);
    }

    private JPanel buildToWsTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        int row = 0;
        row = addFileRow(form, row, "Dati grezzi:", rawField, "(es. grezzi.csv)", fileButton(rawField, false, "csv"));
        row = addFileRow(form, row, "Cartella regressori:", regrField, "(es. cartella regr)", fileButton(regrField, true, null));
        row = addFileRow(form, row, "Specifiche JSON:", specField, "(completo o ridotto, es. specifications_new.txt)", fileButton(specField, false, null));

        JPanel diagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel diagHint = new JLabel("   (processing_problems.out, processing_model_details.out)");
        diagHint.setForeground(HINT);
        diagRow.add(diagBox);
        diagRow.add(diagHint);
        addRow(form, row++, new JLabel(""), diagRow, null, null);
        row = addFileRow(form, row, "Cartella di output:", wsOutField, "(workspace e report)", fileButton(wsOutField, true, null));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toWsButton.addActionListener(e -> runToWs());
        openDiag.setEnabled(false);
        openDiag.addActionListener(e -> openFile(lastDiag));
        openWsDir.setEnabled(false);
        openWsDir.addActionListener(e -> openFile(wsOutField.getText().trim()));
        wsStatus.setFont(wsStatus.getFont().deriveFont(Font.BOLD));
        buttons.add(toWsButton);
        buttons.add(openDiag);
        buttons.add(openWsDir);
        buttons.add(wsStatus);
        addRow(form, row++, new JLabel(""), buttons, null, null);
        return wrap(form);
    }

    /** Tiene il modulo in alto nella scheda. */
    private static JPanel wrap(JPanel form) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(form, BorderLayout.NORTH);
        return p;
    }

    // ------------------------------------------------------------------ layout (come AppGui)

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

    // ------------------------------------------------------------------ Workspace -> JSON

    private void runToJson() {
        final String ws, regr, outDir;
        try {
            ws = requireFile(wsField, "Workspace", false);
            regr = wsRegrField.getText().trim().isEmpty() ? null : requireFile(wsRegrField, "Cartella regressori", true);
            outDir = requireFile(jsonOutField, "Cartella di output", true);
            if (!redBox.isSelected() && !fullBox.isSelected()) {
                throw new IllegalArgumentException("Scegliere almeno un JSON da produrre");
            }
            if (redBox.isSelected() && redName.getText().trim().isEmpty()
                    || fullBox.isSelected() && fullName.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Indicare il nome del file JSON");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dati mancanti o non validi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        savePrefs();
        final boolean red = redBox.isSelected(), full = fullBox.isSelected();
        final String redFile = redName.getText().trim(), fullFile = fullName.getText().trim();
        run(toJsonButton, openJsonDir, jsonStatus, () -> {
            System.out.println("Lettura del workspace: " + ws);
            WorkspaceToJson.Result r = new WorkspaceToJson(regr).extract(ws);
            for (String w : r.warnings) {
                System.out.println("ATTENZIONE: " + w);
            }
            System.out.println(r.seriesNames.size() + " serie: " + String.join(", ", r.seriesNames));
            if (red) {
                File f = new File(outDir, redFile);
                WorkspaceToJson.writeFile(r.reduced, f.getPath());
                System.out.println("JSON RIDOTTO SALVATO: " + f);
            }
            if (full) {
                File f = new File(outDir, fullFile);
                WorkspaceToJson.writeFile(r.full, f.getPath());
                System.out.println("JSON COMPLETO SALVATO: " + f);
            }
            System.out.println("_______________________________________________________________");
            return r.warnings.isEmpty() ? "JSON PRODOTTI" : "JSON PRODOTTI (con avvisi, vedi console)";
        });
    }

    // ------------------------------------------------------------------ JSON -> Workspace

    private void runToWs() {
        final String raw, regr, spec, outDir;
        try {
            raw = requireFile(rawField, "Dati grezzi", false);
            regr = requireFile(regrField, "Cartella regressori", true);
            spec = requireFile(specField, "Specifiche JSON", false);
            outDir = requireFile(wsOutField, "Cartella di output", true);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dati mancanti o non validi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        savePrefs();
        final boolean diag = diagBox.isSelected();
        openDiag.setEnabled(false);
        lastDiag = null;
        run(toWsButton, openWsDir, wsStatus, () -> {
            String problems = createWorkspace(raw, regr, spec, outDir, diag);
            if (problems != null) {
                lastDiag = problems;
                SwingUtilities.invokeLater(() -> openDiag.setEnabled(true));
            }
            return "WORKSPACE CREATO";
        });
    }

    /**
     * Destagionalizzazione delle serie del JSON e salvataggio del workspace (stessa elaborazione di
     * AppGui senza la verifica). Restituisce il percorso di processing_problems.out, oppure null se
     * i report diagnostici non sono richiesti.
     */
    static String createWorkspace(String rawDataFile, String regressorsDir, String specFile,
            String outputDir, boolean diagnosticReports) throws Exception {
        System.out.println("Lettura dati grezzi: " + rawDataFile);
        DataReaderCSV_IstatFormat reader = new DataReaderCSV_IstatFormat();
        Map<String, TsData> tsDataMap = reader.readData(rawDataFile);

        System.out.println("Lettura specifiche: " + specFile);
        List<Map<String, Object>> jsonData = JsonReader.readJsonFile(specFile);

        ProcessingContext context = new ProcessingContext();
        Workspace ws = new Workspace(context);
        MultiProcessing mp = ws.newMultiProcessing("All");

        DiagnosticReports reports = new DiagnosticReports(diagnosticReports, false);
        if (diagnosticReports) {
            reports.setOutputDir(outputDir);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        for (Map<String, Object> data : jsonData) {
            String seriesName = data.get("series_name").toString();
            System.out.println("Series Name: " + seriesName);

            TsData raw = tsDataMap.get(seriesName);
            if (raw == null) {
                throw new IllegalArgumentException("Serie " + seriesName
                        + ": presente nelle specifiche JSON ma non nei dati grezzi (" + rawDataFile + ")");
            }

            DestSpecificationsModel model = mapper.readValue(mapper.writeValueAsString(data), DestSpecificationsModel.class);
            TSmodelSetup tsModelSetup = new TSmodelSetup(model, context, regressorsDir + File.separator, raw);
            TramoSeatsSpecification spec = tsModelSetup.getTsSpec();

            CompositeResults rslt = TramoSeatsProcessingFactory.process(tsModelSetup.getTsData(), spec, context);
            reports.add(seriesName, rslt);
            mp.add(seriesName, tsModelSetup.getTsData(), spec);
        }

        File out = new File(outputDir);
        File wsDir = new File(out, "workspace_java");
        if (!wsDir.isDirectory() && !wsDir.mkdirs()) {
            throw new IOException("Impossibile creare la cartella di output: " + wsDir);
        }
        File wsFile = new File(wsDir, "workspace.xml");
        if (!ws.save(wsFile.getPath())) {
            throw new IOException("Salvataggio del workspace non riuscito: " + wsFile);
        }
        System.out.println(jsonData.size() + " serie. WS SAVED: " + wsFile);

        String problems = null;
        if (diagnosticReports) {
            reports.produce();
            problems = new File(out, reports.getProblemsFileName()).getPath();
            System.out.println("REPORT DIAGNOSTICI SALVATI: " + problems
                    + ", " + new File(out, reports.getDetailsFileName()));
        }
        System.out.println("_______________________________________________________________");
        return problems;
    }

    // ------------------------------------------------------------------ esecuzione in background

    private void run(JButton button, JButton openDir, JLabel status, Callable<String> task) {
        button.setEnabled(false);
        status.setForeground(Color.DARK_GRAY);
        status.setText("Elaborazione in corso...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                openDir.setEnabled(true);
                try {
                    String msg = get();
                    status.setForeground(msg.contains("avvisi") ? new Color(200, 100, 0) : OK);
                    status.setText(msg);
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    StringWriter sw = new StringWriter();
                    cause.printStackTrace(new PrintWriter(sw));
                    System.err.println(sw);
                    status.setForeground(Color.RED);
                    status.setText("ERRORE");
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    JOptionPane.showMessageDialog(AppWorkspaceJson.this, msg, "Elaborazione interrotta", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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

    private void openFile(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Impossibile aprire " + path + "\n" + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ console

    private void redirectConsole() {
        OutputStream os = new OutputStream() {
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
        String jar = jarDirectory();
        wsField.setText(prefs.get("ws", ""));
        wsRegrField.setText(prefs.get("wsRegr", ""));
        jsonOutField.setText(prefs.get("jsonOut", jar));
        redName.setText(prefs.get("redName", WorkspaceToJson.DEFAULT_REDUCED_FILE));
        fullName.setText(prefs.get("fullName", WorkspaceToJson.DEFAULT_FULL_FILE));
        redBox.setSelected(prefs.getBoolean("red", true));
        fullBox.setSelected(prefs.getBoolean("full", true));
        redName.setEnabled(redBox.isSelected());
        fullName.setEnabled(fullBox.isSelected());
        rawField.setText(prefs.get("raw", ""));
        regrField.setText(prefs.get("regr", ""));
        specField.setText(prefs.get("spec", ""));
        wsOutField.setText(prefs.get("wsOut", jar));
        diagBox.setSelected(prefs.getBoolean("diag", true));
    }

    private void savePrefs() {
        prefs.put("ws", wsField.getText().trim());
        prefs.put("wsRegr", wsRegrField.getText().trim());
        prefs.put("jsonOut", jsonOutField.getText().trim());
        prefs.put("redName", redName.getText().trim());
        prefs.put("fullName", fullName.getText().trim());
        prefs.putBoolean("red", redBox.isSelected());
        prefs.putBoolean("full", fullBox.isSelected());
        prefs.put("raw", rawField.getText().trim());
        prefs.put("regr", regrField.getText().trim());
        prefs.put("spec", specField.getText().trim());
        prefs.put("wsOut", wsOutField.getText().trim());
        prefs.putBoolean("diag", diagBox.isSelected());
    }

    private static String jarDirectory() {
        try {
            File f = new File(AppWorkspaceJson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return (f.isFile() ? f.getParentFile() : f).getPath();
        } catch (Exception ex) {
            return System.getProperty("user.dir");
        }
    }

    public static void main(String[] args) {
        try {
            // grafica del sistema operativo (su Windows: pulsanti e campi di Windows), come AppGui
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // look and feel di default
        }
        SwingUtilities.invokeLater(() -> new AppWorkspaceJson().setVisible(true));
    }
}
