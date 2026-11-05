package src;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Valori di default delle specifiche di base (RSA0, RSA1, ..., RSAfull), uguali a quelli
 * usati dallo script R (basic_spec.R) per produrre il JSON ridotto.
 *
 * Il JSON ridotto contiene solo i campi diversi dalla specifica di base indicata in "spec":
 * complete() aggiunge i campi mancanti con il valore di default di quella specifica,
 * ricostruendo il JSON completo (come from_reduced_to_full_JD_JSON_file in R).
 * Un JSON gia' completo non viene modificato.
 *
 * NOTA: devono restare allineati a basic_spec.R. Unica differenza voluta: in RSA1
 * easter.type e' "Unused" (in basic_spec.R c'e' il refuso "Unised").
 *
 * @author alessandro.piovani@istat.it
 */
public final class BasicSpecDefaults {

    private static final Map<String, Map<String, Object>> DEFAULTS = new HashMap<>();

    static {
        Map<String, Object> rsa0 = new LinkedHashMap<>();
        rsa0.put("series_start", "NA");
        rsa0.put("series_end", "NA");
        rsa0.put("preliminary.check", true);
        rsa0.put("estimate.from", "NA");
        rsa0.put("estimate.to", "NA");
        rsa0.put("estimate.first", "NA");
        rsa0.put("estimate.last", "NA");
        rsa0.put("estimate.exclFirst", Integer.valueOf(0));
        rsa0.put("estimate.exclLast", Integer.valueOf(0));
        rsa0.put("estimate.tol", Double.valueOf(1e-07));
        rsa0.put("estimate.eml", true);
        rsa0.put("estimate.urfinal", Double.valueOf(0.96));
        rsa0.put("transform.function", "None");
        rsa0.put("transform.fct", Double.valueOf(0.95));
        rsa0.put("usrdef.outliersEnabled", false);
        rsa0.put("usrdef.outliersType", "NA");
        rsa0.put("usrdef.outliersDate", "NA");
        rsa0.put("usrdef.outliersCoef", "NA");
        rsa0.put("userdef.varFromFile", false);
        rsa0.put("usrdef.varEnabled", false);
        rsa0.put("usrdef.var", "NA");
        rsa0.put("usrdef.varType", "NA");
        rsa0.put("usrdef.varCoef", "NA");
        rsa0.put("tradingdays.mauto", "Unused");
        rsa0.put("tradingdays.pftd", Double.valueOf(0.01));
        rsa0.put("tradingdays.option", "None");
        rsa0.put("tradingdays.leapyear", false);
        rsa0.put("tradingdays.stocktd", Integer.valueOf(0));
        rsa0.put("tradingdays.test", "None");
        rsa0.put("easter.type", "Unused");
        rsa0.put("easter.julian", false);
        rsa0.put("easter.duration", Integer.valueOf(6));
        rsa0.put("easter.test", false);
        rsa0.put("outlier.enabled", false);
        rsa0.put("outlier.from", "NA");
        rsa0.put("outlier.to", "NA");
        rsa0.put("outlier.first", "NA");
        rsa0.put("outlier.last", "NA");
        rsa0.put("outlier.exclFirst", Integer.valueOf(0));
        rsa0.put("outlier.exclLast", Integer.valueOf(0));
        rsa0.put("outlier.ao", false);
        rsa0.put("outlier.tc", false);
        rsa0.put("outlier.ls", false);
        rsa0.put("outlier.so", false);
        rsa0.put("outlier.usedefcv", true);
        rsa0.put("outlier.cv", Double.valueOf(3.5));
        rsa0.put("outlier.eml", false);
        rsa0.put("outlier.tcrate", Double.valueOf(0.7));
        rsa0.put("automdl.enabled", false);
        rsa0.put("automdl.acceptdefault", false);
        rsa0.put("automdl.cancel", Double.valueOf(0.05));
        rsa0.put("automdl.ub1", Double.valueOf(0.97));
        rsa0.put("automdl.ub2", Double.valueOf(0.91));
        rsa0.put("automdl.armalimit", Integer.valueOf(1));
        rsa0.put("automdl.reducecv", Double.valueOf(0.12));
        rsa0.put("automdl.ljungboxlimit", Double.valueOf(0.95));
        rsa0.put("automdl.compare", false);
        rsa0.put("arima.mu", true);
        rsa0.put("arima.p", Integer.valueOf(0));
        rsa0.put("arima.d", Integer.valueOf(1));
        rsa0.put("arima.q", Integer.valueOf(1));
        rsa0.put("arima.bp", Integer.valueOf(0));
        rsa0.put("arima.bd", Integer.valueOf(1));
        rsa0.put("arima.bq", Integer.valueOf(1));
        rsa0.put("arima.coefEnabled", false);
        rsa0.put("arima.coef", "NA");
        rsa0.put("arima.coefType", "NA");
        rsa0.put("fcst.horizon", Integer.valueOf(-2));
        rsa0.put("seats.predictionLength", Integer.valueOf(-1));
        rsa0.put("seats.approx", "Legacy");
        rsa0.put("seats.trendBoundary", Double.valueOf(0.5));
        rsa0.put("seats.seasdBoundary", Double.valueOf(0.8));
        rsa0.put("seats.seasdBoundary1", Double.valueOf(0.8));
        rsa0.put("seats.seasTol", Integer.valueOf(2));
        rsa0.put("seats.maBoundary", Double.valueOf(0.95));
        rsa0.put("seats.method", "Burman");
        rsa0.put("ramps", "NA");
        rsa0.put("intervention_variables", "NA");
        rsa0.put("easterCoef", Integer.valueOf(0));
        // span della serie di tipo First/Last/Excluding (campi gestiti solo dal Java, per ora)
        rsa0.put("series_first", "NA");
        rsa0.put("series_last", "NA");
        rsa0.put("series_exclFirst", Integer.valueOf(0));
        rsa0.put("series_exclLast", Integer.valueOf(0));
        DEFAULTS.put("RSA0", rsa0);
        DEFAULTS.put("TS", rsa0);   // "TS" = specifica personalizzata: base RSA0, come in R

        Map<String, Object> rsa1 = new LinkedHashMap<>(rsa0);
        rsa1.put("transform.function", "Auto");
        rsa1.put("outlier.enabled", true);
        rsa1.put("outlier.ao", true);
        rsa1.put("outlier.tc", true);
        rsa1.put("outlier.ls", true);
        DEFAULTS.put("RSA1", rsa1);

        Map<String, Object> rsa2 = new LinkedHashMap<>(rsa0);
        rsa2.put("transform.function", "Auto");
        rsa2.put("tradingdays.option", "WorkingDays");
        rsa2.put("tradingdays.test", "Separate_T");
        rsa2.put("easter.type", "Standard");
        rsa2.put("easter.test", true);
        rsa2.put("outlier.enabled", true);
        rsa2.put("outlier.ao", true);
        rsa2.put("outlier.tc", true);
        rsa2.put("outlier.ls", true);
        rsa2.put("automdl.enabled", true);
        DEFAULTS.put("RSA2", rsa2);

        Map<String, Object> rsa3 = new LinkedHashMap<>(rsa0);
        rsa3.put("transform.function", "Auto");
        rsa3.put("outlier.enabled", true);
        rsa3.put("outlier.ao", true);
        rsa3.put("outlier.tc", true);
        rsa3.put("outlier.ls", true);
        rsa3.put("automdl.enabled", true);
        DEFAULTS.put("RSA3", rsa3);

        Map<String, Object> rsa4 = new LinkedHashMap<>(rsa0);
        rsa4.put("transform.function", "Auto");
        rsa4.put("tradingdays.option", "WorkingDays");
        rsa4.put("tradingdays.leapyear", true);
        rsa4.put("tradingdays.test", "Separate_T");
        rsa4.put("easter.type", "Standard");
        rsa4.put("easter.test", true);
        rsa4.put("outlier.enabled", true);
        rsa4.put("outlier.ao", true);
        rsa4.put("outlier.tc", true);
        rsa4.put("outlier.ls", true);
        rsa4.put("automdl.enabled", true);
        DEFAULTS.put("RSA4", rsa4);

        Map<String, Object> rsa5 = new LinkedHashMap<>(rsa0);
        rsa5.put("transform.function", "Auto");
        rsa5.put("tradingdays.option", "TradingDays");
        rsa5.put("tradingdays.leapyear", true);
        rsa5.put("tradingdays.test", "Separate_T");
        rsa5.put("easter.type", "Standard");
        rsa5.put("easter.test", true);
        rsa5.put("outlier.enabled", true);
        rsa5.put("outlier.ao", true);
        rsa5.put("outlier.tc", true);
        rsa5.put("outlier.ls", true);
        rsa5.put("automdl.enabled", true);
        DEFAULTS.put("RSA5", rsa5);

        Map<String, Object> rsafull = new LinkedHashMap<>(rsa0);
        rsafull.put("transform.function", "Auto");
        rsafull.put("tradingdays.mauto", "FTest");
        rsafull.put("tradingdays.option", "TradingDays");
        rsafull.put("tradingdays.leapyear", true);
        rsafull.put("tradingdays.test", "Separate_T");
        rsafull.put("easter.type", "IncludeEaster");
        rsafull.put("easter.test", true);
        rsafull.put("outlier.enabled", true);
        rsafull.put("outlier.ao", true);
        rsafull.put("outlier.tc", true);
        rsafull.put("outlier.ls", true);
        rsafull.put("automdl.enabled", true);
        DEFAULTS.put("RSAfull", rsafull);
    }

    private BasicSpecDefaults() {
    }

    /**
     * Completa il JSON di una serie con i default della sua specifica di base.
     * I campi gia' presenti restano invariati.
     */
    public static Map<String, Object> complete(Map<String, Object> json) {
        Object specName = json.get("spec");
        String spec = (specName == null || "NA".equals(specName.toString())) ? "RSA0" : specName.toString();
        Map<String, Object> defaults = DEFAULTS.get(spec);
        if (defaults == null) {
            throw new IllegalArgumentException("Serie " + json.get("series_name")
                    + ": specifica di base sconosciuta '" + spec + "' (attese RSA0..RSA5, RSAfull)");
        }
        Map<String, Object> out = new LinkedHashMap<>(json);
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
        }
        if (!out.containsKey("method")) {
            out.put("method", "TS");
        }
        if (!out.containsKey("spec")) {
            out.put("spec", spec);
        }
        return out;
    }
}
