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
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alessandro.piovani@istat.it
 */
public class Main_with_checks {

    public static void main(String[] args) {
        try {
            DataReaderCSV_IstatFormat reader = new DataReaderCSV_IstatFormat();

//            String localpath_SITIC = "U:\\Desktop\\SINTESI_FAT_2026\\SITIC-FAT";
//            String localpath       = "U:\\Desktop\\SINTESI_FAT_2026";

            String localpath_SITIC = "C:\\Users\\UTENTE\\OneDrive - ISTAT\\Desktop\\SINTESI_FAT_2026\\SITIC-FAT";
            String localpath       = "C:\\Users\\UTENTE\\OneDrive - ISTAT\\Desktop\\SINTESI_FAT_2026";
            
            
            // --- File per la verifica delle serie destagionalizzate ---
            String siticSaFile = localpath_SITIC + "\\sa_new.csv";                              // SA di TS+ (SITIC)
            
            //String wsLocalFile = localpath + "\\Workspace_container_FAT\\FAT.xml";   // oppure \\output_workspace_container\\workspace.xml
            String wsLocalFile = localpath + "\\output_workspace_container\\workspace.xml"; // meglio lui perchè non ha risultati pre-salvati

            
            String reportCsv   = localpath + "\\verifica_sa.csv";                              // report per Excel
            String reportTxt   = localpath + "\\verifica_sa.txt";                              // report leggibile

            Map<String, TsData> tsDataMap = reader.readData(localpath_SITIC + "\\grezzi.csv");

            //Map<String, TsData> tsDataMap = reader.readData(localpath + "\\rawdata_db_istat_format.csv");
            String directoryPathExtReg = localpath_SITIC + "\\regr\\";

//            String filePath = localpath + "\\specifications_new_full.txt";
            String filePath = localpath + "\\specifications_new.txt";
            
            List<Map<String, Object>> jsonData = JsonReader.readJsonFile(filePath);

            // We will use the library jdr-2.2.5.jar (see in https://github.com/nbbrd/jdemetra-sa-advanced for the last release of the library)
            // That library is used in rjdemetra. It has been slightly modified (2.2.5) to be used in this code
            // We create a unique ProcessingContext for all the series
            ProcessingContext context = new ProcessingContext();

            // Create the workspace with the context that will be filled in TSModelSetup
            // (to be noted that we read only once each external file)
            Workspace ws = new Workspace(context);

            // Create the multiplrocessing (with any name)
            MultiProcessing mp = ws.newMultiProcessing("All");

            // Serie destagionalizzate calcolate in Java, per la verifica finale
            Map<String, TsData> javaSaMap = new LinkedHashMap<>();

            String seriesName = null;
            for (Map<String, Object> data : jsonData) {
                seriesName = data.get("series_name").toString();
                System.out.println("Series Name: " + seriesName);
                // Deserialization of JSON
                ObjectMapper mapper = new ObjectMapper();
                //ignore not predefined keys
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                //put also single values in arrays where it is specified
                mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
                //set empty strings to null
                mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                //create the object
                DestSpecificationsModel model = mapper.readValue(mapper.writeValueAsString(data), DestSpecificationsModel.class);

                // If you want to set the start and end of the Time Series by injecting values directly, you can use this rows:
                // model.setSeriesStart("2012-09-01"); // dates must be read from an external source, for the series of interest.
                // model.setSeriesEnd("2022-09-01");

                // The context is initialized in this function call
                TSmodelSetup tsModelSetup = new TSmodelSetup(model, context, directoryPathExtReg, tsDataMap.get(seriesName));
                TramoSeatsSpecification TRAMOSEATSspec = tsModelSetup.getTsSpec();

                //CompositeResults rslt = TramoSeatsProcessingFactory.process(tsDataMap.get(seriesName), TRAMOSEATSspec, context); //old
                CompositeResults rslt = TramoSeatsProcessingFactory.process(tsModelSetup.getTsData(), TRAMOSEATSspec, context); // new, qui prendo i dati di input dopo che sono stati filtrati con le date di inizio e fine che ho messo nel modello
                TsData sa_data  = rslt.getData("sa", TsData.class);
                TsData cal_data = rslt.getData("ycal", TsData.class);

                // conservo la serie SA per la verifica
                javaSaMap.put(seriesName, sa_data);

                System.out.println("SA series:");
                System.out.println(sa_data);
                System.out.println("Cal. adjusted series:");
                System.out.println(cal_data);
                System.out.println("_______________________________________________________________");

                // add in the multiprocessing each single processing
                // (stessi dati filtrati usati per il calcolo Java, cosi' workspace e risultati Java coincidono)
                mp.add(seriesName, tsModelSetup.getTsData(), TRAMOSEATSspec);
            }

            // Save the workspace (the output folder must exist)
            boolean ret = ws.save(localpath + "\\workspace_SINTESI_LOCAL\\test.xml");
            if (ret) {
                System.out.println("WS SAVED");
            } else {
                System.out.println("ERROR IN SAVING WS");
            }

            // --- Verifica di coerenza: Java vs TS+ (SITIC) vs workspace locale ---
            // tolleranze: differenza assoluta 0.01 OPPURE relativa 1e-5
            SaConsistencyCheck check = new SaConsistencyCheck(0.01, 1e-5);
            boolean allOk = check.run(javaSaMap, siticSaFile, wsLocalFile, reportCsv, reportTxt);
            System.out.println(allOk ? "VERIFICA SUPERATA" : "VERIFICA CON DIFFERENZE: vedi " + reportTxt);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(Main_with_checks.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}