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
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final JTextField meanTolField = new JTextField("0.001", 8);
    // un criterio non spuntato e' disattivato. Default: attiva solo la tolleranza relativa per periodo
    private final JCheckBox absTolOn  = new JCheckBox("assoluta", false);
    private final JCheckBox relTolOn  = new JCheckBox("relativa (%)", true);
    private final JCheckBox meanTolOn = new JCheckBox("differenza relativa media (%)", false);

    private final JRadioButton onlySitic = new JRadioButton("Solo TS+");
    private final JRadioButton onlyWs    = new JRadioButton("Solo workspace");
    private final JRadioButton both      = new JRadioButton("Entrambe", true);
    private final JRadioButton noCheck   = new JRadioButton("Nessuna (solo elaborazione Java)");
    private final JCheckBox saveBox      = new JCheckBox("Salva workspace e verifica", true);
    private final JCheckBox diagBox      = new JCheckBox("Report diagnostici (come in R)", true);

    private final JButton siticBrowse = new JButton("Sfoglia...");
    private final JButton wsBrowse    = new JButton("Sfoglia...");
    private final JButton outBrowse   = new JButton("Sfoglia...");
    private final JButton runButton   = new JButton("Esegui");
    private final JButton openReport  = new JButton("Apri verifica");
    private final JButton openDiag    = new JButton("Apri report diagnostici");
    private final JLabel  status      = new JLabel(" ");
    private final JTextArea console   = new JTextArea(22, 100);

    private String lastReport;
    private String lastDiag;

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
        for (JRadioButton r : new JRadioButton[]{onlySitic, onlyWs, both, noCheck}) {
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
        absTolOn.addActionListener(e -> updateEnabled());
        relTolOn.addActionListener(e -> updateEnabled());
        tol.add(absTolOn);
        tol.add(absTolField);
        tol.add(relTolOn);
        tol.add(relTolField);
        JLabel tolHint = new JLabel("periodo coerente se rientra in almeno una delle soglie attive");
        tolHint.setForeground(HINT);
        tol.add(tolHint);
        addRow(form, row++, new JLabel("Tolleranze per periodo:"), tol, null, null);

        // Tolleranza sulla serie
        JPanel meanTol = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meanTolOn.addActionListener(e -> updateEnabled());
        meanTol.add(meanTolOn);
        meanTol.add(meanTolField);
        JLabel meanHint = new JLabel("serie KO anche se la media supera la soglia");
        meanHint.setForeground(HINT);
        meanTol.add(meanHint);
        addRow(form, row++, new JLabel("Tolleranza sulla serie:"), meanTol, null, null);

        // Salvataggio
        saveBox.addActionListener(e -> updateEnabled());
        addRow(form, row++, new JLabel(""), saveBox, null, null);
        JPanel diagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel diagHint = new JLabel("   (processing_problems.out, processing_model_details.out)");
        diagHint.setForeground(HINT);
        diagRow.add(diagBox);
        diagRow.add(diagHint);
        addRow(form, row++, new JLabel(""), diagRow, null, null);
        outBrowse.addActionListener(e -> choose(outField, true, null));
        row = addFileRow(form, row, "Cartella di output:", outField, "(workspace, verifica e report)", outBrowse);

        // Pulsanti
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runButton.addActionListener(e -> runProcessing());
        openReport.setEnabled(false);
        openReport.addActionListener(e -> openFile(lastReport));
        openDiag.setEnabled(false);
        openDiag.addActionListener(e -> openFile(lastDiag));
        JButton clear = new JButton("Pulisci console");
        clear.addActionListener(e -> console.setText(""));
        buttons.add(runButton);
        buttons.add(openReport);
        buttons.add(openDiag);
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
        absTolOn.setEnabled(sitic || ws);      // tolleranze: servono solo alla verifica
        relTolOn.setEnabled(sitic || ws);
        absTolField.setEnabled((sitic || ws) && absTolOn.isSelected());
        relTolField.setEnabled((sitic || ws) && relTolOn.isSelected());
        meanTolOn.setEnabled(sitic || ws);
        meanTolField.setEnabled((sitic || ws) && meanTolOn.isSelected());
        outField.setEnabled(saveBox.isSelected());
        outBrowse.setEnabled(saveBox.isSelected());
        diagBox.setEnabled(saveBox.isSelected());   // i report vanno nella cartella di output
    }

    // ------------------------------------------------------------------ esecuzione

    private void runProcessing() {
        final Config c;
        try {
            c = readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dati mancanti o non validi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        savePrefs();
        runButton.setEnabled(false);
        openReport.setEnabled(false);
        openDiag.setEnabled(false);
        status.setForeground(Color.DARK_GRAY);
        status.setText("Elaborazione in corso...");

        new SwingWorker<Result, Void>() {
            @Override
            protected Result doInBackground() throws Exception {
                return elabora(c);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    Result r = get();
                    status.setForeground(r.allOk ? new Color(0, 128, 0) : new Color(200, 100, 0));
                    if (!r.verified) {
                        status.setText("ELABORAZIONE COMPLETATA (nessuna verifica)");
                    } else {
                        status.setText(r.allOk ? "VERIFICA SUPERATA" : "VERIFICA CON DIFFERENZE (vedi console/report)");
                    }
                    lastReport = r.reportTxt;
                    openReport.setEnabled(lastReport != null);
                    lastDiag = r.diagnosticReport;
                    openDiag.setEnabled(lastDiag != null);
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

    private Config readConfig() {
        Config c = new Config();
        c.rawDataFile   = requireFile(rawField, "Dati grezzi", false);
        c.regressorsDir = requireFile(regrField, "Cartella regressori", true);
        c.specFile      = requireFile(specField, "Specifiche JSON", false);
        boolean sitic = onlySitic.isSelected() || both.isSelected();
        boolean ws = onlyWs.isSelected() || both.isSelected();
        c.siticSaFile   = sitic ? requireFile(siticField, "SA di TS+", false) : null;
        c.compareWsFile = ws ? requireFile(wsField, "Workspace di confronto", false) : null;
        c.saveOutputs   = saveBox.isSelected();
        c.outputDir     = c.saveOutputs ? requireFile(outField, "Cartella di output", true) : null;
        c.diagnosticReports = c.saveOutputs && diagBox.isSelected();
        if (sitic || ws) {   // le tolleranze servono solo alla verifica
            if (!absTolOn.isSelected() && !relTolOn.isSelected() && !meanTolOn.isSelected()) {
                throw new IllegalArgumentException("Attivare almeno una tolleranza");
            }
            // criterio disattivato = NaN (SaConsistencyCheck usa solo l'altro)
            c.absTol        = absTolOn.isSelected() ? parseNumber(absTolField, "Tolleranza assoluta") : Double.NaN;
            c.relTolPercent = relTolOn.isSelected() ? parseNumber(relTolField, "Tolleranza relativa (%)") : Double.NaN;
            c.meanRelTolPercent = meanTolOn.isSelected()
                    ? parseNumber(meanTolField, "Tolleranza sulla differenza relativa media (%)") : Double.NaN;
        }
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
        absTolOn.setSelected(prefs.getBoolean("absTolOn", false));
        relTolOn.setSelected(prefs.getBoolean("relTolOn", true));
        meanTolOn.setSelected(prefs.getBoolean("meanTolOn", false));
        meanTolField.setText(prefs.get("meanTol", "0.001"));
        String mode = prefs.get("mode", "both");
        onlySitic.setSelected("sitic".equals(mode));
        onlyWs.setSelected("ws".equals(mode));
        both.setSelected("both".equals(mode));
        noCheck.setSelected("none".equals(mode));
        saveBox.setSelected(prefs.getBoolean("save", true));
        diagBox.setSelected(prefs.getBoolean("diag", true));
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
        prefs.putBoolean("absTolOn", absTolOn.isSelected());
        prefs.putBoolean("relTolOn", relTolOn.isSelected());
        prefs.putBoolean("meanTolOn", meanTolOn.isSelected());
        prefs.put("meanTol", meanTolField.getText().trim());
        prefs.put("mode", onlySitic.isSelected() ? "sitic" : onlyWs.isSelected() ? "ws"
                : noCheck.isSelected() ? "none" : "both");
        prefs.putBoolean("save", saveBox.isSelected());
        prefs.putBoolean("diag", diagBox.isSelected());
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

    // ------------------------------------------------------------------ elaborazione


    /** Parametri dell'elaborazione. */
    static class Config {
        public String rawDataFile;      // dati grezzi (CSV formato ISTAT, es. grezzi.csv)
        public String regressorsDir;    // cartella dei regressori esterni (es. regr)
        public String specFile;         // specifiche JSON (completo o ridotto)
        public String siticSaFile;      // SA di TS+ (es. sa_new.csv); null = confronto non eseguito
        public String compareWsFile;    // workspace di confronto (.xml); null = confronto non eseguito
        public boolean saveOutputs;     // salva workspace e verifica
        public String outputDir;        // cartella di output (se saveOutputs)
        public boolean diagnosticReports; // report diagnostici come in R (nella cartella di output)
        public double absTol = 0.01;    // tolleranza assoluta
        public double relTolPercent = 0.001;  // tolleranza relativa, in percentuale
        public double meanRelTolPercent = Double.NaN;  // tolleranza sulla differenza relativa media, in percentuale (NaN = disattivata)
    }

    /** Esito dell'elaborazione. */
    static class Result {
        public boolean allOk;
        public boolean verified;        // false se non e' stato chiesto nessun confronto
        public String reportTxt;        // null se i report non sono salvati
        public String diagnosticReport; // processing_problems.out; null se i report diagnostici non sono prodotti
        public String workspaceFile;    // null se il workspace non e' salvato
    }

    /**
     * Destagionalizzazione delle serie del JSON e verifica di coerenza: stessa elaborazione di
     * Main_with_checks, con i percorsi scelti nella finestra.
     */
    static Result elabora(Config c) throws Exception {
        // nessun confronto indicato: solo elaborazione Java (destagionalizzazione, workspace e report)
        boolean verify = c.siticSaFile != null || c.compareWsFile != null;
        Result result = new Result();
        result.verified = verify;

        System.out.println("Lettura dati grezzi: " + c.rawDataFile);
        DataReaderCSV_IstatFormat reader = new DataReaderCSV_IstatFormat();
        Map<String, TsData> tsDataMap = reader.readData(c.rawDataFile);

        System.out.println("Lettura specifiche: " + c.specFile);
        List<Map<String, Object>> jsonData = JsonReader.readJsonFile(c.specFile);

        ProcessingContext context = new ProcessingContext();
        Workspace ws = new Workspace(context);
        MultiProcessing mp = ws.newMultiProcessing("All");
        Map<String, TsData> javaSaMap = new LinkedHashMap<>();

        // Report diagnostici (come create_diagnostic_report1/2 in R): DiagnosticReports(outputOnFile, outputOnStrings)
        DiagnosticReports reports = new DiagnosticReports(c.diagnosticReports, false);
        if (c.diagnosticReports) {
            reports.setOutputDir(c.outputDir);
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
                        + ": presente nelle specifiche JSON ma non nei dati grezzi (" + c.rawDataFile + ")");
            }

            DestSpecificationsModel model = mapper.readValue(mapper.writeValueAsString(data), DestSpecificationsModel.class);
            TSmodelSetup tsModelSetup = new TSmodelSetup(model, context, c.regressorsDir + File.separator, raw);
            TramoSeatsSpecification spec = tsModelSetup.getTsSpec();

            CompositeResults rslt = TramoSeatsProcessingFactory.process(tsModelSetup.getTsData(), spec, context);
            TsData sa = rslt.getData("sa", TsData.class);
            TsData cal = rslt.getData("ycal", TsData.class);
            javaSaMap.put(seriesName, sa);
            reports.add(seriesName, rslt);

            System.out.println("SA series:");
            System.out.println(sa);
            System.out.println("Cal. adjusted series:");
            System.out.println(cal);
            System.out.println("_______________________________________________________________");

            mp.add(seriesName, tsModelSetup.getTsData(), spec);
        }

        String reportCsv = null;
        String reportTxt = null;
        if (c.saveOutputs) {
            File out = new File(c.outputDir);
            File wsDir = new File(out, "workspace_java");
            if (!wsDir.isDirectory() && !wsDir.mkdirs()) {
                throw new IOException("Impossibile creare la cartella di output: " + wsDir);
            }
            File wsFile = new File(wsDir, "workspace.xml");
            if (!ws.save(wsFile.getPath())) {
                throw new IOException("Salvataggio del workspace non riuscito: " + wsFile);
            }
            System.out.println("WS SAVED: " + wsFile);
            result.workspaceFile = wsFile.getPath();
            if (c.diagnosticReports) {
                reports.produce();
                File problems = new File(out, reports.getProblemsFileName());
                System.out.println("REPORT DIAGNOSTICI SALVATI: " + problems
                        + ", " + new File(out, reports.getDetailsFileName()));
                result.diagnosticReport = problems.getPath();
            }
            if (verify) {
                reportCsv = new File(out, "verifica_sa.csv").getPath();
                reportTxt = new File(out, "verifica_sa.txt").getPath();
            }
        }

        if (!verify) {
            result.allOk = true;
            System.out.println("ELABORAZIONE COMPLETATA (nessuna verifica richiesta)");
            return result;
        }
        SaConsistencyCheck check = new SaConsistencyCheck(c.absTol, c.relTolPercent / 100.0, c.meanRelTolPercent / 100.0);
        result.allOk = check.run(javaSaMap, c.siticSaFile, c.compareWsFile, reportCsv, reportTxt);
        result.reportTxt = reportTxt;
        System.out.println(result.allOk ? "VERIFICA SUPERATA" : "VERIFICA CON DIFFERENZE");
        return result;
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
