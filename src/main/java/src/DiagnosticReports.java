package src;

import ec.tstoolkit.Parameter;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.information.StatisticalTest;
import ec.tstoolkit.jdr.sa.TramoSeatsResults;
import ec.tstoolkit.maths.matrices.Matrix;
import ec.tstoolkit.timeseries.simplets.TsData;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Report diagnostici delle serie destagionalizzate, uguali a quelli prodotti in R da
 * create_diagnostic_report1 (problemi: Ljung-Box, normalita', coefficienti non significativi)
 * e create_diagnostic_report2 (dettaglio del modello di ogni serie) del pacchetto RJDProcessor.
 * Gli indicatori sono letti con lo stesso dizionario usato da RJDemetra (TramoSeatsResults di jdr),
 * quindi i valori sono gli stessi dei report R.
 *
 * Uso:
 *   DiagnosticReports reports = new DiagnosticReports();
 *   reports.setOutputOnFile(true);            // scrive i file in outputDir
 *   reports.setOutputOnStrings(false);        // restituisce i report in una mappa nome file -> testo
 *   ... per ogni serie: reports.add(nomeSerie, compositeResults);
 *   Map<String, String> testi = reports.produce();
 *
 * I coefficienti ARIMA sono elencati nell'ordine del modello (p,d,q)(bp,bd,bq): phi, theta, Bphi,
 * Btheta, come in TS+. JD+ li restituisce nell'ordine phi, Bphi, theta, Btheta, quindi valori ed
 * errori standard vengono riordinati (stessa correzione fatta in report.R).
 *
 * @author alessandro.piovani@istat.it
 */
public class DiagnosticReports {

    /** Nomi di default dei file, uguali a JD_JSON_file_processor in R. */
    public static final String DEFAULT_PROBLEMS_FILE = "processing_problems.out";
    public static final String DEFAULT_DETAILS_FILE  = "processing_model_details.out";

    private boolean outputOnFile    = false;  // scrive i report su file (TRUE in locale, FALSE in produzione)
    private boolean outputOnStrings = false;  // restituisce i report come stringhe nella mappa di produce()
    private String  outputDir       = ".";    // cartella dei file (se outputOnFile)
    private String  problemsFileName = DEFAULT_PROBLEMS_FILE;  // report 1
    private String  detailsFileName  = DEFAULT_DETAILS_FILE;   // report 2
    private boolean excludeNonSeas   = true;  // esclude dalle diagnostiche le serie senza parte stagionale

    // indicatori letti al momento di add(): i risultati completi delle serie non vengono trattenuti
    private final List<SeriesInfo> series = new ArrayList<>();

    public DiagnosticReports() {
    }

    public DiagnosticReports(boolean outputOnFile, boolean outputOnStrings) {
        this.outputOnFile = outputOnFile;
        this.outputOnStrings = outputOnStrings;
    }

    // ------------------------------------------------------------------ serie

    /** Aggiunge una serie, con i risultati di TramoSeatsProcessingFactory.process(...). */
    public void add(String seriesName, CompositeResults rslt) {
        if (!outputOnFile && !outputOnStrings) {
            return;
        }
        if (rslt == null) {
            throw new IllegalArgumentException("Serie " + seriesName + ": risultati nulli, report non producibile");
        }
        add(seriesName, new TramoSeatsResults(rslt));
    }

    /** Aggiunge una serie, con i risultati gia' nel formato di jdr (es. da un workspace). */
    public void add(String seriesName, TramoSeatsResults rslt) {
        if (!outputOnFile && !outputOnStrings) {
            return;   // report disattivati: nessun lavoro e nessuna memoria occupata
        }
        series.add(new SeriesInfo(seriesName, rslt));
    }

    // ------------------------------------------------------------------ produzione dei report

    /**
     * Produce i due report. Se outputOnFile li scrive in outputDir; se outputOnStrings restituisce
     * una mappa con due voci (nome del file -> testo del report), altrimenti una mappa vuota.
     */
    public Map<String, String> produce() throws IOException {
        if (!outputOnFile && !outputOnStrings) {
            return Collections.emptyMap();
        }
        Map<String, String> reports = new LinkedHashMap<>();
        reports.put(problemsFileName, buildProblemsReport());
        reports.put(detailsFileName, buildDetailsReport());

        if (outputOnFile) {
            File dir = new File(outputDir);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Impossibile creare la cartella dei report: " + dir);
            }
            for (Map.Entry<String, String> e : reports.entrySet()) {
                File f = new File(dir, e.getKey());
                // come R (cat in modalita' testo): fine riga del sistema operativo
                String text = e.getValue().replace("\n", System.lineSeparator());
                try (Writer w = new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
                    w.write(text);
                }
            }
        }
        return outputOnStrings ? reports : Collections.<String, String>emptyMap();
    }

    // ------------------------------------------------------------------ report 1 (create_diagnostic_report1)

    private static final String SEPARATOR            = "------------------------------------------------------------------------------------------\n";
    private static final String LB_SEPARATOR         = "-------------------------------------- LB PROBLEMS --------------------------------------\n";
    private static final String LB_SQUARED_SEPARATOR = "---------------------------------- LB SQUARED PROBLEMS ----------------------------------\n";
    private static final String NORMALITY_SEPARATOR  = "----------------------------------- NORMALITY PROBLEMS ----------------------------------\n";
    private static final String RESUME_SEPARATOR     = "------------------------------------ PROBLEMS RESUME ------------------------------------\n";

    /** Una riga del data frame report_df di R. */
    private static class Row {
        int frequency;
        String title;
        double bic, lbProb, lb2Prob, normProb, normTest;   // le tre Prob arrotondate a 3 decimali, come in R
        String notSignificant;
        boolean problematic;
    }

    public String buildProblemsReport() {
        List<Row> rows = new ArrayList<>();
        List<String> nonSeasonal = new ArrayList<>();

        for (SeriesInfo si : series) {
            if (!excludeNonSeas || !si.isNonSeasonal()) {
                List<String> nonSig = new ArrayList<>();
                // regressori: model_descr[abs(t) < 2]; con t NaN R restituisce NA
                for (int i = 0; i < si.regDescr.length; i++) {
                    double t = si.regT(i);
                    if (Double.isNaN(t)) {
                        nonSig.add("NA");
                    } else if (Math.abs(t) < 2) {
                        nonSig.add(si.regDescr[i]);
                    }
                }
                // ARIMA: esclusi i coefficienti fissati (errore standard 0, t-stat NaN)
                for (int i = 0; i < si.arimaParams.length; i++) {
                    double t = si.arimaT(i);
                    if (!si.arimaFixed(i) && Math.abs(t) < 2) {
                        nonSig.add(si.arimaNames.get(i));
                    }
                }

                Row r = new Row();
                r.frequency = (int) Math.round(si.freq);
                r.title = si.name;
                r.bic = si.bic;
                r.lbProb = rRound(si.lb[1], 3);
                r.lb2Prob = rRound(si.lb2[1], 3);
                r.normProb = rRound(si.norm[1], 3);
                r.normTest = si.norm[0];
                r.notSignificant = String.join(", ", nonSig);
                // R: any(c(lb[2], lb_squared[2], normality[2]) < 0.05) || has_non_significant (valori non arrotondati)
                r.problematic = si.lb[1] < 0.05 || si.lb2[1] < 0.05 || si.norm[1] < 0.05 || !nonSig.isEmpty();
                rows.add(r);

                if (si.isNonSeasonal()) {
                    nonSeasonal.add(si.name);
                }
            } else {
                nonSeasonal.add(si.name);
            }
        }

        StringBuilder out = new StringBuilder();
        String header = f("%-5s %-5s %-15s %-8s %-10s %-10s %-10s %-10s\n", "", "Freq", "TITLE", "BIC", "LB_Prob", "LB2_Prob", "Norm_Prob", "Norm_Test");

        out.append(header).append(SEPARATOR);
        for (int i = 0; i < rows.size(); i++) {
            appendRow(out, i + 1, rows.get(i));
        }

        // sezioni: i problemi sono selezionati sui valori arrotondati, come in R (report_df$LB_Prob < 0.05)
        out.append("\n\n").append(LB_SEPARATOR).append(header).append(SEPARATOR);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).lbProb < 0.05) appendRow(out, i + 1, rows.get(i));
        }
        out.append("\n\n").append(NORMALITY_SEPARATOR).append(header).append(SEPARATOR);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).normProb < 0.05) appendRow(out, i + 1, rows.get(i));
        }
        out.append("\n\n").append(LB_SQUARED_SEPARATOR).append(header).append(SEPARATOR);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).lb2Prob < 0.05) appendRow(out, i + 1, rows.get(i));
        }
        out.append("\n\n");

        // PROBLEMS RESUME: solo le serie problematiche, numerate in sequenza
        out.append(RESUME_SEPARATOR);
        out.append(f("%-5s %-5s %-15s %-8s %-10s %-10s %-10s %-10s %-25s\n", "", "Freq", "TITLE", "BIC", "LB_Prob", "LB2_Prob", "Norm_Prob", "Norm_Test", "Not_significant_coeff"));
        out.append("--------------------------------------------------------------------------------------------------------------\n");
        int k = 0;
        for (Row r : rows) {
            if (!r.problematic) continue;
            k++;
            out.append(f("%-5d %-5d %-15s %-8.2f %-10s %-10s %-10s %-10.2f %-25s\n",
                    k, r.frequency, r.title, r.bic,
                    flag(r.lbProb), flag(r.lb2Prob), flag(r.normProb),
                    r.normTest, r.notSignificant));
        }
        out.append("\n");
        out.append("\n\n");

        out.append("Time series without seasonality: \n\n");
        out.append(nonSeasonal.isEmpty() ? "NONE" : String.join(", ", nonSeasonal));
        out.append("\n\n");
        return out.toString();
    }

    private static void appendRow(StringBuilder out, int idx, Row r) {
        out.append(f("%-5d %-5d %-15s %-8.2f %-10.3f %-10.3f %-10.3f %-10.2f\n",
                idx, r.frequency, r.title, r.bic, r.lbProb, r.lb2Prob, r.normProb, r.normTest));
    }

    /** ifelse(p < 0.05, paste0(p, "(*)"), p) */
    private static String flag(double p) {
        return p < 0.05 ? rChar(p) + "(*)" : rChar(p);
    }

    // ------------------------------------------------------------------ report 2 (create_diagnostic_report2)

    public String buildDetailsReport() {
        StringBuilder out = new StringBuilder();
        List<String> nonSeasonal = new ArrayList<>();

        for (SeriesInfo si : series) {
            if (!excludeNonSeas || !si.isNonSeasonal()) {
                out.append("\n\n");
                out.append(f("TITLE: %s\n", si.name));
                out.append("\nlog: ").append(si.log).append(" \n");   // cat("\nlog:", log, "\n")

                out.append(f("%-15s %-15s %-15s %-15s %-15s\n", "BIC", "LB_Prob", "LB2_Prob", "Norm_Prob", "Norm_Test"));
                out.append("-----------------------------------------------------------------------------------------\n");
                out.append(f("%-15.2f %-15.3f %-15.3f %-15.3f %-15.2f\n", si.bic, si.lb[1], si.lb2[1], si.norm[1], si.norm[0]));

                out.append(f("\n%-25s %-15s %-15s %-15s\n", " Name   ", "Coefficients", "T-Stats", "Not significant"));
                out.append("-------------------------------------------------------------------------------\n");

                for (int i = 0; i < si.regDescr.length; i++) {
                    double t = si.regT(i);
                    String ns = Double.isNaN(t) ? "NA" : (Math.abs(t) < 2 ? "X" : "");
                    out.append(f("%-25s %-15.3f %-15.3f %-15s\n", si.regDescr[i], si.regCoef[i], t, ns));
                }
                for (int i = 0; i < si.arimaParams.length; i++) {
                    if (si.arimaFixed(i)) {   // coefficiente fissato: nessun test
                        out.append(f("%-25s %-15.3f %-15s %-15s\n", si.arimaNames.get(i), si.arimaParams[i], "fixed", ""));
                    } else {
                        double t = si.arimaT(i);
                        out.append(f("%-25s %-15.3f %-15f %-15s\n", si.arimaNames.get(i), si.arimaParams[i], t, Math.abs(t) < 2 ? "X" : ""));
                    }
                }
                if (si.isNonSeasonal()) {
                    nonSeasonal.add(si.name);
                }
            } else {
                nonSeasonal.add(si.name);
            }
        }
        out.append("\n\nNon-seasonal time series:\n\n");
        out.append(String.join(", ", nonSeasonal));
        return out.toString();
    }

    // ------------------------------------------------------------------ lettura degli indicatori

    /** Gli indicatori di una serie, letti con gli stessi nomi usati in R da get_indicators. */
    private static class SeriesInfo {
        final String name;
        final double freq;          // periodi per anno (12 mensile, 4 trimestrale)
        final int p, d, q, bp, bd, bq;
        final String log;           // "true"/"false", come lo restituisce RJDemetra
        final double bic;
        final double[] lb, lb2, norm;   // {valore, p-value}
        final String[] regDescr;
        final double[] regCoef, regSe;
        final double[] arimaParams, arimaSe;
        final List<String> arimaNames = new ArrayList<>();

        SeriesInfo(String name, TramoSeatsResults r) {
            this.name = name;
            TsData y = r.getData("y", TsData.class);
            // In R: freq <- 12 / frequency(get_indicators(x, "y")). get_indicators restituisce una lista,
            // per cui frequency() vale sempre 1 e la colonna Freq risultava sempre 12, anche per le serie
            // trimestrali. Qui si riporta la frequenza vera: per le mensili il valore e' identico (12).
            freq = y.getFrequency().intValue();
            p  = intOf(r, "preprocessing.arima.p");
            d  = intOf(r, "preprocessing.arima.d");
            q  = intOf(r, "preprocessing.arima.q");
            bp = intOf(r, "preprocessing.arima.bp");
            bd = intOf(r, "preprocessing.arima.bd");
            bq = intOf(r, "preprocessing.arima.bq");
            Object l = r.getData("preprocessing.model.log", Object.class);
            log = l == null ? "NULL" : l.toString();
            Number b = r.getData("preprocessing.likelihood.bicc", Number.class);
            bic = b == null ? Double.NaN : b.doubleValue();
            lb   = test(r, "preprocessing.residuals.lb");
            lb2  = test(r, "preprocessing.residuals.lb2");
            norm = test(r, "preprocessing.residuals.dh");

            String[] descr = r.getData("preprocessing.model.description", String[].class);
            Parameter[] coefs = r.getData("preprocessing.model.coefficients", Parameter[].class);
            regDescr = descr == null ? new String[0] : descr;
            int nc = coefs == null ? 0 : coefs.length;
            regCoef = new double[nc];
            regSe = new double[nc];
            for (int i = 0; i < nc; i++) {
                regCoef[i] = coefs[i].getValue();
                regSe[i] = coefs[i].getStde();
            }

            double[] ap = r.getData("preprocessing.arima.parameters", double[].class);
            double[] jdParams = ap == null ? new double[0] : ap;
            Matrix pcov = r.getData("preprocessing.model.pcovar", Matrix.class);
            double[] jdSe = new double[jdParams.length];
            for (int i = 0; i < jdParams.length; i++) {
                jdSe[i] = (pcov != null && i < pcov.getRowsCount()) ? Math.sqrt(pcov.get(i, i)) : Double.NaN;
            }
            // JD+ restituisce i parametri nell'ordine phi, Bphi, theta, Btheta: si riordinano (valori ed
            // errori standard) come nel modello (p,d,q)(bp,bd,bq), cioe' phi, theta, Bphi, Btheta
            // (come in TS+ e nei report R)
            int[] ord = new int[p + q + bp + bq];
            int k = 0;
            for (int i = 0; i < p; i++)  ord[k++] = i;
            for (int i = 0; i < q; i++)  ord[k++] = p + bp + i;
            for (int i = 0; i < bp; i++) ord[k++] = p + i;
            for (int i = 0; i < bq; i++) ord[k++] = p + bp + q + i;
            if (ord.length == jdParams.length) {
                arimaParams = new double[ord.length];
                arimaSe = new double[ord.length];
                for (int i = 0; i < ord.length; i++) {
                    arimaParams[i] = jdParams[ord[i]];
                    arimaSe[i] = jdSe[ord[i]];
                }
            } else {   // caso non previsto: ordine di JD+
                arimaParams = jdParams;
                arimaSe = jdSe;
            }
            for (int i = 1; i <= p; i++)  arimaNames.add("phi" + i);
            for (int i = 1; i <= q; i++)  arimaNames.add("theta" + i);
            for (int i = 1; i <= bp; i++) arimaNames.add("Bphi" + i);
            for (int i = 1; i <= bq; i++) arimaNames.add("Btheta" + i);
            while (arimaNames.size() < arimaParams.length) arimaNames.add("NA");
        }

        boolean isNonSeasonal() {
            return bp == 0 && bd == 0 && bq == 0;
        }

        double regT(int i) {
            return i < regCoef.length ? regCoef[i] / regSe[i] : Double.NaN;
        }

        double arimaT(int i) {
            return arimaParams[i] / arimaSe[i];
        }

        boolean arimaFixed(int i) {
            double t = arimaT(i);
            return Double.isNaN(t) || arimaSe[i] == 0;
        }

        private static int intOf(TramoSeatsResults r, String id) {
            Number n = r.getData(id, Number.class);
            return n == null ? 0 : n.intValue();
        }

        private static double[] test(TramoSeatsResults r, String id) {
            StatisticalTest t = r.getData(id, StatisticalTest.class);
            return t == null ? new double[]{Double.NaN, Double.NaN} : new double[]{t.value, t.pvalue};
        }
    }

    // ------------------------------------------------------------------ formattazione come in R

    /** sprintf di R: punto decimale, Inf/-Inf/NaN come in R. */
    private static String f(String format, Object... args) {
        Object[] a = args.clone();
        boolean special = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] instanceof Double) {
                double v = (Double) a[i];
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    a[i] = Double.isNaN(v) ? "NaN" : (v > 0 ? "Inf" : "-Inf");
                    special = true;
                }
            }
        }
        // i formati numerici applicati a NaN/Inf diventano stringhe, con la stessa larghezza
        String fmt = special ? adaptFormat(format, a) : format;
        return String.format(Locale.ROOT, fmt, a);
    }

    /** Sostituisce %-N.Mf con %-Ns per gli argomenti NaN/Inf (R li stampa come testo allineato). */
    private static String adaptFormat(String format, Object[] a) {
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("%(-?\\d*)(\\.\\d+)?([sdf%])").matcher(format);
        int last = 0;
        while (m.find()) {
            sb.append(format, last, m.start());
            String conv = m.group(3);
            if (conv.equals("%")) {
                sb.append(m.group());
            } else {
                if (conv.equals("f") && a[argIdx] instanceof String) {
                    sb.append('%').append(m.group(1)).append('s');
                } else {
                    sb.append(m.group());
                }
                argIdx++;
            }
            last = m.end();
        }
        sb.append(format.substring(last));
        return sb.toString();
    }

    /** round(x, digits) di R (arrotondamento al pari sul valore binario esatto). */
    static double rRound(double x, int digits) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return x;
        return new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** as.character(x) di R per un numero (15 cifre significative, senza zeri finali). */
    static String rChar(double x) {
        if (Double.isNaN(x)) return "NaN";
        if (Double.isInfinite(x)) return x > 0 ? "Inf" : "-Inf";
        if (x == 0) return "0";
        return new BigDecimal(x).round(new MathContext(15)).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ impostazioni

    public boolean isOutputOnFile() { return outputOnFile; }
    public void setOutputOnFile(boolean outputOnFile) { this.outputOnFile = outputOnFile; }
    public boolean isOutputOnStrings() { return outputOnStrings; }
    public void setOutputOnStrings(boolean outputOnStrings) { this.outputOnStrings = outputOnStrings; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getProblemsFileName() { return problemsFileName; }
    public void setProblemsFileName(String problemsFileName) { this.problemsFileName = problemsFileName; }
    public String getDetailsFileName() { return detailsFileName; }
    public void setDetailsFileName(String detailsFileName) { this.detailsFileName = detailsFileName; }
    public boolean isExcludeNonSeas() { return excludeNonSeas; }
    public void setExcludeNonSeas(boolean excludeNonSeas) { this.excludeNonSeas = excludeNonSeas; }
}
