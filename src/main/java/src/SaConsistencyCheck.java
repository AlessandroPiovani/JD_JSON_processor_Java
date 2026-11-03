package src;

import ec.tstoolkit.jdr.ws.MultiProcessing;
import ec.tstoolkit.jdr.ws.SaItem;
import ec.tstoolkit.jdr.ws.Workspace;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import jd2.algorithm.IProcResults;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifica di coerenza delle serie destagionalizzate (SA) tra tre fonti:
 *  - JAVA : serie calcolate da Main (TramoSeatsProcessingFactory)
 *  - SITIC: serie di TS+ lette da sa_new.csv (date nel formato AAAAqPP, es. 2020q03)
 *  - WS   : serie del workspace locale di JDemetra+ (letto con Workspace.open)
 *
 * Le serie vengono allineate per periodo (anno + numero del periodo), quindi
 * il formato "q" usato anche per i mesi non crea ambiguita': la frequenza
 * e' quella della serie JAVA/WS con cui si confronta.
 *
 * @author alessandro.piovani@istat.it
 */
public class SaConsistencyCheck {

    /** Differenza assoluta massima ammessa (es. arrotondamenti nel CSV di TS+). */
    private final double absTol;
    /** Differenza relativa massima ammessa (|a-b| / |b|). */
    private final double relTol;

    public SaConsistencyCheck(double absTol, double relTol) {
        this.absTol = absTol;
        this.relTol = relTol;
    }

    // ---------------------------------------------------------------------
    // Esecuzione completa
    // ---------------------------------------------------------------------

    /** Righe del report testuale (stesse righe stampate a console). */
    private final List<String> txt = new ArrayList<>();

    private void out(String line) {
        System.out.println(line);
        txt.add(line);
    }

    /**
     * Esegue tutti i confronti e scrive due report: uno CSV (una riga per
     * serie e confronto, da aprire in Excel) e uno TXT (leggibile, con le
     * stesse informazioni stampate a console).
     *
     * @param javaSa    serie SA calcolate in Main (nome serie -> TsData)
     * @param siticCsv  percorso di sa_new.csv
     * @param wsFile    percorso del file .xml del workspace locale di JDemetra+
     * @param reportCsv percorso del report CSV (la cartella deve esistere)
     * @param reportTxt percorso del report TXT (la cartella deve esistere)
     * @return true se tutte le serie sono coerenti tra le tre fonti
     */
    public boolean run(Map<String, TsData> javaSa, String siticCsv, String wsFile,
            String reportCsv, String reportTxt) throws IOException {

        txt.clear();
        out("=============== VERIFICA SERIE DESTAGIONALIZZATE ===============");
        out("Data esecuzione: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date()));
        out(String.format(Locale.ROOT, "Tolleranze: assoluta = %g, relativa = %g", absTol, relTol));
        out("Un periodo e' coerente se la differenza e' entro la soglia assoluta OPPURE entro quella relativa.");
        out("");

        Map<String, Map<String, Double>> sitic = readSiticCsv(siticCsv);
        out("Serie lette da TS+ (" + siticCsv + "): " + sitic.size());

        Map<String, TsData> ws = readWorkspaceSa(wsFile);
        out("Serie lette dal workspace (" + wsFile + "): " + ws.size());
        out("Serie calcolate in Java: " + javaSa.size());
        out("");

        List<Diff> diffs = new ArrayList<>();
        List<String> koNames = new ArrayList<>();
        int nOk = 0;

        out("---------------------------- DETTAGLIO ----------------------------");
        for (Map.Entry<String, TsData> e : javaSa.entrySet()) {
            String name = e.getKey();
            Map<String, Double> j = toMap(e.getValue());
            Map<String, Double> s = lookup(sitic, name);
            TsData wsTs = lookup(ws, name);
            Map<String, Double> w = wsTs == null ? null : toMap(wsTs);

            List<Diff> seriesDiffs = new ArrayList<>();
            seriesDiffs.add(compare(name, "JAVA vs SITIC", j, s));
            seriesDiffs.add(compare(name, "JAVA vs WS", j, w));
            seriesDiffs.add(compare(name, "WS vs SITIC", w, s));

            boolean seriesOk = true;
            for (Diff d : seriesDiffs) {
                seriesOk &= d.ok;
            }
            if (seriesOk) {
                nOk++;
            } else {
                koNames.add(name);
            }
            diffs.addAll(seriesDiffs);

            out((seriesOk ? "[OK] " : "[KO] ") + name);
            for (Diff d : seriesDiffs) {
                if (!d.ok) {
                    out("       " + d.describe());
                }
            }
        }

        // Serie presenti in TS+ o nel workspace ma non calcolate in Java
        List<String> extra = new ArrayList<>();
        for (String name : sitic.keySet()) {
            if (lookup(javaSa, name) == null) {
                extra.add(name + " (TS+)");
            }
        }
        for (String name : ws.keySet()) {
            if (lookup(javaSa, name) == null) {
                extra.add(name + " (workspace)");
            }
        }
        if (!extra.isEmpty()) {
            out("");
            out("Serie presenti nelle altre fonti ma non calcolate in Java (" + extra.size() + "):");
            out("   " + String.join(", ", extra));
        }

        int nKo = koNames.size();
        out("");
        out("----------------------------- RIEPILOGO -----------------------------");
        out("Serie coerenti: " + nOk + " / " + (nOk + nKo));
        if (nKo > 0) {
            out("Serie NON coerenti (" + nKo + "): " + String.join(", ", koNames));
        }

        out("");
        out("Serie con differenze per confronto:");
        for (String what : new String[]{"WS vs JAVA", "WS vs SITIC", "JAVA vs SITIC"}) {
            List<String> withDiff = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (Diff d : diffs) {
                if (!sameComparison(d.what, what) || d.ok) {
                    continue;
                }
                if (d.n > 0) {
                    withDiff.add(d.series);   // valori diversi oltre la tolleranza
                } else {
                    missing.add(d.series + " (" + d.note + ")");   // serie assente o senza periodi comuni
                }
            }
            out("");
            out("  " + what + ": " + (!withDiff.isEmpty() ? withDiff.size() + " serie con differenze"
                    : missing.isEmpty() ? "nessuna differenza" : "nessuna differenza tra le serie confrontabili"));
            if (!withDiff.isEmpty()) {
                out("     " + String.join(", ", withDiff));
            }
            if (!missing.isEmpty()) {
                out("     non confrontabili (" + missing.size() + "): " + String.join(", ", missing));
            }
        }
        out("");
        out("Report CSV: " + reportCsv);
        out("Report TXT: " + reportTxt);
        out("====================================================================");

        writeReport(diffs, reportCsv);
        Files.write(Paths.get(reportTxt), txt, StandardCharsets.UTF_8);
        return nKo == 0;
    }

    // ---------------------------------------------------------------------
    // Lettura del CSV di TS+ (sa_new.csv)
    // ---------------------------------------------------------------------

    private static final Pattern DATE = Pattern.compile("^(\\d{4})\\s*[qQ]\\s*(\\d{1,2})$");

    /**
     * Legge sa_new.csv. Gestisce:
     *  - separatore ';', ',' o tabulazione (rilevato dall'intestazione);
     *  - decimale con virgola quando il separatore e' ';' o tab;
     *  - serie in colonna (prima colonna = date) oppure serie in riga
     *    (prima riga = date).
     *
     * @return nome serie -> (chiave periodo "AAAA-P" -> valore)
     */
    public static Map<String, Map<String, Double>> readSiticCsv(String fileName) throws IOException {
        List<String> lines = readLines(Paths.get(fileName));
        lines.removeIf(l -> l.trim().isEmpty());
        if (lines.isEmpty()) {
            throw new IOException("File vuoto: " + fileName);
        }

        String header = lines.get(0);
        char sep = detectSeparator(header);
        boolean commaDecimal = sep != ',';

        String[] head = split(header, sep);
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();

        boolean seriesInRows = head.length > 1 && periodKey(head[1]) != null;

        if (!seriesInRows) {
            // Formato "largo": prima colonna = data, colonne successive = serie
            for (int c = 1; c < head.length; c++) {
                out.put(head[c], new TreeMap<>());
            }
            for (int r = 1; r < lines.size(); r++) {
                String[] cells = split(lines.get(r), sep);
                String key = periodKey(cells[0]);
                if (key == null) {
                    System.err.println("sa_new.csv riga " + (r + 1) + ": data non riconosciuta '" + cells[0] + "'");
                    continue;
                }
                for (int c = 1; c < head.length && c < cells.length; c++) {
                    Double v = parseValue(cells[c], commaDecimal);
                    if (v != null) {
                        out.get(head[c]).put(key, v);
                    }
                }
            }
        } else {
            // Formato trasposto: prima riga = date, ogni riga successiva = una serie
            String[] keys = new String[head.length];
            for (int c = 1; c < head.length; c++) {
                keys[c] = periodKey(head[c]);
            }
            for (int r = 1; r < lines.size(); r++) {
                String[] cells = split(lines.get(r), sep);
                Map<String, Double> values = new TreeMap<>();
                for (int c = 1; c < head.length && c < cells.length; c++) {
                    Double v = parseValue(cells[c], commaDecimal);
                    if (keys[c] != null && v != null) {
                        values.put(keys[c], v);
                    }
                }
                out.put(cells[0], values);
            }
        }
        return out;
    }

    /** "2020q03" -> "2020-3" ; null se non e' una data. */
    static String periodKey(String cell) {
        Matcher m = DATE.matcher(cell.trim());
        if (!m.matches()) {
            return null;
        }
        return Integer.parseInt(m.group(1)) + "-" + Integer.parseInt(m.group(2));
    }

    private static Double parseValue(String cell, boolean commaDecimal) {
        String s = cell.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("NA") || s.equalsIgnoreCase("NaN")
                || s.equals(".") || s.equals("-")) {
            return null;
        }
        if (commaDecimal && s.indexOf(',') >= 0) {
            s = s.replace(".", "").replace(',', '.'); // 1.234,56 -> 1234.56 ; 12,327 -> 12.327
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static char detectSeparator(String header) {
        int semi = count(header, ';');
        int tab = count(header, '\t');
        int comma = count(header, ',');
        if (semi >= tab && semi >= comma && semi > 0) {
            return ';';
        }
        if (tab >= comma && tab > 0) {
            return '\t';
        }
        return ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static String[] split(String line, char sep) {
        String[] parts = line.split(Pattern.quote(String.valueOf(sep)), -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().replace("\"", "").replace("\uFEFF", "");
        }
        return parts;
    }

    private static List<String> readLines(Path p) throws IOException {
        try {
            return new ArrayList<>(Files.readAllLines(p, StandardCharsets.UTF_8));
        } catch (MalformedInputException ex) {
            return new ArrayList<>(Files.readAllLines(p, StandardCharsets.ISO_8859_1));
        }
    }

    // ---------------------------------------------------------------------
    // Lettura del workspace locale di JDemetra+
    // ---------------------------------------------------------------------

    /**
     * Apre il workspace, ricalcola tutte le stime e restituisce la serie SA
     * di ogni SaItem (di tutte le multiprocessing).
     * Nome serie = nome della serie contenuta nel SaItem.
     * (stessa API usata da RJDemetra: getSaDefinition().getTs().getName() e getResults())
     */
    public static Map<String, TsData> readWorkspaceSa(String fileName) throws IOException {
        Workspace ws = Workspace.open(fileName);
        if (ws == null) {
            throw new IOException("Impossibile aprire il workspace: " + fileName);
        }
        ws.computeAll();

        Map<String, TsData> out = new LinkedHashMap<>();
        for (int m = 0; m < ws.getMultiProcessingCount(); m++) {
            MultiProcessing mp = ws.getMultiProcessing(m);
            for (int i = 0; i < mp.size(); i++) {
                SaItem item = mp.get(i);
                String name = item.getSaDefinition().getTs().getName();
                IProcResults results = item.getResults();
                TsData sa = results == null ? null : results.getData("sa", TsData.class);
                if (sa == null) {
                    System.err.println("Workspace: nessun risultato 'sa' per " + name);
                    continue;
                }
                if (out.containsKey(name)) {
                    System.err.println("Workspace: serie duplicata '" + name + "', uso la prima trovata");
                    continue;
                }
                out.put(name, sa);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------
    // Confronto
    // ---------------------------------------------------------------------

    /** TsData -> (chiave periodo "AAAA-P" -> valore), P da 1 (mese o trimestre). */
    static Map<String, Double> toMap(TsData ts) {
        Map<String, Double> out = new TreeMap<>();
        if (ts == null) {
            return out;
        }
        TsPeriod p = ts.getStart();
        for (int i = 0; i < ts.getLength(); i++) {
            double v = ts.get(i);
            if (!Double.isNaN(v)) {
                out.put(p.getYear() + "-" + (p.getPosition() + 1), v);
            }
            p = p.plus(1);
        }
        return out;
    }

    Diff compare(String series, String what, Map<String, Double> a, Map<String, Double> b) {
        Diff d = new Diff(series, what);
        if (a == null || b == null) {
            d.note = (a == null ? what.split(" vs ")[0] : what.split(" vs ")[1]) + " mancante";
            return d;
        }
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double vb = b.get(e.getKey());
            if (vb == null) {
                d.onlyA++;
                continue;
            }
            double va = e.getValue();
            double abs = Math.abs(va - vb);
            double rel = vb == 0 ? (abs == 0 ? 0 : Double.POSITIVE_INFINITY) : abs / Math.abs(vb);
            d.n++;
            if (abs > d.maxAbs) {
                d.maxAbs = abs;
                d.maxRel = rel;
                d.worstPeriod = e.getKey();
                d.worstA = va;
                d.worstB = vb;
            }
            if (abs > absTol && rel > relTol) {
                d.nOut++;
            }
        }
        for (String k : b.keySet()) {
            if (!a.containsKey(k)) {
                d.onlyB++;
            }
        }
        if (d.n == 0) {
            d.note = "nessun periodo in comune";
        } else {
            d.ok = d.nOut == 0;
        }
        return d;
    }

    /** "JAVA vs WS" e "WS vs JAVA" indicano lo stesso confronto. */
    private static boolean sameComparison(String a, String b) {
        String[] x = a.split(" vs ");
        String[] y = b.split(" vs ");
        return (x[0].equals(y[0]) && x[1].equals(y[1])) || (x[0].equals(y[1]) && x[1].equals(y[0]));
    }

    /** Ricerca per nome esatto, poi ignorando maiuscole/minuscole e spazi. */
    private static <T> T lookup(Map<String, T> map, String name) {
        T v = map.get(name);
        if (v != null) {
            return v;
        }
        String norm = name.trim();
        for (Map.Entry<String, T> e : map.entrySet()) {
            if (e.getKey().trim().equalsIgnoreCase(norm)) {
                return e.getValue();
            }
        }
        return null;
    }

    static class Diff {
        final String series;
        final String what;
        int n;          // periodi in comune
        int nOut;       // periodi fuori tolleranza
        int onlyA;      // periodi solo nella prima fonte
        int onlyB;      // periodi solo nella seconda fonte
        double maxAbs;
        double maxRel;
        String worstPeriod = "";
        double worstA = Double.NaN;
        double worstB = Double.NaN;
        boolean ok;
        String note = "";

        Diff(String series, String what) {
            this.series = series;
            this.what = what;
        }

        String describe() {
            if (!note.isEmpty() && n == 0) {
                return what + ": " + note;
            }
            return String.format(Locale.ROOT,
                    "%s: %d/%d periodi fuori tolleranza, max diff %.6f (rel %.2e) in %s (%.6f vs %.6f)%s",
                    what, nOut, n, maxAbs, maxRel, worstPeriod.replace("-", "q"), worstA, worstB,
                    (onlyA + onlyB > 0) ? String.format(" [periodi non comuni: %d / %d]", onlyA, onlyB) : "");
        }
    }

    private static void writeReport(List<Diff> diffs, String reportCsv) throws IOException {
        Path p = Paths.get(reportCsv);
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write("serie;confronto;esito;periodi_comuni;periodi_fuori_tolleranza;solo_prima_fonte;"
                    + "solo_seconda_fonte;max_diff_assoluta;max_diff_relativa;periodo_max_diff;"
                    + "valore_prima_fonte;valore_seconda_fonte;note");
            w.newLine();
            for (Diff d : diffs) {
                w.write(String.join(";",
                        d.series,
                        d.what,
                        d.ok ? "OK" : "KO",
                        String.valueOf(d.n),
                        String.valueOf(d.nOut),
                        String.valueOf(d.onlyA),
                        String.valueOf(d.onlyB),
                        num(d.maxAbs),
                        num(d.maxRel),
                        d.worstPeriod.replace("-", "q"),
                        num(d.worstA),
                        num(d.worstB),
                        d.note));
                w.newLine();
            }
        }
    }

    /** Numero con virgola decimale, per aprire il report direttamente in Excel italiano. */
    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return String.format(Locale.ITALY, "%.8f", v);
    }
}