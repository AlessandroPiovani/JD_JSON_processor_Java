package src;

import ec.satoolkit.ISaSpecification;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.Parameter;
import ec.tstoolkit.ParameterType;
import ec.tstoolkit.algorithm.ProcessingContext;
import ec.tstoolkit.jdr.ws.MultiProcessing;
import ec.tstoolkit.jdr.ws.Workspace;
import ec.tstoolkit.modelling.arima.tramo.ArimaSpec;
import ec.tstoolkit.modelling.arima.tramo.RegressionSpec;
import ec.tstoolkit.timeseries.regression.ITsVariable;
import ec.tstoolkit.timeseries.regression.InterventionVariable;
import ec.tstoolkit.timeseries.regression.Ramp;
import ec.tstoolkit.timeseries.regression.Sequence;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsDomain;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import jd2.datatypes.sa.SaItemType;
import jdr.spec.tramoseats.TramoSeatsSpec;
import jdr.spec.ts.SpanSelector;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estrae le specifiche JSON (JD_JSON) da un workspace di JDemetra+, come fa RJDProcessor in R:
 *  - versione ridotta (reduced): JD_JSON_from_materialized_workspace(..., diff = TRUE),
 *    con il lettore dei regressori in formato TRAMO-SEATS+ (Data_reader_ext_reg_tsplus);
 *  - versione completa (full): from_reduced_to_full_JD_JSON_file(...) applicata al file ridotto.
 *
 * Il codice segue passo per passo quello R (Extended_tramoseats_spec.R, JD_JSON.R,
 * Data_reader_ext_reg_tsplus.R, basic_spec.R, utility_functions.R) e legge la specifica con le
 * stesse classi Java usate da RJDemetra (jdr), quindi i file prodotti sono identici a quelli di R,
 * comprese la formattazione e l'ordine dei campi.
 *
 * Uso da riga di comando:
 *   java -jar WorkspaceToJson.jar workspace.xml [cartella_regressori] [file_ridotto] [file_completo]
 * Senza argomenti si apre la finestra di AppWorkspaceJson.
 *
 * @author alessandro.piovani@istat.it
 */
public final class WorkspaceToJson {

    public static final String DEFAULT_REDUCED_FILE = "specifications_new.txt";
    public static final String DEFAULT_FULL_FILE    = "specifications_new_full.txt";

    // =====================================================================================
    //  Valori "alla R": servono a riprodurre identical(), is.na() e rjson::toJSON()
    // =====================================================================================

    /** Un valore R: vettore atomico (logical, integer, double, character), lista o NULL. */
    static final class RV {
        enum Kind { NULL, LGL, INT, DBL, CHR, LIST }

        static final RV NULL = new RV(Kind.NULL, Collections.emptyList(), null);

        final Kind kind;
        final List<Object> values;   // elementi (null = NA) oppure, per le liste, altri RV
        final List<String> names;    // solo per le liste con nomi (oggetti JSON)

        private RV(Kind kind, List<Object> values, List<String> names) {
            this.kind = kind;
            this.values = values;
            this.names = names;
        }

        static RV b(boolean v)  { return new RV(Kind.LGL, Collections.singletonList(v), null); }
        static RV i(int v)      { return new RV(Kind.INT, Collections.singletonList(v), null); }
        static RV d(double v)   { return new RV(Kind.DBL, Collections.singletonList(v), null); }
        static RV s(String v)   { return new RV(Kind.CHR, Collections.singletonList(v), null); }
        static RV naLgl()       { return new RV(Kind.LGL, Collections.singletonList(null), null); }
        static RV naInt()       { return new RV(Kind.INT, Collections.singletonList(null), null); }
        static RV naDbl()       { return new RV(Kind.DBL, Collections.singletonList(null), null); }
        static RV naChr()       { return new RV(Kind.CHR, Collections.singletonList(null), null); }

        static RV chrVec(List<String> v) { return new RV(Kind.CHR, new ArrayList<Object>(v), null); }
        static RV dblVec(List<Double> v) { return new RV(Kind.DBL, new ArrayList<Object>(v), null); }

        static RV list(List<RV> items) { return new RV(Kind.LIST, new ArrayList<Object>(items), null); }

        static RV named(Object... kv) {
            List<Object> vals = new ArrayList<>();
            List<String> nms = new ArrayList<>();
            for (int k = 0; k < kv.length; k += 2) {
                nms.add((String) kv[k]);
                vals.add(kv[k + 1]);
            }
            return new RV(Kind.LIST, vals, nms);
        }

        /** Copia di una lista con nomi a cui si aggiunge (o sostituisce) un elemento: x$name <- value. */
        RV with(String name, RV value) {
            List<Object> vals = new ArrayList<>(values);
            List<String> nms = new ArrayList<>(names);
            int idx = nms.indexOf(name);
            if (idx >= 0) {
                vals.set(idx, value);
            } else {
                nms.add(name);
                vals.add(value);
            }
            return new RV(Kind.LIST, vals, nms);
        }

        int length() { return values.size(); }

        /** all(is.na(x)) di R (vero anche per NULL e per i vettori/liste vuoti). */
        boolean allNA() {
            if (kind == Kind.NULL) return true;
            for (Object o : values) {
                if (kind == Kind.LIST) {
                    RV e = (RV) o;
                    boolean na = e.kind != Kind.LIST && e.kind != Kind.NULL && e.length() == 1 && e.values.get(0) == null;
                    if (!na) return false;
                } else if (o != null) {
                    return false;
                }
            }
            return true;
        }

        /** identical(a, b) di R per i valori usati qui. */
        static boolean identical(RV a, RV b) {
            if (a.kind != b.kind || a.length() != b.length()) return false;
            if (!Objects.equals(a.names, b.names)) return false;
            for (int k = 0; k < a.length(); k++) {
                Object x = a.values.get(k), y = b.values.get(k);
                if (a.kind == Kind.LIST) {
                    if (!identical((RV) x, (RV) y)) return false;
                } else if (x == null || y == null) {
                    if (x != y) return false;
                } else if (a.kind == Kind.DBL) {
                    double dx = (Double) x, dy = (Double) y;
                    if (!(dx == dy || (Double.isNaN(dx) && Double.isNaN(dy)))) return false;
                } else if (!x.equals(y)) {
                    return false;
                }
            }
            return true;
        }

        /** rjson::toJSON(x) (implementazione C di rjson). */
        String toJson() {
            StringBuilder sb = new StringBuilder();
            appendJson(sb);
            return sb.toString();
        }

        private void appendJson(StringBuilder sb) {
            switch (kind) {
                case NULL:
                    sb.append("null");
                    return;
                case LIST:
                    boolean obj = names != null;
                    sb.append(obj ? '{' : '[');
                    for (int k = 0; k < values.size(); k++) {
                        if (k > 0) sb.append(',');
                        if (obj) sb.append(jsonString(names.get(k))).append(':');
                        ((RV) values.get(k)).appendJson(sb);
                    }
                    sb.append(obj ? '}' : ']');
                    return;
                default:
                    if (values.size() == 1) {
                        appendScalar(sb, values.get(0));
                    } else {
                        sb.append('[');
                        for (int k = 0; k < values.size(); k++) {
                            if (k > 0) sb.append(',');
                            appendScalar(sb, values.get(k));
                        }
                        sb.append(']');
                    }
            }
        }

        private void appendScalar(StringBuilder sb, Object v) {
            if (v == null) {
                sb.append("\"NA\"");
            } else if (v instanceof Boolean) {
                sb.append(((Boolean) v) ? "true" : "false");
            } else if (v instanceof Integer) {
                sb.append(v.toString());
            } else if (v instanceof Double) {
                double d = (Double) v;
                if (Double.isNaN(d)) sb.append("\"NaN\"");
                else if (Double.isInfinite(d)) sb.append(d > 0 ? "\"Inf\"" : "\"-Inf\"");
                else sb.append(formatG15(d));
            } else {
                sb.append(jsonString(v.toString()));
            }
        }
    }

    /** sprintf("%.15g", x) del C: e' il formato dei numeri di rjson. */
    static String formatG15(double x) {
        if (x == 0) return (1 / x < 0) ? "-0" : "0";
        BigDecimal r = new BigDecimal(x).round(new MathContext(15, RoundingMode.HALF_EVEN));
        int exp = r.precision() - r.scale() - 1;           // esponente decimale del valore arrotondato
        if (exp < -4 || exp >= 15) {
            BigDecimal mant = r.movePointLeft(exp).stripTrailingZeros();
            String m = mant.toPlainString();
            String e = String.valueOf(Math.abs(exp));
            if (e.length() < 2) e = "0" + e;
            return m + "e" + (exp < 0 ? "-" : "+") + e;
        }
        String s = r.stripTrailingZeros().toPlainString();
        return s;
    }

    /** Stringa JSON come rjson: virgolette e backslash con escape, caratteri non ASCII come \\uXXXX. */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // =====================================================================================
    //  Campi del JD_JSON (slot della classe Extended_tramoseats_spec, nello stesso ordine)
    // =====================================================================================

    static final String[] SLOTS = {
        "series_name", "series_start", "series_end", "series_first", "series_last", "series_exclFirst",
        "series_exclLast", "frequency", "method", "spec", "preliminary.check", "estimate.from", "estimate.to",
        "estimate.first", "estimate.last", "estimate.exclFirst", "estimate.exclLast", "estimate.tol",
        "estimate.eml", "estimate.urfinal", "transform.function", "transform.fct", "usrdef.outliersEnabled",
        "usrdef.outliersType", "usrdef.outliersDate", "usrdef.outliersCoef", "userdef.varFromFile",
        "userdef.varFromFile.infoList", "usrdef.varEnabled", "usrdef.var", "usrdef.varType", "usrdef.varCoef",
        "tradingdays.mauto", "tradingdays.pftd", "tradingdays.option", "tradingdays.leapyear",
        "tradingdays.stocktd", "tradingdays.test", "easter.type", "easter.julian", "easter.duration",
        "easter.test", "outlier.enabled", "outlier.from", "outlier.to", "outlier.first", "outlier.last",
        "outlier.exclFirst", "outlier.exclLast", "outlier.ao", "outlier.tc", "outlier.ls", "outlier.so",
        "outlier.usedefcv", "outlier.cv", "outlier.eml", "outlier.tcrate", "automdl.enabled",
        "automdl.acceptdefault", "automdl.cancel", "automdl.ub1", "automdl.ub2", "automdl.armalimit",
        "automdl.reducecv", "automdl.ljungboxlimit", "automdl.compare", "arima.mu", "arima.muFixedValue",
        "arima.p", "arima.d", "arima.q", "arima.bp", "arima.bd", "arima.bq", "arima.coefEnabled", "arima.coef",
        "arima.coefType", "fcst.horizon", "seats.predictionLength", "seats.approx", "seats.trendBoundary",
        "seats.seasdBoundary", "seats.seasdBoundary1", "seats.seasTol", "seats.maBoundary", "seats.method",
        "ramps", "intervention_variables", "easterCoef"
    };

    /** Campi scritti sempre per primi nel JSON ridotto e impostati direttamente dal costruttore R. */
    private static final List<String> HEADER = Arrays.asList("series_name", "frequency", "method", "spec");

    /** Valori di default delle specifiche di base: get_basic_spec() di basic_spec.R. */
    private static final Map<String, Map<String, RV>> DEFAULTS = new HashMap<>();

    static {
        Map<String, RV> rsa0 = new LinkedHashMap<>();
        rsa0.put("series_name", RV.naLgl());
        rsa0.put("series_start", RV.naChr());
        rsa0.put("series_end", RV.naChr());
        rsa0.put("series_first", RV.naInt());
        rsa0.put("series_last", RV.naInt());
        rsa0.put("series_exclFirst", RV.i(0));
        rsa0.put("series_exclLast", RV.i(0));
        rsa0.put("spec", RV.s("RSA0"));
        rsa0.put("preliminary.check", RV.b(true));
        rsa0.put("estimate.from", RV.naChr());
        rsa0.put("estimate.to", RV.naChr());
        rsa0.put("estimate.first", RV.naInt());
        rsa0.put("estimate.last", RV.naInt());
        rsa0.put("estimate.exclFirst", RV.i(0));
        rsa0.put("estimate.exclLast", RV.i(0));
        rsa0.put("estimate.tol", RV.d(1e-07));
        rsa0.put("estimate.eml", RV.b(true));
        rsa0.put("estimate.urfinal", RV.d(0.96));
        rsa0.put("transform.function", RV.s("None"));
        rsa0.put("transform.fct", RV.d(0.95));
        rsa0.put("usrdef.outliersEnabled", RV.b(false));
        rsa0.put("usrdef.outliersType", RV.naLgl());
        rsa0.put("usrdef.outliersDate", RV.naLgl());
        rsa0.put("usrdef.outliersCoef", RV.naLgl());
        rsa0.put("userdef.varFromFile", RV.b(false));
        rsa0.put("userdef.varFromFile.infoList", RV.NULL);
        rsa0.put("usrdef.varEnabled", RV.b(false));
        rsa0.put("usrdef.var", RV.naLgl());
        rsa0.put("usrdef.varType", RV.naLgl());
        rsa0.put("usrdef.varCoef", RV.naLgl());
        rsa0.put("tradingdays.mauto", RV.s("Unused"));
        rsa0.put("tradingdays.pftd", RV.d(0.01));
        rsa0.put("tradingdays.option", RV.s("None"));
        rsa0.put("tradingdays.leapyear", RV.b(false));
        rsa0.put("tradingdays.stocktd", RV.i(0));
        rsa0.put("tradingdays.test", RV.s("None"));
        rsa0.put("easter.type", RV.s("Unused"));
        rsa0.put("easter.julian", RV.b(false));
        rsa0.put("easter.duration", RV.i(6));
        rsa0.put("easter.test", RV.b(false));
        rsa0.put("outlier.enabled", RV.b(false));
        rsa0.put("outlier.from", RV.naChr());
        rsa0.put("outlier.to", RV.naChr());
        rsa0.put("outlier.first", RV.naInt());
        rsa0.put("outlier.last", RV.naInt());
        rsa0.put("outlier.exclFirst", RV.i(0));
        rsa0.put("outlier.exclLast", RV.i(0));
        rsa0.put("outlier.ao", RV.b(false));
        rsa0.put("outlier.tc", RV.b(false));
        rsa0.put("outlier.ls", RV.b(false));
        rsa0.put("outlier.so", RV.b(false));
        rsa0.put("outlier.usedefcv", RV.b(true));
        rsa0.put("outlier.cv", RV.d(3.5));
        rsa0.put("outlier.eml", RV.b(false));
        rsa0.put("outlier.tcrate", RV.d(0.7));
        rsa0.put("automdl.enabled", RV.b(false));
        rsa0.put("automdl.acceptdefault", RV.b(false));
        rsa0.put("automdl.cancel", RV.d(0.05));
        rsa0.put("automdl.ub1", RV.d(0.97));
        rsa0.put("automdl.ub2", RV.d(0.91));
        rsa0.put("automdl.armalimit", RV.d(1));
        rsa0.put("automdl.reducecv", RV.d(0.12));
        rsa0.put("automdl.ljungboxlimit", RV.d(0.95));
        rsa0.put("automdl.compare", RV.b(false));
        rsa0.put("arima.mu", RV.b(true));
        rsa0.put("arima.muFixedValue", RV.naDbl());
        rsa0.put("arima.p", RV.i(0));
        rsa0.put("arima.d", RV.i(1));
        rsa0.put("arima.q", RV.i(1));
        rsa0.put("arima.bp", RV.i(0));
        rsa0.put("arima.bd", RV.i(1));
        rsa0.put("arima.bq", RV.i(1));
        rsa0.put("arima.coefEnabled", RV.b(false));
        rsa0.put("arima.coef", RV.naLgl());
        rsa0.put("arima.coefType", RV.naLgl());
        rsa0.put("fcst.horizon", RV.d(-2));
        rsa0.put("seats.predictionLength", RV.i(-1));
        rsa0.put("seats.approx", RV.s("Legacy"));
        rsa0.put("seats.trendBoundary", RV.d(0.5));
        rsa0.put("seats.seasdBoundary", RV.d(0.8));
        rsa0.put("seats.seasdBoundary1", RV.d(0.8));
        rsa0.put("seats.seasTol", RV.d(2));
        rsa0.put("seats.maBoundary", RV.d(0.95));
        rsa0.put("seats.method", RV.s("Burman"));
        rsa0.put("ramps", RV.naLgl());
        rsa0.put("intervention_variables", RV.naLgl());
        rsa0.put("easterCoef", RV.d(0));
        DEFAULTS.put("RSA0", rsa0);
        DEFAULTS.put("TS", rsa0);   // come in R: "TS" (specifica personalizzata) ha come base RSA0

        Map<String, RV> rsa1 = new LinkedHashMap<>(rsa0);
        rsa1.put("spec", RV.s("RSA1"));
        rsa1.put("transform.function", RV.s("Auto"));
        rsa1.put("outlier.enabled", RV.b(true));
        rsa1.put("outlier.ao", RV.b(true));
        rsa1.put("outlier.tc", RV.b(true));
        rsa1.put("outlier.ls", RV.b(true));
        DEFAULTS.put("RSA1", rsa1);

        Map<String, RV> rsa2 = new LinkedHashMap<>(rsa0);
        rsa2.put("spec", RV.s("RSA2"));
        rsa2.put("transform.function", RV.s("Auto"));
        rsa2.put("tradingdays.option", RV.s("WorkingDays"));
        rsa2.put("tradingdays.leapyear", RV.b(true));
        rsa2.put("tradingdays.test", RV.s("Separate_T"));
        rsa2.put("easter.type", RV.s("Standard"));
        rsa2.put("easter.test", RV.b(true));
        rsa2.put("outlier.enabled", RV.b(true));
        rsa2.put("outlier.ao", RV.b(true));
        rsa2.put("outlier.tc", RV.b(true));
        rsa2.put("outlier.ls", RV.b(true));
        DEFAULTS.put("RSA2", rsa2);

        Map<String, RV> rsa3 = new LinkedHashMap<>(rsa0);
        rsa3.put("spec", RV.s("RSA3"));
        rsa3.put("transform.function", RV.s("Auto"));
        rsa3.put("outlier.enabled", RV.b(true));
        rsa3.put("outlier.ao", RV.b(true));
        rsa3.put("outlier.tc", RV.b(true));
        rsa3.put("outlier.ls", RV.b(true));
        rsa3.put("automdl.enabled", RV.b(true));
        DEFAULTS.put("RSA3", rsa3);

        Map<String, RV> rsa4 = new LinkedHashMap<>(rsa0);
        rsa4.put("spec", RV.s("RSA4"));
        rsa4.put("transform.function", RV.s("Auto"));
        rsa4.put("tradingdays.option", RV.s("WorkingDays"));
        rsa4.put("tradingdays.leapyear", RV.b(true));
        rsa4.put("tradingdays.test", RV.s("Separate_T"));
        rsa4.put("easter.type", RV.s("Standard"));
        rsa4.put("easter.test", RV.b(true));
        rsa4.put("outlier.enabled", RV.b(true));
        rsa4.put("outlier.ao", RV.b(true));
        rsa4.put("outlier.tc", RV.b(true));
        rsa4.put("outlier.ls", RV.b(true));
        rsa4.put("automdl.enabled", RV.b(true));
        DEFAULTS.put("RSA4", rsa4);

        Map<String, RV> rsa5 = new LinkedHashMap<>(rsa0);
        rsa5.put("spec", RV.s("RSA5"));
        rsa5.put("transform.function", RV.s("Auto"));
        rsa5.put("tradingdays.option", RV.s("TradingDays"));
        rsa5.put("tradingdays.leapyear", RV.b(true));
        rsa5.put("tradingdays.test", RV.s("Separate_T"));
        rsa5.put("easter.type", RV.s("Standard"));
        rsa5.put("easter.test", RV.b(true));
        rsa5.put("outlier.enabled", RV.b(true));
        rsa5.put("outlier.ao", RV.b(true));
        rsa5.put("outlier.tc", RV.b(true));
        rsa5.put("outlier.ls", RV.b(true));
        rsa5.put("automdl.enabled", RV.b(true));
        DEFAULTS.put("RSA5", rsa5);

        Map<String, RV> rsafull = new LinkedHashMap<>(rsa0);
        rsafull.put("spec", RV.s("RSAfull"));
        rsafull.put("transform.function", RV.s("Auto"));
        rsafull.put("tradingdays.mauto", RV.s("FTest"));
        rsafull.put("tradingdays.option", RV.s("TradingDays"));
        rsafull.put("tradingdays.leapyear", RV.b(true));
        rsafull.put("tradingdays.test", RV.s("Separate_T"));
        rsafull.put("easter.type", RV.s("IncludeEaster"));
        rsafull.put("easter.test", RV.b(true));
        rsafull.put("outlier.enabled", RV.b(true));
        rsafull.put("outlier.ao", RV.b(true));
        rsafull.put("outlier.tc", RV.b(true));
        rsafull.put("outlier.ls", RV.b(true));
        rsafull.put("automdl.enabled", RV.b(true));
        DEFAULTS.put("RSAfull", rsafull);
    }

    private static Map<String, RV> defaultsOf(String spec, String seriesName) {
        Map<String, RV> d = DEFAULTS.get(spec);
        if (d == null) {
            throw new IllegalArgumentException("Serie " + seriesName + ": specifica di base sconosciuta '" + spec
                    + "' (attese RSA0..RSA5, RSAfull)");
        }
        return d;
    }

    /**
     * Costruttore di Extended_tramoseats_spec: i campi non specificati (tutti NA, o assenti) prendono
     * il valore della specifica di base. Restituisce tutti gli slot, nell'ordine della classe R.
     */
    static Map<String, RV> construct(RV seriesName, RV frequency, String method, String spec, Map<String, RV> attrs) {
        Map<String, RV> d = defaultsOf(spec, String.valueOf(seriesName.values.isEmpty() ? null : seriesName.values.get(0)));
        Map<String, RV> obj = new LinkedHashMap<>();
        for (String slot : SLOTS) {
            switch (slot) {
                case "series_name": obj.put(slot, seriesName); break;
                case "frequency":   obj.put(slot, frequency); break;
                case "method":      obj.put(slot, RV.s(method)); break;
                case "spec":        obj.put(slot, RV.s(spec)); break;
                default:
                    RV v = attrs.get(slot);
                    obj.put(slot, (v == null || v.allNA()) ? d.get(slot) : v);
            }
        }
        return obj;
    }

    // =====================================================================================
    //  Lettura del workspace
    // =====================================================================================

    /** Informazioni sui regressori di tutte le serie (read_ext_reg_info del lettore TS+). */
    private static final class ExtRegInfo {
        final Map<String, RV> extVars = new LinkedHashMap<>();   // lista di {container, start, n_var}
        final Map<String, List<RV>> ramps = new LinkedHashMap<>();
        final Map<String, List<RV>> ivs = new LinkedHashMap<>();
        final Map<String, List<Double>> varCoef = new LinkedHashMap<>();
    }

    /** Una serie del workspace: nome, dati e specifiche. */
    private static final class Item {
        final String name;
        final SaItemType def;
        final TramoSeatsSpecification domain;
        final TsData y;

        Item(String name, SaItemType def, TramoSeatsSpecification domain, TsData y) {
            this.name = name;
            this.def = def;
            this.domain = domain;
            this.y = y;
        }
    }

    /** Risultato dell'estrazione. */
    public static final class Result {
        public final List<String> seriesNames = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public String reduced;   // testo del JSON ridotto (righe separate da \n)
        public String full;      // testo del JSON completo (righe separate da \n)
    }

    private final String regressorDir;     // cartella dei regressori TS+ (facoltativa, solo per il controllo dei file)
    private final boolean adjustPath;      // come adjust_path = TRUE in R: toglie il percorso dal nome del file

    public WorkspaceToJson(String regressorDir) {
        this(regressorDir, true);
    }

    public WorkspaceToJson(String regressorDir, boolean adjustPath) {
        this.regressorDir = (regressorDir == null || regressorDir.trim().isEmpty()) ? null : regressorDir;
        this.adjustPath = adjustPath;
    }

    /** Estrae il JSON ridotto e quello completo dal workspace (file .xml). */
    public Result extract(String workspaceFile) throws IOException {
        Workspace ws = Workspace.open(workspaceFile);
        if (ws == null) {
            throw new IOException("Impossibile aprire il workspace: " + workspaceFile);
        }
        Result res = new Result();
        if (ws.getMultiProcessingCount() == 0) {
            throw new IOException("Il workspace non contiene elaborazioni (SAProcessing)");
        }
        if (ws.getMultiProcessingCount() > 1) {
            // come R (m[[1]]): si usa solo la prima elaborazione del workspace
            res.warnings.add("Il workspace contiene " + ws.getMultiProcessingCount() + " elaborazioni (SAProcessing):"
                    + " come in R viene letta solo la prima");
        }
        ProcessingContext context = ws.getContext();

        // serie della prima elaborazione, con il nome come in RJDemetra (get_name)
        MultiProcessing mp = ws.getMultiProcessing(0);
        List<Item> items = new ArrayList<>();
        Map<String, Item> firstByName = new LinkedHashMap<>();
        for (int k = 0; k < mp.size(); k++) {
            SaItemType def = mp.get(k).getSaDefinition();
            String name = def.getTs() == null ? null : def.getTs().getName();
            name = name == null ? "" : name.replaceFirst("(?s)^.*\n", "");
            ISaSpecification spec = def.getDomainSpec();
            TsData y = def.getTs() == null ? null : def.getTs().getData();
            if (!(spec instanceof TramoSeatsSpecification)) {
                res.warnings.add("Serie " + name + ": specifica non TRAMO-SEATS, ignorata");
                continue;
            }
            if (y == null) {
                res.warnings.add("Serie " + name + ": nessun dato, ignorata");
                continue;
            }
            Item it = new Item(name, def, (TramoSeatsSpecification) spec, y);
            if (firstByName.containsKey(name)) {
                // in R la lista per nome restituisce sempre la prima serie con quel nome
                res.warnings.add("Serie " + name + " presente piu' volte: come in R viene usata la prima");
                it = firstByName.get(name);
            } else {
                firstByName.put(name, it);
            }
            items.add(it);
        }
        if (items.isEmpty()) {
            throw new IOException("Nessuna serie TRAMO-SEATS nel workspace");
        }

        ExtRegInfo info = readExtRegInfo(items, context, res.warnings);
        checkRegressorFiles(info, res.warnings);

        StringBuilder reduced = new StringBuilder("[\n");
        StringBuilder full = new StringBuilder("[\n");
        for (int k = 0; k < items.size(); k++) {
            Item it = items.get(k);
            Map<String, RV> obj = fromSaSpec(it, info);
            res.seriesNames.add(it.name);

            Map<String, RV> red = reducedOf(obj);
            reduced.append(indent(toJson(red)));
            full.append(indent(toJson(fullOf(red))));
            if (k < items.size() - 1) {
                reduced.append(",\n\n\n");
                full.append(",\n\n\n");
            }
        }
        reduced.append("\n]").append("\n");
        full.append("\n]").append("\n");
        res.reduced = reduced.toString();
        res.full = full.toString();
        return res;
    }

    /** Scrive i file come R (fine riga del sistema operativo, UTF-8). */
    public static void writeFile(String text, String fileName) throws IOException {
        File f = new File(fileName);
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Impossibile creare la cartella " + dir);
        }
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
            w.write(text.replace("\n", System.lineSeparator()));
        }
    }

    // ------------------------------------------------------------------ JSON ridotto e completo

    /** difference_objects_preserving_name_and_spec(): campi diversi dalla specifica di base. */
    private static Map<String, RV> reducedOf(Map<String, RV> obj) {
        String spec = (String) obj.get("spec").values.get(0);
        Map<String, RV> basic = basicInstance(spec);
        Map<String, RV> out = new LinkedHashMap<>();
        for (String h : HEADER) {
            out.put(h, obj.get(h));
        }
        for (String slot : SLOTS) {
            if (HEADER.contains(slot)) continue;
            RV v = obj.get(slot);
            if (v.kind == RV.Kind.NULL) continue;           // in R assegnare NULL a una lista non aggiunge nulla
            if (!RV.identical(v, basic.get(slot))) {
                out.put(slot, v);
            }
        }
        return out;
    }

    /** from_reduced_to_full_JD_JSON_file(): campi mancanti presi dalla specifica di base. */
    private static Map<String, RV> fullOf(Map<String, RV> reduced) {
        RV specRv = reduced.get("spec");
        String spec = specRv == null || specRv.allNA() ? "RSA0" : (String) specRv.values.get(0);
        RV method = reduced.get("method");
        Map<String, RV> attrs = new HashMap<>();
        for (Map.Entry<String, RV> e : reduced.entrySet()) {
            attrs.put(e.getKey(), afterRoundTrip(e.getValue()));
        }
        Map<String, RV> obj = construct(reduced.get("series_name"),
                reduced.containsKey("frequency") ? reduced.get("frequency") : RV.naInt(),
                method == null || method.allNA() ? "TS" : (String) method.values.get(0), spec, attrs);
        // to_named_list(): gli slot NULL non compaiono
        Map<String, RV> out = new LinkedHashMap<>();
        for (Map.Entry<String, RV> e : obj.entrySet()) {
            if (e.getValue().kind != RV.Kind.NULL) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Valore come risulta dopo la scrittura del JSON ridotto e la sua rilettura con rjson::fromJSON
     * (from_reduced_to_full_JD_JSON_file rilegge il file): un vettore JSON con un solo numero o
     * una sola stringa diventa un valore semplice (es. [0.6] -> 0.6); i vettori di oggetti restano liste.
     */
    static RV afterRoundTrip(RV v) {
        if (v.kind != RV.Kind.LIST) return v;
        if (v.names == null && v.length() == 1) {
            RV e = (RV) v.values.get(0);
            if (e.kind != RV.Kind.LIST && e.kind != RV.Kind.NULL && e.length() == 1) return e;
        }
        List<Object> vals = new ArrayList<>();
        for (Object o : v.values) vals.add(afterRoundTrip((RV) o));
        return new RV(RV.Kind.LIST, vals, v.names);
    }

    private static String toJson(Map<String, RV> fields) {
        List<RV> vals = new ArrayList<>();
        List<Object> kv = new ArrayList<>();
        for (Map.Entry<String, RV> e : fields.entrySet()) {
            kv.add(e.getKey());
            kv.add(e.getValue());
        }
        return RV.named(kv.toArray()).toJson();
    }

    /** Stessa formattazione di to_JD_JSON(..., indent = TRUE) in R (stesse sostituzioni, nello stesso ordine). */
    static String indent(String json) {
        String s = json;
        s = s.replaceAll("\\{\\s*\"", "{\n\"");
        s = s.replaceAll("\",\\s*", "\",\n");
        s = s.replaceAll("([0-9]+|true|false),", "$1,\n");
        s = s.replaceAll("\\],", "],\n");
        s = s.replaceAll("\\}(?!\\])", "\n}");
        // in R ",([^\\r\\n])" usa la sintassi POSIX, dove nelle parentesi quadre il backslash e' un
        // carattere normale: la classe esclude '\', 'r' e 'n' (non gli a capo)
        s = s.replaceAll(",([^\\\\rn])", ", $1");
        // toglie gli a capo all'interno dei vettori [ ... ] che non contengono oggetti
        Matcher m = Pattern.compile("\\[[^\\[\\{\\}]*?\\]").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replaceAll("[\r\n]", "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------ specifica di base

    private static final Map<String, Map<String, RV>> BASIC_CACHE = new HashMap<>();

    /** from_SA_spec(tramoseats_spec(basic), userdef.varFromFile = FALSE): oggetto di confronto per il JSON ridotto. */
    static synchronized Map<String, RV> basicInstance(String spec) {
        Map<String, RV> b = BASIC_CACHE.get(spec);
        if (b != null) return b;
        TramoSeatsSpecification core = predefined(spec);
        Map<String, RV> a = commonAttributes(core);
        // campi che per la specifica di base non dipendono dal workspace
        a.put("usrdef.outliersEnabled", RV.b(false));
        a.put("usrdef.varEnabled", RV.b(false));
        a.put("userdef.varFromFile", RV.b(false));
        a.put("easterCoef", RV.d(0));
        ArimaSpec arima = core.getTramoSpecification().getArima();
        putArimaOrders(a, arima);
        a.put("arima.coefEnabled", RV.b(false));
        b = construct(RV.naChr(), RV.naInt(), "TS", spec, a);
        BASIC_CACHE.put(spec, b);
        return b;
    }

    private static TramoSeatsSpecification predefined(String spec) {
        switch (spec) {
            case "RSA0": return TramoSeatsSpecification.RSA0;
            case "RSA1": return TramoSeatsSpecification.RSA1;
            case "RSA2": return TramoSeatsSpecification.RSA2;
            case "RSA3": return TramoSeatsSpecification.RSA3;
            case "RSA4": return TramoSeatsSpecification.RSA4;
            case "RSA5": return TramoSeatsSpecification.RSA5;
            case "RSAfull": return TramoSeatsSpecification.RSAfull;
            default: throw new IllegalArgumentException("Specifica di base sconosciuta: " + spec);
        }
    }

    // ------------------------------------------------------------------ from_SA_spec per una serie

    /** Campi letti dalla specifica come in RJDemetra (spec_TRAMO_jd2r, specSeats_jd2r) e in from_SA_spec. */
    private static Map<String, RV> commonAttributes(TramoSeatsSpecification core) {
        TramoSeatsSpec js = new TramoSeatsSpec(core);
        Map<String, RV> a = new HashMap<>();

        a.put("preliminary.check", RV.b(js.getBasic().isPreliminaryCheck()));
        putSpan(a, "estimate", js.getEstimate().getSpan());
        a.put("estimate.tol", RV.d(js.getEstimate().getTol()));
        a.put("estimate.eml", RV.b(js.getEstimate().isEml()));
        a.put("estimate.urfinal", RV.d(js.getEstimate().getUbp()));
        a.put("transform.function", RV.s(js.getTransform().getFunction()));
        a.put("transform.fct", RV.d(js.getTransform().getFct()));

        jdr.spec.tramoseats.TradingDaysSpec td = js.getRegression().getCalendar().getTradingDays();
        a.put("tradingdays.mauto", RV.s(td.getAutomatic()));
        a.put("tradingdays.pftd", RV.d(td.getPftd()));
        String option = td.getOption();
        a.put("tradingdays.option", RV.s("UserDefined".equals(option) ? option : td.getTradingDays()));
        a.put("tradingdays.leapyear", RV.b(td.getLeapYear()));
        a.put("tradingdays.stocktd", RV.i(td.getW()));
        a.put("tradingdays.test", RV.s(td.getRegressionTestType()));

        jdr.spec.tramoseats.EasterSpec easter = js.getRegression().getCalendar().getEaster();
        a.put("easter.type", RV.s(easter.getOption()));
        a.put("easter.julian", RV.b(easter.isJulian()));
        a.put("easter.duration", RV.i(easter.getDuration()));
        a.put("easter.test", RV.b(easter.isTest()));

        jdr.spec.tramoseats.OutlierSpec out = js.getOutlier();
        a.put("outlier.enabled", RV.b(out.isOutliersDetectionEnabled()));
        putSpan(a, "outlier", out.getSpan());
        a.put("outlier.ao", RV.b(out.isAO()));
        a.put("outlier.tc", RV.b(out.isTC()));
        a.put("outlier.ls", RV.b(out.isLS()));
        a.put("outlier.so", RV.b(out.isSO()));
        a.put("outlier.usedefcv", RV.b(out.isAutoVa()));
        a.put("outlier.cv", RV.d(out.getVa()));
        a.put("outlier.eml", RV.b(out.isEML()));
        a.put("outlier.tcrate", RV.d(out.getTCRate()));

        jdr.spec.tramoseats.ArimaSpec ar = js.getArima();
        a.put("automdl.enabled", RV.b(ar.isEnabled()));
        a.put("automdl.acceptdefault", RV.b(ar.isAcceptDefault()));
        a.put("automdl.cancel", RV.d(ar.getCancel()));
        a.put("automdl.ub1", RV.d(ar.getUb1()));
        a.put("automdl.ub2", RV.d(ar.getUb2()));
        a.put("automdl.armalimit", RV.d(ar.getTsig()));
        a.put("automdl.reducecv", RV.d(ar.getPc()));
        a.put("automdl.ljungboxlimit", RV.d(ar.getPcr()));
        a.put("automdl.compare", RV.b(ar.isAmiCompare()));
        a.put("arima.mu", RV.b(ar.isMean()));

        a.put("fcst.horizon", RV.d(-2));   // fisso in RJDemetra (regarima_defTS)

        jdr.spec.tramoseats.SeatsSpec seats = js.getSeats();
        a.put("seats.predictionLength", RV.i(seats.getPredictionLength()));
        a.put("seats.approx", RV.s(seats.getApproximationMode()));
        a.put("seats.trendBoundary", RV.d(seats.getRMod()));
        a.put("seats.seasdBoundary", RV.d(seats.getSMod()));
        a.put("seats.seasdBoundary1", RV.d(seats.getSMod1()));
        a.put("seats.seasTol", RV.d(seats.getEpsPhi()));
        a.put("seats.maBoundary", RV.d(seats.getXl()));
        a.put("seats.method", RV.s(seats.getMethod()));
        return a;
    }

    /** jd_span() di RJDemetra seguita da span_unpack_into_spec() di RJDProcessor. */
    private static void putSpan(Map<String, RV> a, String prefix, SpanSelector span) {
        String type = span.getType();
        switch (type) {
            case "All":
                a.put(prefix + ".exclFirst", RV.i(0));
                a.put(prefix + ".exclLast", RV.i(0));
                break;
            case "From":
                a.put(prefix + ".from", RV.s(span.getD0()));
                break;
            case "To":
                a.put(prefix + ".to", RV.s(span.getD1()));
                break;
            case "Between":
                a.put(prefix + ".from", RV.s(span.getD0()));
                a.put(prefix + ".to", RV.s(span.getD1()));
                break;
            case "First":
                a.put(prefix + ".first", RV.i(span.getN0()));
                break;
            case "Last":
                a.put(prefix + ".last", RV.i(span.getN1()));
                break;
            case "Excluding":
                a.put(prefix + ".exclFirst", RV.i(span.getN0()));
                a.put(prefix + ".exclLast", RV.i(span.getN1()));
                break;
            default:
                throw new IllegalArgumentException("Span di tipo " + type + " non gestito (" + prefix + ")");
        }
    }

    private static void putArimaOrders(Map<String, RV> a, ArimaSpec arima) {
        a.put("arima.p", RV.i(arima.getP()));
        a.put("arima.d", RV.i(arima.getD()));
        a.put("arima.q", RV.i(arima.getQ()));
        a.put("arima.bp", RV.i(arima.getBP()));
        a.put("arima.bd", RV.i(arima.getBD()));
        a.put("arima.bq", RV.i(arima.getBQ()));
    }

    /** from_SA_spec() per una serie del workspace. */
    private Map<String, RV> fromSaSpec(Item it, ExtRegInfo info) {
        TramoSeatsSpecification core = it.domain;
        TramoSeatsSpec js = new TramoSeatsSpec(core);
        RegressionSpec reg = core.getTramoSpecification().getRegression();
        Map<String, RV> a = commonAttributes(core);

        // specifica di base: toString() vale RSA0..RSAfull solo per le specifiche predefinite, altrimenti "TS"
        String basic = core.toString();
        String spec = "TS".equals(basic) ? "RSA0" : basic;

        // span della serie (Basic > Series span)
        SpanSelector span = js.getBasic().getSpan();
        String st = span.getType();
        if ("Between".equals(st)) {
            a.put("series_start", RV.s(span.getD0()));
            a.put("series_end", RV.s(span.getD1()));
        } else if ("From".equals(st)) {
            a.put("series_start", RV.s(span.getD0()));
        } else if ("To".equals(st)) {
            a.put("series_end", RV.s(span.getD1()));
        } else if ("First".equals(st)) {
            a.put("series_first", RV.i(span.getN0()));
        } else if ("Last".equals(st)) {
            a.put("series_last", RV.i(span.getN1()));
        } else if ("Excluding".equals(st)) {
            a.put("series_exclFirst", RV.i(span.getN0()));
            a.put("series_exclLast", RV.i(span.getN1()));
        }

        // outlier definiti dall'utente (get_outliers_info): coefficienti tutti 0 = non fissati = NA
        jdr.spec.tramoseats.RegressionSpec jreg = js.getRegression();
        int nOut = jreg.getPrespecifiedOutliersCount();
        a.put("usrdef.outliersEnabled", RV.b(nOut > 0));
        if (nOut > 0) {
            List<String> types = new ArrayList<>(), dates = new ArrayList<>();
            List<Double> coefs = new ArrayList<>();
            boolean allZero = true;
            for (int k = 0; k < nOut; k++) {
                jdr.spec.ts.Utility.Outlier o = jreg.getPrespecifiedOutlier(k);
                types.add(o.getCode());
                dates.add(o.getPosition());
                coefs.add(o.getCoefficient());
                if (o.getCoefficient() != 0) allZero = false;
            }
            a.put("usrdef.outliersType", RV.chrVec(types));
            a.put("usrdef.outliersDate", RV.chrVec(dates));
            a.put("usrdef.outliersCoef", allZero ? RV.naLgl() : RV.dblVec(coefs));
        }

        // variabili definite dall'utente e regressori di calendario (get_user_def_var_info).
        // Le serie con rampe passano in R dal modello Java, dove le rampe vengono tolte dalla specifica:
        // quindi qui le rampe non contano mai tra le variabili.
        int nUsr = jreg.getUserDefinedVariablesCount();
        String[] tdVars = jreg.getCalendar().getTradingDays().getUserVariables();
        int nTd = tdVars == null ? 0 : tdVars.length;
        boolean variables = nUsr > 0 || nTd > 0;
        a.put("usrdef.varEnabled", RV.b(variables));

        RV infoList = info.extVars.get(it.name);
        boolean varFromFile = infoList != null;   // in R: dati dei regressori letti (vars_mts) per la serie
        a.put("userdef.varFromFile", RV.b(varFromFile));
        a.put("userdef.varFromFile.infoList", infoList == null ? RV.NULL : infoList);
        if (variables) {
            List<String> types = new ArrayList<>();
            for (int k = 0; k < nUsr; k++) {
                types.add(jreg.getUserDefinedVariable(k).getComponent());
            }
            for (int k = 0; k < nTd; k++) {
                types.add("Calendar");
            }
            a.put("usrdef.varType", RV.chrVec(types));
        }
        List<Double> coefList = info.varCoef.get(it.name);
        boolean allZero = true;
        if (coefList != null) {
            for (Double c : coefList) if (c != 0) allZero = false;
        }
        if (coefList != null && !allZero) {
            List<RV> l = new ArrayList<>();
            for (Double c : coefList) l.add(RV.d(c));
            a.put("usrdef.varCoef", RV.list(l));
        }

        // ARIMA (get_arima_info_from_java): dalla specifica Point se il modello automatico e' spento
        ArimaSpec arima = core.getTramoSpecification().getArima();
        if (!core.getTramoSpecification().getAutoModel().isEnabled()) {
            ISaSpecification point = it.def.getPointSpec();
            if (point instanceof TramoSeatsSpecification) {
                arima = ((TramoSeatsSpecification) point).getTramoSpecification().getArima();
            }
        }
        putArimaOrders(a, arima);
        List<Double> coef = new ArrayList<>();
        List<String> coefType = new ArrayList<>();
        readParams(arima.getPhi(), arima.getP(), coef, coefType);
        readParams(arima.getTheta(), arima.getQ(), coef, coefType);
        readParams(arima.getBPhi(), arima.getBP(), coef, coefType);
        readParams(arima.getBTheta(), arima.getBQ(), coef, coefType);
        boolean coefEnabled = false;
        for (String t : coefType) if (!"Undefined".equals(t)) coefEnabled = true;
        a.put("arima.coefEnabled", RV.b(coefEnabled));
        if (coefEnabled) {
            a.put("arima.coef", RV.dblVec(coef));
            a.put("arima.coefType", RV.chrVec(coefType));
        }
        Parameter mu = arima.getMu();
        if (mu != null && mu.getType() == ParameterType.Fixed) {
            a.put("arima.muFixedValue", RV.d(mu.getValue()));
        }

        // coefficienti fissati: Pasqua e rampe
        double easterCoef = 0;
        if (reg.hasFixedCoefficients()) {
            double[] ec = reg.getFixedCoefficients("easter");
            if (ec != null && ec.length > 0 && !Double.isNaN(ec[0])) {
                easterCoef = ec[0];
            }
        }
        a.put("easterCoef", RV.d(easterCoef));

        // rampe: in R le serie con rampe passano dal modello Java, dove a ogni rampa si aggiunge
        // fixed_coef (0 = non fissato) solo se la serie ha qualche coefficiente fissato
        List<RV> ramps = info.ramps.get(it.name);
        if (ramps != null && !ramps.isEmpty()) {
            List<RV> out = new ArrayList<>(ramps);
            if (reg.hasFixedCoefficients()) {
                for (int k = 0; k < reg.getRampsCount(); k++) {
                    Ramp r = reg.getRamp(k);
                    double[] fc = reg.getFixedCoefficients(r.getName());
                    String s0 = r.getStart().toString(), s1 = r.getEnd().toString();
                    for (int j = 0; j < out.size(); j++) {
                        RV ramp = out.get(j);
                        String rs = (String) ((RV) ramp.values.get(0)).values.get(0);
                        String re = (String) ((RV) ramp.values.get(1)).values.get(0);
                        if (s0.equals(rs) && s1.equals(re)) {
                            out.set(j, ramp.with("fixed_coef", fc == null ? RV.d(0) : coefRV(fc)));
                        }
                    }
                }
            }
            a.put("ramps", RV.list(out));
        }
        List<RV> ivs = info.ivs.get(it.name);
        if (ivs != null && !ivs.isEmpty()) {
            a.put("intervention_variables", RV.list(ivs));
        }

        return construct(RV.s(it.name), RV.i(it.y.getFrequency().intValue()), "TS", spec, a);
    }

    private static RV coefRV(double[] v) {
        if (v.length == 1) return RV.d(v[0]);
        List<Double> l = new ArrayList<>();
        for (double x : v) l.add(x);
        return RV.dblVec(l);
    }

    /** read_params(): Fixed / Initial -> valore e tipo; altrimenti NA e "Undefined". */
    private static void readParams(Parameter[] params, int order, List<Double> values, List<String> types) {
        for (int k = 0; k < order; k++) {
            Double v = null;
            String t = "Undefined";
            if (params != null && k < params.length && params[k] != null) {
                ParameterType pt = params[k].getType();
                if (pt == ParameterType.Fixed || pt == ParameterType.Initial) {
                    v = params[k].getValue();
                    t = pt.toString();
                }
            }
            values.add(v);
            types.add(t);
        }
    }

    // ------------------------------------------------------------------ regressori (lettore TS+)

    /** getUserDefinedTdVariables_info_tsplus() di Data_reader_ext_reg_tsplus.R. */
    private ExtRegInfo readExtRegInfo(List<Item> items, ProcessingContext context, List<String> warnings) {
        ExtRegInfo info = new ExtRegInfo();
        Set<String> done = new HashSet<>();
        for (Item it : items) {
            if (!done.add(it.name)) continue;
            TramoSeatsSpec js = new TramoSeatsSpec(it.domain);
            jdr.spec.tramoseats.RegressionSpec jreg = js.getRegression();
            String[] tdVars = jreg.getCalendar().getTradingDays().getUserVariables();
            List<RV> files = new ArrayList<>();

            int nUsr = jreg.getUserDefinedVariablesCount();
            RV previous = null;
            for (int k = 0; k < nUsr; k++) {
                String varString = jreg.getUserDefinedVariable(k).getName();
                RV current = RV.named("container", RV.s(fileNameOf(varString)),
                        "start", RV.s(regressorStartDate(context, varString, it, warnings)), "n_var", RV.d(1));
                if (previous == null || !RV.identical(current, previous)) {
                    files.add(current);
                    previous = current;
                }
            }
            if (tdVars != null && tdVars.length > 0) {
                RV[] counts = repeatCounts(tdVars);
                previous = null;
                for (int k = 0; k < tdVars.length; k++) {
                    String varString = tdVars[k];
                    RV current = RV.named("container", RV.s(fileNameOf(varString)),
                            "start", RV.s(regressorStartDate(context, varString, it, warnings)), "n_var", counts[k]);
                    if (previous == null || !RV.identical(current, previous)) {
                        files.add(current);
                        previous = current;
                    }
                }
            }
            if (!files.isEmpty()) {
                info.extVars.put(it.name, RV.list(files));
            }

            RegressionSpec reg = it.domain.getTramoSpecification().getRegression();
            List<RV> ramps = new ArrayList<>();
            for (int k = 0; k < reg.getRampsCount(); k++) {
                Ramp r = reg.getRamp(k);
                ramps.add(RV.named("start", RV.s(r.getStart().toString()), "end", RV.s(r.getEnd().toString())));
            }
            info.ramps.put(it.name, ramps);

            List<RV> ivs = new ArrayList<>();
            for (int k = 0; k < reg.getInterventionVariablesCount(); k++) {
                InterventionVariable iv = reg.getInterventionVariable(k);
                List<RV> seqs = new ArrayList<>();
                Sequence[] sq = iv.getSequences();
                for (int j = 0; j < sq.length; j++) {
                    seqs.add(RV.named("start", RV.s(sq[j].start.toString()), "end", RV.s(sq[j].end.toString())));
                }
                ivs.add(RV.named("delta", RV.d(iv.getDelta()), "delta_s", RV.d(iv.getDeltaS()),
                        "seq", RV.list(seqs), "D1DS", RV.b(iv.getD1DS())));
            }
            info.ivs.put(it.name, ivs);

            // coefficienti fissati dei regressori esterni: nomi con "@", quelli di calendario (td|) in fondo
            Set<String> fixedNames = new HashSet<>();
            Matcher m = Pattern.compile("[^\\s,]+@[^\\s,]+(?==\\[)").matcher(String.valueOf(reg.getAllFixedCoefficients()));
            while (m.find()) {
                fixedNames.add(m.group().replaceFirst("^\\{", ""));
            }
            List<String> names = new ArrayList<>(), tdNames = new ArrayList<>();
            for (String n : reg.getRegressionVariableNames(it.y.getFrequency())) {
                if (!n.contains("@")) continue;
                (n.startsWith("td|") ? tdNames : names).add(n);
            }
            names.addAll(tdNames);
            List<Double> coefs = new ArrayList<>();
            for (String n : names) {
                if (fixedNames.contains(n)) {
                    double[] c = reg.getFixedCoefficients(n);
                    if (c != null) for (double x : c) coefs.add(x);
                } else {
                    coefs.add(0.0);
                }
            }
            info.varCoef.put(it.name, coefs);
        }
        return info;
    }

    /** Nome del file del regressore (blocco "Designed over specific regressors presents in ISTAT"). */
    String fileNameOf(String varString) {
        String fileName;
        if (Pattern.compile("lym_", Pattern.CASE_INSENSITIVE).matcher(varString).find()) {
            fileName = varString.replaceFirst("^[^.]*\\.", "").toLowerCase(Locale.ROOT);
            fileName = fileName.replaceFirst("^(.*lym_[0-9]+).*", "$1");
        } else if (varString.contains("q3_00_04")) {
            fileName = "q3_00_04";
        } else if (Pattern.compile("^[^.]*\\.q[0-9]+_[0-9]+", Pattern.CASE_INSENSITIVE).matcher(varString).find()) {
            fileName = varString.replaceFirst("^[^.]*\\.", "").toLowerCase(Locale.ROOT);
            fileName = fileName.replaceFirst("^(.*q_[0-9]+).*", "$1");
        } else {
            fileName = varString.replaceFirst("^[^.]*\\.", "").toLowerCase(Locale.ROOT);
            fileName = fileName.replaceFirst("(.*?)_\\d+$", "$1").toLowerCase(Locale.ROOT);
        }
        fileName = fileName + ".txt";
        if (adjustPath) {
            fileName = fileName.replaceFirst("(?s).*[\\\\/]", "");
        }
        return fileName;
    }

    /** get_repeat_counts(): quante variabili condividono lo stesso prefisso (es. 6 variabili TD in un file). */
    private static RV[] repeatCounts(String[] vars) {
        RV[] out = new RV[vars.length];
        if (vars.length == 1) {
            out[0] = RV.d(1);
            return out;
        }
        String[] prefixes = new String[vars.length];
        Map<String, Integer> counts = new HashMap<>();
        for (int k = 0; k < vars.length; k++) {
            prefixes[k] = vars[k].replaceFirst("^td\\|", "").replaceFirst("(?s)^(.*)_\\d+$", "$1");
            counts.merge(prefixes[k], 1, Integer::sum);
        }
        for (int k = 0; k < vars.length; k++) {
            out[k] = RV.i(counts.get(prefixes[k]));
        }
        return out;
    }

    /** get_regressor_start_date(): data di inizio della variabile nel contesto del workspace ("AAAA-PP-01"). */
    private static String regressorStartDate(ProcessingContext context, String varName, Item it, List<String> warnings) {
        if (context != null) {
            ITsVariable v = null;
            try {
                v = context.getTsVariable(varName.replaceFirst("^td\\|", ""));
            } catch (RuntimeException ex) {
                v = null;
            }
            if (v != null) {
                TsDomain dom = v.getDefinitionDomain();
                if (dom != null) {
                    TsPeriod start = dom.getStart();
                    return String.format(Locale.ROOT, "%04d-%02d-01", start.getYear(), start.getPosition() + 1);
                }
            }
            warnings.add("Regressore " + varName + ": non trovato nelle variabili del workspace, come data di inizio uso quella della serie");
        }
        TsPeriod start = it.y.getStart();
        return String.format(Locale.ROOT, "%04d-%02d-01", start.getYear(), start.getPosition() + 1);
    }

    /** In R i file dei regressori vengono letti (read.table): qui si controlla solo che esistano. */
    private void checkRegressorFiles(ExtRegInfo info, List<String> warnings) {
        if (regressorDir == null) return;
        Set<String> missing = new TreeSet<>();
        for (RV files : info.extVars.values()) {
            for (Object o : files.values) {
                String container = (String) ((RV) ((RV) o).values.get(0)).values.get(0);
                if (!new File(regressorDir, container).isFile()) missing.add(container);
            }
        }
        if (!missing.isEmpty()) {
            warnings.add("File dei regressori non trovati in " + regressorDir + ": " + String.join(", ", missing)
                    + " (in R l'estrazione si fermerebbe con un errore)");
        }
    }

    // =====================================================================================
    //  Avvio: riga di comando o finestra
    // =====================================================================================

    public static void main(String[] args) {
        if (args.length == 0) {
            AppWorkspaceJson.main(args);   // senza argomenti: la finestra con le due schede
            return;
        }
        String ws = args[0];
        String regr = args.length > 1 ? args[1] : null;
        String outReduced = args.length > 2 ? args[2] : new File(new File(ws).getAbsoluteFile().getParentFile(), DEFAULT_REDUCED_FILE).getPath();
        String outFull = args.length > 3 ? args[3] : new File(new File(outReduced).getAbsoluteFile().getParentFile(), DEFAULT_FULL_FILE).getPath();
        try {
            Result r = new WorkspaceToJson(regr).extract(ws);
            writeFile(r.reduced, outReduced);
            writeFile(r.full, outFull);
            for (String w : r.warnings) System.err.println("ATTENZIONE: " + w);
            System.out.println(r.seriesNames.size() + " serie. JSON ridotto: " + outReduced + "  JSON completo: " + outFull);
        } catch (Exception ex) {
            System.err.println("ERRORE: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
