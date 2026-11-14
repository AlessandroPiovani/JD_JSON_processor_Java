package src;

import ec.tstoolkit.jdr.ws.MultiProcessing;
import ec.tstoolkit.jdr.ws.Workspace;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import jd2.datatypes.sa.SaItemType;
import ec.satoolkit.ISaSpecification;
import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.Parameter;
import ec.tstoolkit.ParameterType;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.modelling.arima.tramo.ArimaSpec;

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

    /** Differenza assoluta massima ammessa (es. arrotondamenti nel CSV di TS+). NaN = criterio disattivato. */
    private final double absTol;
    /** Differenza relativa massima ammessa (|a-b| / |b|, come frazione). NaN = criterio disattivato. */
    private final double relTol;

    /** Differenza relativa MEDIA massima ammessa sulla serie (frazione). NaN = criterio disattivato. */
    private final double meanRelTol;

    /** Solo i criteri per periodo (criterio sulla media disattivato). */
    public SaConsistencyCheck(double absTol, double relTol) {
        this(absTol, relTol, Double.NaN);
    }

    /**
     * Criteri per periodo: un periodo e' fuori tolleranza se supera TUTTI i criteri attivi (assoluto e
     * relativo); coerente se rientra in almeno uno.
     * Criterio sulla serie: la differenza relativa media della serie non deve superare meanRelTol.
     * Una serie e' KO se ha periodi fuori tolleranza OPPURE se la sua differenza relativa media supera
     * la soglia. Double.NaN disattiva un criterio; almeno uno dei tre deve essere attivo.
     */
    public SaConsistencyCheck(double absTol, double relTol, double meanRelTol) {
        if (Double.isNaN(absTol) && Double.isNaN(relTol) && Double.isNaN(meanRelTol)) {
            throw new IllegalArgumentException("Verifica: attivare almeno una tolleranza");
        }
        this.absTol = absTol;
        this.relTol = relTol;
        this.meanRelTol = meanRelTol;
    }

    /** Almeno un criterio per periodo attivo. */
    private boolean periodActive() {
        return absActive() || relActive();
    }

    private boolean meanActive() {
        return !Double.isNaN(meanRelTol);
    }

    private boolean absActive() {
        return !Double.isNaN(absTol);
    }

    private boolean relActive() {
        return !Double.isNaN(relTol);
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
     * @param siticCsv  percorso di sa_new.csv; null = confronto con TS+ non eseguito
     * @param wsFile    percorso del file .xml del workspace locale di JDemetra+;
     *                  null = confronto con il workspace non eseguito
     *                  (almeno uno dei due deve essere indicato)
     * @param reportCsv percorso del report CSV (la cartella deve esistere); null = non salvato
     * @param reportTxt percorso del report TXT (la cartella deve esistere); null = non salvato
     * @return true se tutte le serie sono coerenti tra le tre fonti
     */
    public boolean run(Map<String, TsData> javaSa, String siticCsv, String wsFile,
            String reportCsv, String reportTxt) throws IOException {

        if (siticCsv == null && wsFile == null) {
            throw new IllegalArgumentException("Verifica: indicare almeno uno tra il file di TS+ (sa_new.csv) e il workspace");
        }
        txt.clear();
        out("=============== VERIFICA SERIE DESTAGIONALIZZATE ===============");
        out("Data esecuzione: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date()));
        out("Tolleranze: assoluta = " + (absActive() ? String.format(Locale.ROOT, "%g", absTol) : "disattivata")
                + ", relativa = " + (relActive() ? percent(relTol) : "disattivata"));
        out("Tolleranza sulla differenza relativa media della serie = "
                + (meanActive() ? percent(meanRelTol) : "disattivata"));
        if (absActive() && relActive()) {
            out("Un periodo e' coerente se la differenza e' entro la soglia assoluta OPPURE entro quella relativa.");
        } else if (periodActive()) {
            out("Un periodo e' coerente se la differenza e' entro la soglia " + (absActive() ? "assoluta." : "relativa."));
        } else {
            out("Nessun controllo per periodo: decide solo la differenza relativa media.");
        }
        if (meanActive()) {
            out("Una serie e' KO se ha periodi fuori tolleranza OPPURE se la differenza relativa media supera la soglia.");
        }
        out("max diff = periodo con la differenza assoluta piu' grande; peggiore = periodo fuori tolleranza"
                + " con la differenza relativa piu' grande.");
        out("");

        // Fonti attive: null = confronto non richiesto
        Map<String, Map<String, Double>> sitic = null;
        if (siticCsv != null) {
            sitic = readSiticCsv(siticCsv);
            out("Serie lette da TS+ (" + siticCsv + "): " + sitic.size());
        } else {
            out("Confronto con TS+: non richiesto");
        }

        Map<String, TsData> ws = null;
        if (wsFile != null) {
            ws = readWorkspaceSa(wsFile);
            out("Serie lette dal workspace (" + wsFile + "): " + ws.size());
        } else {
            out("Confronto con il workspace: non richiesto");
        }
        out("Serie calcolate in Java: " + javaSa.size());
        out("");

        List<Diff> diffs = new ArrayList<>();
        List<String> koNames = new ArrayList<>();
        int nOk = 0;

        out("---------------------------- DETTAGLIO ----------------------------");
        for (Map.Entry<String, TsData> e : javaSa.entrySet()) {
            String name = e.getKey();
            Map<String, Double> j = toMap(e.getValue());
            Map<String, Double> s = sitic == null ? null : lookup(sitic, name);
            TsData wsTs = ws == null ? null : lookup(ws, name);
            Map<String, Double> w = wsTs == null ? null : toMap(wsTs);

            List<Diff> seriesDiffs = new ArrayList<>();
            if (sitic != null) {
                seriesDiffs.add(compare(name, "JAVA vs SITIC", j, s));
            }
            if (ws != null) {
                seriesDiffs.add(compare(name, "JAVA vs WS", j, w));
            }
            if (sitic != null && ws != null) {
                seriesDiffs.add(compare(name, "WS vs SITIC", w, s));
            }

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
        if (sitic != null) {
            for (String name : sitic.keySet()) {
                if (lookup(javaSa, name) == null) {
                    extra.add(name + " (TS+)");
                }
            }
        }
        if (ws != null) {
            for (String name : ws.keySet()) {
                if (lookup(javaSa, name) == null) {
                    extra.add(name + " (workspace)");
                }
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
        List<String> comparisons = new ArrayList<>();
        if (ws != null) {
            comparisons.add("WS vs JAVA");
        }
        if (ws != null && sitic != null) {
            comparisons.add("WS vs SITIC");
        }
        if (sitic != null) {
            comparisons.add("JAVA vs SITIC");
        }
        for (String what : comparisons) {
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
        if (reportCsv != null) {
            out("Report CSV: " + reportCsv);
        }
        if (reportTxt != null) {
            out("Report TXT: " + reportTxt);
        }
        out("====================================================================");

        if (reportCsv != null) {
            writeReport(diffs, reportCsv);
        }
        if (reportTxt != null) {
            Files.write(Paths.get(reportTxt), txt, StandardCharsets.UTF_8);
        }
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
     * Apre il workspace e RICALCOLA DA ZERO ogni serie, senza usare i risultati salvati.
     *
     * Perche': jdr (SaItem.compute) stima ogni serie partendo dalla specifica Point salvata,
     * cioe' dal modello di una stima precedente, con i coefficienti gia' stimati come
     * valori di partenza. Nelle serie con stima "piatta" (es. Pasqua + giorni lavorativi)
     * questo porta a risultati leggermente diversi da una stima fatta da zero.
     *
     * Qui invece ogni serie viene stimata con la stessa regola usata dallo script R per il JSON:
     *  - si parte dalla specifica Domain;
     *  - se il modello automatico e' spento e c'e' la Point, l'ARIMA (ordini e coefficienti
     *    fissati) viene preso dalla Point, perche' nei workspace del plugin TS+ il modello
     *    vero e' solo li'; i coefficienti "stimati" della Point tornano liberi.
     * Nome serie = nome della serie contenuta nel SaItem.
     */
    public static Map<String, TsData> readWorkspaceSa(String fileName) throws IOException {
        Workspace ws = Workspace.open(fileName);
        if (ws == null) {
            throw new IOException("Impossibile aprire il workspace: " + fileName);
        }

        Map<String, TsData> out = new LinkedHashMap<>();
        for (int m = 0; m < ws.getMultiProcessingCount(); m++) {
            MultiProcessing mp = ws.getMultiProcessing(m);
            for (int i = 0; i < mp.size(); i++) {
                SaItemType def = mp.get(i).getSaDefinition();
                String name = def.getTs().getName();
                TsData data = def.getTs().getData();
                TramoSeatsSpecification spec = specFromScratch(def);
                if (data == null || spec == null) {
                    System.err.println("Workspace: serie '" + name + "' senza dati o senza specifica TRAMO-SEATS, ignorata");
                    continue;
                }
                CompositeResults rslt = TramoSeatsProcessingFactory.process(data.cleanExtremities(), spec, ws.getContext());
                TsData sa = rslt == null ? null : rslt.getData("sa", TsData.class);
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

    /** Specifica da usare per la stima da zero (vedi readWorkspaceSa). */
    private static TramoSeatsSpecification specFromScratch(SaItemType def) {
        ISaSpecification domain = def.getDomainSpec();
        if (!(domain instanceof TramoSeatsSpecification)) {
            return null;
        }
        TramoSeatsSpecification spec = ((TramoSeatsSpecification) domain).clone();

        ISaSpecification point = def.getPointSpec();
        if (!spec.getTramoSpecification().getAutoModel().isEnabled() && point instanceof TramoSeatsSpecification) {
            ArimaSpec arima = ((TramoSeatsSpecification) point).getTramoSpecification().getArima().clone();
            arima.setPhi(freeEstimated(arima.getPhi()));
            arima.setTheta(freeEstimated(arima.getTheta()));
            arima.setBPhi(freeEstimated(arima.getBPhi()));
            arima.setBTheta(freeEstimated(arima.getBTheta()));
            // media: se presente e non fissata, torna stimata (niente valore di una stima precedente)
            if (arima.getMu() != null && arima.getMu().getType() != ParameterType.Fixed) {
                arima.setMu(new Parameter());
            }
            spec.getTramoSpecification().setArima(arima);
        }
        return spec;
    }

    /** Coefficienti "stimati" (valori di una stima precedente) -> liberi; Fixed e Initial restano. */
    private static Parameter[] freeEstimated(Parameter[] params) {
        if (params == null) {
            return null;
        }
        Parameter[] out = new Parameter[params.length];
        for (int k = 0; k < params.length; k++) {
            Parameter p = params[k];
            boolean keep = p != null && (p.getType() == ParameterType.Fixed || p.getType() == ParameterType.Initial);
            out[k] = keep ? p.clone() : new Parameter();
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
            d.sumAbs += abs;
            if (!Double.isInfinite(rel)) {   // valore di riferimento 0: escluso dalla media relativa
                d.sumRel += rel;
                d.nRel++;
            }
            if (abs > d.maxAbs) {
                d.maxAbs = abs;
                d.maxRel = rel;
                d.worstPeriod = e.getKey();
                d.worstA = va;
                d.worstB = vb;
            }
            // fuori tolleranza = supera tutti i criteri per periodo attivi
            boolean absOut = !absActive() || abs > absTol;
            boolean relOut = !relActive() || rel > relTol;
            if (periodActive() && absOut && relOut) {
                d.nOut++;
                d.outPeriods.add(e.getKey());
                // peggiore tra i periodi fuori: differenza relativa piu' grande (a parita', assoluta)
                if (d.outPeriod.isEmpty() || rel > d.outRel || (rel == d.outRel && abs > d.outAbs)) {
                    d.outPeriod = e.getKey();
                    d.outAbs = abs;
                    d.outRel = rel;
                    d.outA = va;
                    d.outB = vb;
                }
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
            d.meanAbs = d.sumAbs / d.n;
            d.meanRel = d.nRel > 0 ? d.sumRel / d.nRel : Double.NaN;
            d.meanOut = meanActive() && !Double.isNaN(d.meanRel) && d.meanRel > meanRelTol;
            d.ok = d.nOut == 0 && !d.meanOut;
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
        // periodi fuori tolleranza e il peggiore tra questi (differenza relativa piu' grande)
        final List<String> outPeriods = new ArrayList<>();
        String outPeriod = "";
        double outAbs = Double.NaN;
        double outRel = Double.NaN;
        double outA = Double.NaN;
        double outB = Double.NaN;
        // differenze medie sulla serie (la relativa esclude i periodi con valore di riferimento 0)
        double sumAbs, sumRel;
        int nRel;
        double meanAbs = Double.NaN;
        double meanRel = Double.NaN;
        boolean meanOut;

        Diff(String series, String what) {
            this.series = series;
            this.what = what;
        }

        String describe() {
            if (!note.isEmpty() && n == 0) {
                return what + ": " + note;
            }
            String worstOut = nOut == 0 ? "" : String.format(Locale.ROOT,
                    " (peggiore %s: %.6f vs %.6f, diff %.6f, rel %s)",
                    outPeriod.replace("-", "q"), outA, outB, outAbs, percent(outRel));
            String mean = String.format(Locale.ROOT, ", media diff %.6f (rel %s)%s",
                    meanAbs, percent(meanRel), meanOut ? " MEDIA OLTRE SOGLIA" : "");
            return String.format(Locale.ROOT,
                    "%s: %d/%d periodi fuori tolleranza%s, max diff %.6f (rel %s) in %s (%.6f vs %.6f)%s%s",
                    what, nOut, n, worstOut, maxAbs, percent(maxRel), worstPeriod.replace("-", "q"), worstA, worstB,
                    mean,
                    (onlyA + onlyB > 0) ? String.format(" [periodi non comuni: %d / %d]", onlyA, onlyB) : "");
        }
    }

    private static void writeReport(List<Diff> diffs, String reportCsv) throws IOException {
        Path p = Paths.get(reportCsv);
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write("serie;confronto;esito;periodi_comuni;periodi_fuori_tolleranza;solo_prima_fonte;"
                    + "solo_seconda_fonte;max_diff_assoluta;max_diff_relativa_perc;periodo_max_diff;"
                    + "valore_prima_fonte;valore_seconda_fonte;"
                    + "periodo_peggiore_fuori_tolleranza;valore_prima_fonte_peggiore;valore_seconda_fonte_peggiore;"
                    + "diff_assoluta_peggiore;diff_relativa_perc_peggiore;periodi_fuori_tolleranza_elenco;"
                    + "diff_assoluta_media;diff_relativa_media_perc;media_oltre_soglia;note");
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
                        num(d.maxRel * 100),
                        d.worstPeriod.replace("-", "q"),
                        num(d.worstA),
                        num(d.worstB),
                        d.outPeriod.replace("-", "q"),
                        num(d.outA),
                        num(d.outB),
                        num(d.outAbs),
                        num(d.outRel * 100),
                        String.join(" ", d.outPeriods).replace("-", "q"),
                        num(d.meanAbs),
                        num(d.meanRel * 100),
                        d.meanOut ? "SI" : "",
                        d.note));
                w.newLine();
            }
        }
    }

    /** Differenza relativa (frazione) in percentuale, es. 0.00228 -> "0.228%". */
    static String percent(double fraction) {
        if (Double.isNaN(fraction) || Double.isInfinite(fraction)) {
            return String.valueOf(fraction);
        }
        return String.format(Locale.ROOT, "%.3g%%", fraction * 100);
    }

    /** Numero con virgola decimale, per aprire il report direttamente in Excel italiano. */
    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return String.format(Locale.ITALY, "%.8f", v);
    }
}
