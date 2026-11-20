package src;

import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsPeriod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grafici delle serie destagionalizzate in un file PDF, una pagina per serie, come compare_sa_ts in R:
 * modello nuovo in blu (linea continua), modello vecchio in rosso (tratteggiata), legenda a destra,
 * pagina 11 x 6 pollici. Se una serie c'e' in un solo modello, nella pagina c'e' solo quella.
 *
 * Il PDF e' scritto direttamente (testo e linee vettoriali, carattere Helvetica standard), senza
 * librerie esterne.
 *
 * @author alessandro.piovani@istat.it
 */
public final class SaPlotsPdf {

    private static final double W = 11 * 72, H = 6 * 72;            // pagina in punti (11 x 6 pollici)
    private static final double PLOT_W = W * 6 / 7;                 // come layout(widths = c(6, 1)) in R
    private static final double L = 62, R = 14, T = 50, B = 58;     // margini dell'area del grafico
    private static final String NEW_COLOR = "0 0 1";                // blu
    private static final String OLD_COLOR = "1 0 0";                // rosso

    private SaPlotsPdf() {
    }

    /**
     * Scrive il PDF. newSa e oldSa possono essere null (modello non indicato): in quel caso nelle
     * pagine e nella legenda c'e' solo l'altro modello. Ordine delle pagine: serie del modello
     * nuovo, poi quelle presenti solo nel vecchio.
     */
    public static void write(File file, Map<String, TsData> newSa, Map<String, TsData> oldSa) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        if (newSa != null) names.addAll(newSa.keySet());
        if (oldSa != null) names.addAll(oldSa.keySet());

        List<byte[]> pages = new ArrayList<>();
        for (String name : names) {
            TsData n = newSa == null ? null : newSa.get(name);
            TsData o = oldSa == null ? null : oldSa.get(name);
            pages.add(page(name, n, o).getBytes(StandardCharsets.ISO_8859_1));
        }
        if (pages.isEmpty()) {
            pages.add(text(W / 2 - 60, H / 2, 14, false, "Nessuna serie").getBytes(StandardCharsets.ISO_8859_1));
        }
        writePdf(file, pages);
    }

    // ------------------------------------------------------------------ una pagina

    private static String page(String name, TsData newTs, TsData oldTs) {
        StringBuilder sb = new StringBuilder();
        double x0 = L, x1 = PLOT_W - R, y0 = B, y1 = H - T;

        // intervalli degli assi: unione delle due serie
        double tMin = Double.POSITIVE_INFINITY, tMax = Double.NEGATIVE_INFINITY;
        double vMin = Double.POSITIVE_INFINITY, vMax = Double.NEGATIVE_INFINITY;
        for (TsData s : new TsData[]{newTs, oldTs}) {
            if (s == null) continue;
            for (int k = 0; k < s.getLength(); k++) {
                double v = s.get(k);
                if (Double.isNaN(v) || Double.isInfinite(v)) continue;
                double t = time(s, k);
                tMin = Math.min(tMin, t);
                tMax = Math.max(tMax, t);
                vMin = Math.min(vMin, v);
                vMax = Math.max(vMax, v);
            }
        }
        // titolo
        sb.append(text(center(name, 14, true, (x0 + x1) / 2), H - 30, 14, true, name));

        if (tMin > tMax) {   // nessun dato
            sb.append(text(center("nessun dato", 11, false, (x0 + x1) / 2), (y0 + y1) / 2, 11, false, "nessun dato"));
            return sb.toString();
        }
        if (tMin == tMax) { tMin -= 0.5; tMax += 0.5; }
        if (vMin == vMax) { double d = Math.abs(vMin) * 0.04 + 1; vMin -= d; vMax += d; }
        // estensione del 4% come in R (xaxs = yaxs = "r")
        double tPad = (tMax - tMin) * 0.04, vPad = (vMax - vMin) * 0.04;
        final double ta = tMin - tPad, tb = tMax + tPad, va = vMin - vPad, vb = vMax + vPad;
        List<Double> yTicks = pretty(va, vb, 5);
        double labW = 0;
        for (double v : yTicks) labW = Math.max(labW, width(label(v), 10, false));
        x0 = Math.max(L, 30 + labW + 14);   // spazio per le etichette dell'asse y e per "Values"

        // assi e tacche
        sb.append("0 0 0 RG 0.75 w [] 0 d\n");
        sb.append(f("%.2f %.2f %.2f %.2f re S\n", x0, y0, x1 - x0, y1 - y0));
        for (double t : pretty(ta, tb, 6)) {
            double x = x0 + (t - ta) / (tb - ta) * (x1 - x0);
            sb.append(f("%.2f %.2f m %.2f %.2f l S\n", x, y0, x, y0 - 5));
            String lab = label(t);
            sb.append(text(center(lab, 10, false, x), y0 - 17, 10, false, lab));
        }
        for (double v : yTicks) {
            double y = y0 + (v - va) / (vb - va) * (y1 - y0);
            sb.append(f("%.2f %.2f m %.2f %.2f l S\n", x0, y, x0 - 5, y));
            String lab = label(v);
            sb.append(text(x0 - 8 - width(lab, 10, false), y - 3.5, 10, false, lab));
        }
        sb.append(text(center("Time", 11, false, (x0 + x1) / 2), y0 - 38, 11, false, "Time"));
        // "Values" ruotato, come in R
        sb.append(f("BT /F1 11 Tf 0 1 -1 0 %.2f %.2f Tm (Values) Tj ET\n", x0 - labW - 22, (y0 + y1) / 2 - width("Values", 11, false) / 2));

        // serie: prima il nuovo, poi il vecchio sopra (come in R: plot del nuovo, lines del vecchio)
        sb.append(f("q %.2f %.2f %.2f %.2f re W n\n", x0, y0, x1 - x0, y1 - y0));
        if (newTs != null) sb.append(polyline(newTs, NEW_COLOR, "[] 0 d", 1.0, ta, tb, va, vb, x0, x1, y0, y1));
        if (oldTs != null) sb.append(polyline(oldTs, OLD_COLOR, "[4 4] 0 d", 1.0, ta, tb, va, vb, x0, x1, y0, y1));
        sb.append("Q\n");

        // legenda nel pannello di destra: solo le serie presenti nella pagina
        List<String[]> items = new ArrayList<>();
        if (newTs != null) items.add(new String[]{"new model", NEW_COLOR, "[] 0 d"});
        if (oldTs != null) items.add(new String[]{"old model", OLD_COLOR, "[4 4] 0 d"});
        double lx = PLOT_W + 4, ly = H / 2 + (items.size() - 1) * 9;
        for (String[] it : items) {
            sb.append(it[1]).append(" RG 2 w ").append(it[2]).append('\n');
            sb.append(f("%.2f %.2f m %.2f %.2f l S\n", lx, ly + 3.5, lx + 28, ly + 3.5));
            sb.append(text(lx + 34, ly, 10, false, it[0]));
            ly -= 18;
        }
        sb.append("[] 0 d\n");
        return sb.toString();
    }

    /** Spezzata della serie; i valori mancanti interrompono la linea. */
    private static String polyline(TsData s, String color, String dash, double lw,
            double ta, double tb, double va, double vb, double x0, double x1, double y0, double y1) {
        StringBuilder sb = new StringBuilder();
        sb.append(color).append(" RG ").append(f("%.2f", lw)).append(" w 1 j ").append(dash).append('\n');
        boolean open = false;
        for (int k = 0; k < s.getLength(); k++) {
            double v = s.get(k);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                if (open) sb.append("S\n");
                open = false;
                continue;
            }
            double x = x0 + (time(s, k) - ta) / (tb - ta) * (x1 - x0);
            double y = y0 + (v - va) / (vb - va) * (y1 - y0);
            sb.append(f("%.2f %.2f %s\n", x, y, open ? "l" : "m"));
            open = true;
        }
        if (open) sb.append("S\n");
        return sb.toString();
    }

    /** Tempo in anni decimali, come time() di R: anno + (periodo - 1) / frequenza. */
    private static double time(TsData s, int k) {
        TsPeriod p = s.getDomain().get(k);
        return p.getYear() + (double) p.getPosition() / s.getFrequency().intValue();
    }

    // ------------------------------------------------------------------ tacche "belle" (come pretty() in R)

    static List<Double> pretty(double a, double b, int n) {
        List<Double> out = new ArrayList<>();
        double range = b - a;
        if (!(range > 0)) return out;
        double raw = range / n;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double[] steps = {1, 2, 5, 10};
        double step = steps[steps.length - 1] * mag;
        for (double s : steps) {
            if (s * mag >= raw * 0.75) { step = s * mag; break; }
        }
        double start = Math.ceil(a / step - 1e-9) * step;
        for (double v = start; v <= b + step * 1e-9; v += step) {
            out.add(Math.abs(v) < step * 1e-9 ? 0.0 : v);
        }
        return out;
    }

    private static String label(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        String s = String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    // ------------------------------------------------------------------ testo

    private static String text(double x, double y, double size, boolean bold, String s) {
        return f("BT /%s %.1f Tf %.2f %.2f Td (%s) Tj ET\n", bold ? "F2" : "F1", size, x, y, escape(s));
    }

    private static double center(String s, double size, boolean bold, double cx) {
        return cx - width(s, size, bold) / 2;
    }

    /** Larghezza approssimata del testo in Helvetica (sufficiente per centrare titoli ed etichette). */
    private static double width(String s, double size, boolean bold) {
        double w = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') w += 278;
            else if ("il.,:;'|!".indexOf(c) >= 0) w += 250;
            else if ("fjtrI()[]-".indexOf(c) >= 0) w += 333;
            else if (Character.isDigit(c)) w += 556;
            else if ("mwMW".indexOf(c) >= 0) w += 833;
            else if (Character.isUpperCase(c)) w += 667;
            else w += 556;
        }
        return w * size / 1000 * (bold ? 1.05 : 1);
    }

    /** Stringa PDF: parentesi e backslash con escape, caratteri fuori da Latin-1 sostituiti con '?'. */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c < 32) sb.append(' ');
            else if (c > 255) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String f(String fmt, Object... args) {
        return String.format(Locale.ROOT, fmt, args);
    }

    // ------------------------------------------------------------------ struttura del PDF

    private static void writePdf(File file, List<byte[]> contents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        int nPages = contents.size();
        // oggetti: 1 catalogo, 2 pagine, 3 Helvetica, 4 Helvetica-Bold, poi pagina e contenuto per ogni serie
        int firstPage = 5;
        put(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

        offsets.add(out.size());
        put(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets.add(out.size());
        StringBuilder kids = new StringBuilder();
        for (int k = 0; k < nPages; k++) kids.append(firstPage + 2 * k).append(" 0 R ");
        put(out, "2 0 obj\n<< /Type /Pages /Count " + nPages + " /Kids [" + kids + "] >>\nendobj\n");

        offsets.add(out.size());
        put(out, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");
        offsets.add(out.size());
        put(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

        for (int k = 0; k < nPages; k++) {
            int pageObj = firstPage + 2 * k, contentObj = pageObj + 1;
            offsets.add(out.size());
            put(out, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) W + " " + (int) H + "]"
                    + " /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + contentObj + " 0 R >>\nendobj\n");
            byte[] c = contents.get(k);
            offsets.add(out.size());
            put(out, contentObj + " 0 obj\n<< /Length " + c.length + " >>\nstream\n");
            out.write(c);
            put(out, "\nendstream\nendobj\n");
        }

        int xref = out.size();
        int nObj = offsets.size() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("xref\n0 ").append(nObj).append("\n0000000000 65535 f \n");
        for (int off : offsets) sb.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
        sb.append("trailer\n<< /Size ").append(nObj).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        put(out, sb.toString());

        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Impossibile creare la cartella " + dir);
        }
        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            out.writeTo(os);
        }
    }

    private static void put(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
