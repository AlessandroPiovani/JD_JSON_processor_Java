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
            String localpath = "C:\\Users\\UTENTE\\OneDrive - ISTAT\\Documenti\\NetBeansProjects3\\JD_JSON_processor_Java-main\\JD_JSON_processor_Java-main\\src\\resources";
            //String localpath = "C:\\Users\\UTENTE\\Desktop\\resources";
            //Map<String, TsData> tsDataMap = reader.readData(localpath + "\\grezziFAT.csv");
            
            //Map<String, TsData> tsDataMap = reader.readData(localpath + "\\grezziTUR.csv");
            //Map<String, TsData> tsDataMap = reader.readData(localpath + "\\grezziVEN.csv");
            Map<String, TsData> tsDataMap = reader.readData(localpath +"\\serie_fatt_per_JD.csv");

            //Map<String, TsData> tsDataMap = reader.readData(localpath + "\\rawdata_db_istat_format.csv");           
            String directoryPathExtReg = localpath + "\\regr\\";

            //String filePath = localpath + "\\specifications_new_full_TUR_NO_Ramps_and_Iv.txt";
            //String filePath = localpath + "\\specifications_new_full_TUR_Ramps_and_Iv.txt";
            String filePath = localpath + "\\specifications_db2.txt";
            //String filePath = localpath + "\\specifications_new_full_VEN.txt";
            
            

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
            
            String seriesName=null;
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
                // The context is initialized in this function call
                TSmodelSetup tsModelSetup = new TSmodelSetup(model, context, directoryPathExtReg, tsDataMap.get(seriesName));
                TramoSeatsSpecification TRAMOSEATSspec = tsModelSetup.getTsSpec();

                CompositeResults rslt = TramoSeatsProcessingFactory.process(tsDataMap.get(seriesName), TRAMOSEATSspec, context);
                TsData sa_data  = rslt.getData("sa", TsData.class);
                TsData cal_data = rslt.getData("ycal", TsData.class);
                
                System.out.println("SA series:");
                System.out.println(sa_data);
                System.out.println("Cal. adjusted series:");
                System.out.println(cal_data);
                System.out.println("_______________________________________________________________");

                // ADDED for forecasting 
                TsData yef_data = rslt.getData("y_ef", TsData.class);
                System.out.println("y_ef:");
                System.out.println(yef_data);
                TsData yf_data = rslt.getData("y_f", TsData.class);
                System.out.println("y_f:");
                System.out.println(yef_data);
                Map<String, Class> d = rslt.getDictionary();
                
                // add in the multiprocessing each single processing
                mp.add(seriesName, tsDataMap.get(seriesName) , TRAMOSEATSspec);
                                      
            }

            // Save the workspace (the output folder must exist)
            // ws.save("C:\\Users\\UTENTE\\Documents\\NetBeansProjects3\\JD_JSON_processor_Java-main\\JD_JSON_processor_Java-main\\src\\resources\\workspace\\test.xml");
            boolean ret=ws.save("C:\\Users\\UTENTE\\Desktop\\resources\\workspace\\test.xml");
            if(ret)
            {System.out.println("WS SAVED");}
            else
            {System.out.println("ERROR IN SAVING WS");}    
            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
