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
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esegue la stessa elaborazione di Main_with_checks (destagionalizzazione delle serie del JSON
 * + verifica di coerenza), con i percorsi passati come parametri. Usata dall'interfaccia grafica.
 *
 * @author alessandro.piovani@istat.it
 */
public class ProcessingRunner {

    /** Parametri dell'elaborazione. */
    public static class Config {
        public String rawDataFile;      // dati grezzi (CSV formato ISTAT, es. grezzi.csv)
        public String regressorsDir;    // cartella dei regressori esterni (es. regr)
        public String specFile;         // specifiche JSON (completo o ridotto)
        public String siticSaFile;      // SA di TS+ (es. sa_new.csv); null = confronto non eseguito
        public String compareWsFile;    // workspace di confronto (.xml); null = confronto non eseguito
        public boolean saveOutputs;     // salva workspace e report
        public String outputDir;        // cartella di output (se saveOutputs)
        public double absTol = 0.01;    // tolleranza assoluta
        public double relTolPercent = 0.001;  // tolleranza relativa, in percentuale
    }

    /** Esito dell'elaborazione. */
    public static class Result {
        public boolean allOk;
        public String reportTxt;        // null se i report non sono salvati
        public String workspaceFile;    // null se il workspace non e' salvato
    }

    public static Result run(Config c) throws Exception {
        if (c.siticSaFile == null && c.compareWsFile == null) {
            throw new IllegalArgumentException("Indicare almeno un confronto: TS+ oppure workspace");
        }
        Result result = new Result();

        System.out.println("Lettura dati grezzi: " + c.rawDataFile);
        DataReaderCSV_IstatFormat reader = new DataReaderCSV_IstatFormat();
        Map<String, TsData> tsDataMap = reader.readData(c.rawDataFile);

        System.out.println("Lettura specifiche: " + c.specFile);
        List<Map<String, Object>> jsonData = JsonReader.readJsonFile(c.specFile);

        ProcessingContext context = new ProcessingContext();
        Workspace ws = new Workspace(context);
        MultiProcessing mp = ws.newMultiProcessing("All");
        Map<String, TsData> javaSaMap = new LinkedHashMap<>();

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
            reportCsv = new File(out, "verifica_sa.csv").getPath();
            reportTxt = new File(out, "verifica_sa.txt").getPath();
        }

        SaConsistencyCheck check = new SaConsistencyCheck(c.absTol, c.relTolPercent / 100.0);
        result.allOk = check.run(javaSaMap, c.siticSaFile, c.compareWsFile, reportCsv, reportTxt);
        result.reportTxt = reportTxt;
        System.out.println(result.allOk ? "VERIFICA SUPERATA" : "VERIFICA CON DIFFERENZE");
        return result;
    }
}
