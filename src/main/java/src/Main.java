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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alessandro.piovani@istat.it
 */
public class Main {

    public static void main(String[] args) {
        try {

            DataReaderCSV_IstatFormat reader = new DataReaderCSV_IstatFormat();
            String localpath = "C:\\Users\\palat\\Documents\\Netbeans\\JD_JSON_processor_Java\\src\\resources";
            Map<String, TsData> tsDataMap = reader.readData(localpath + "\\grezziFAT.csv");
            String directoryPathExtReg = localpath + "\\regr\\";
            String filePath = localpath + "\\specifications_new_full_FAT.txt";
            List<Map<String, Object>> jsonData = JsonReader.readJsonFile(filePath);

            // Esegui ulteriori operazioni sulla mappa tsDataMap
            //for (Map.Entry<String, TsData> entry : tsDataMap.entrySet()) {
            //    System.out.println("Serie: " + entry.getKey());
            //    System.out.println(entry.getValue());
            //}
            // Print or use the data as you prefer
            
            
            // We will use the library jdr-2.2.5.jar (see in https://github.com/nbbrd/jdemetra-sa-advanced for the last release of the library)
            // That library is used in rjdemetra. It has been slightly modified (2.2.5) to be used in this code 
            // We create a unique ProcessingContext for all the series
            ProcessingContext context = new ProcessingContext();
            
            // Create the workspace with the context that will be filled in TSModelSetup
            // (to be noted that we read only once each external file)
            Workspace ws = new Workspace(context);
            
            // Create the multiplrocessing (with any name)
            MultiProcessing mp = ws.newMultiProcessing("All");
            for (Map<String, Object> data : jsonData) {
                System.out.println("Series Name: " + data.get("series_name"));

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
                // The context is initialized in this function call
                TSmodelSetup tsModelSetup = new TSmodelSetup(model, context, directoryPathExtReg);
                TramoSeatsSpecification TRAMOSEATSspec = tsModelSetup.getTsSpec();

                CompositeResults rslt = TramoSeatsProcessingFactory.process(tsDataMap.get(data.get("series_name")), TRAMOSEATSspec, context);
                TsData sa_data = rslt.getData("sa", TsData.class);
                System.out.println(sa_data);
                
                // add in the multiprocessing each single processing
                mp.add(data.get("series_name").toString(), sa_data, TRAMOSEATSspec);

            }

            // Save the workspace (the output folder must exist)
            ws.save("c:\\cruncher\\test.xml");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
