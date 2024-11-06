package unused;

import src.DestSpecificationsModel;
import src.TSmodelSetup;

import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.seats.SeatsResults;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.satoolkit.x13.X13Specification;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.algorithm.IProcResults;
import ec.tstoolkit.algorithm.ProcessingContext;
import ec.tstoolkit.arima.special.EasterSpec;
import ec.tstoolkit.information.StatisticalTest;
import ec.tstoolkit.modelling.DefaultTransformationType;
import ec.tstoolkit.modelling.arima.PreprocessingModel;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsDataTable;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.ucarima.UcarimaModel;
//import it.istat.koala.enumerator.DestTipoDestag;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.config.ConfigurableBeanFactory;
//import org.springframework.context.annotation.Scope;
//import org.springframework.core.env.Environment;
//import org.springframework.stereotype.Service;
//import it.istat.koala.service.JDemetraService;
//import it.istat.koala.service.JobErroriService;
//import it.istat.koala.utility.DestSpecificationsModel;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import it.istat.koala.utility.SeasonalData;
//import it.istat.koala.utility.TSmodelSetup;

/**
 *
 * @author giacomi
 */
public class JDemetraServiceImpl {

    private static final String JSON_CODE_ATTR = "Code";
    private static final String JSON_IDCUBE_ATTR = "IDCube";
    private static final String JSON_IDMAPPING_ATTR = "IDMapping";
    private static final String ISTAT_AGENCY_ID = "IT1";



    //private Environment environment;

    //private JobErroriService jobErroriService;

    public String apply(Long cubeId) throws Exception {

        return "todo";
    }

    public String process(SeasonalData datiSerie, Long id, String directoryPathExtReg) throws Exception {

        TramoSeatsSpecification specifications=null;
        X13Specification xspec=null;
        
        if (datiSerie.getModello()!=null) {
            
            if ("X".equals(datiSerie.getModello().getMethod())) {
                processXspec(datiSerie);
            } else {
                processTSspec(datiSerie, directoryPathExtReg);
            }
        }
        return "ok";
    }
    
    private long[] getDefaultResultTypes() {
        long [] rsltTypes= new long[1];
        rsltTypes[0]=DestTipoDestag.SA.getIdTipoIndicatoreOutput();
        return rsltTypes;
    }
    
    private String processTSspec(SeasonalData datiSerie, String directoryPathExtReg) {
        TramoSeatsSpecification specifications=null;
 
        long[] rsltTypes = datiSerie.getIdTipoOutput();
        if (rsltTypes==null || rsltTypes.length==0) {
            rsltTypes=getDefaultResultTypes();
        }
        
        try {
            DestSpecificationsModel model=datiSerie.getModello();
            ProcessingContext context=new ProcessingContext();
            TSmodelSetup setup = new TSmodelSetup(model, context, directoryPathExtReg);
            //specifications = TramoSeatsSpecification.fromString(model.getSpec());
            specifications = setup.getTsSpec();
        } catch (Exception e) {
            System.out.println("conversione del modello non riuscita: "+e.getMessage());
            throw e;
        }
    /*
     * Creates a new time series for a given array of observations.
     *
     * @param freq The frequency of the series
     * @param firstyear Year of the first period . .
     * @param firstperiod 0-based Position of the first period in the first
     * year.
     * @param data The given observations
     * @param copydata Indicates if the observations are copied or if the array
     * is taken as is in the internal state of the object. If copydata is false,
     * users should no longer use the given data array.
     */
       
        TsData inputData = new TsData(TsFrequency.valueOf(datiSerie.getModello().getFrequency()), datiSerie.getAnno(), datiSerie.getMese(), datiSerie.getValori(), true);
        System.out.println("Dati grezzi in input "+datiSerie.getOutputKey()+"\n"+inputData);
        /* TODO Decidere se inserire anche i dati di input o no*/
        datiSerie.addRisultato(DestTipoDestag.DATIGREZZI.name(), inputData);

        // Just to store the results (not necessary in other usages)
        TsDataTable table = new TsDataTable();
        table.insert(-1, inputData);

        // Process
        IProcResults rslts = TramoSeatsProcessingFactory.process(inputData, specifications);
        
        for(int idx=0;idx<rsltTypes.length;idx++){
            String resType=null;
            try {
                resType=DestTipoDestag.getTipoDestag(rsltTypes[idx]);
                if (resType!=null) {
                    TsData result = rslts.getData(resType, TsData.class);
                    datiSerie.addRisultato(resType,result);
                    table.insert(-1, result);
                    System.out.println("TsData "+resType+"\n"+result);
                }
            } catch (Exception e) {
                System.out.println("Errore in elab risultato "+resType+":\n"+e.getMessage());
            }
        }
        datiSerie.setLikelihoodBIC(rslts.getData("likelihood.bic",Double.class));
        System.out.println("BIC: "+datiSerie.getLikelihoodBIC());
        ////System.out.println("TsData\n"+table);
        return "ok";
    }
    private String processXspec(SeasonalData datiSerie) {
        return "ok";
    }
    //public String process(List<RigaDettaglioDTO> datiDaDestagionalizzare, Long id) throws Exception {
    public String processTest(int anno, int mese, double[] datiDaDestagionalizzare, String modello, Long id) throws Exception {
        
        //hw10
        /*double[] g_prod = {
            59.2, 58.3, 63.4, 59.7, 58.9, 62.7, 47.6, 58.6, 64.4, 66.4, 64.2, 62.2, 61.7, 62.2, 65.5, 64.6, 64.6, 62.2, 53.2, 62.5, 68.5, 73.5, 67.1, 68.6,
            69.1, 65.5, 72.7, 73, 70.3, 73.5, 61.5, 67.6, 77.7, 81.7, 73.5, 75.4, 70.6, 70.8, 76.9, 77.7, 71.1, 77.3, 63.1, 70.8, 80.5, 82.7, 75.8, 79.3,
            72.3, 74, 82.7, 79.1, 74.4, 79.5, 61.9, 73.5, 83.1, 82.9, 78, 80.4, 77.7, 79, 88.1, 79.5, 80.9, 85.7, 61.2, 78.7, 87.6, 91.5, 88.5, 86.6,
            86.8, 84.7, 94.1, 86.9, 90.2, 86.1, 68.8, 86.9, 90.7, 99.6, 94.9, 88.2, 95.2, 91.9, 97.5, 96.4, 95.2, 91.8, 74.7, 86.7, 96.2, 100.6, 89.7, 85.7,
            88.5, 83.8, 86.3, 86.7, 79, 84.2, 64.6, 72.6, 88.2, 91.1, 84, 85.8, 86.1, 88, 97.6, 95.3, 89.1, 93.5, 69.4, 86, 99.1, 97.3, 92.9, 92.7,
            90.2, 89.7, 102.3, 92, 89.1, 95.2, 67, 88.1, 95.6, 94.2, 93, 92.2, 91.5, 88.9, 99.1, 93.6, 91.5, 94.6, 67.6, 89.8, 99.3, 103.7, 100.3, 94.8,
            92.2, 93.8, 103.5, 98.8, 99.2, 99.5, 75.6, 96, 102.1, 109.3, 103.3, 96.3, 104.5, 102.8, 105.8, 102.3, 93.7, 99, 73, 87.9, 100.1, 103.8, 90.9, 89.1,
            91.6, 92.5, 100.3, 97.5, 90.4, 96.4, 70.8, 86.7, 102.5, 103.7, 96.8, 93.7, 93.4, 92.5, 99.9, 99.6, 91.5, 99.7, 70.6, 88.1, 102, 101.1, 94, 92.3,
            94.4, 93, 103.9, 96.1, 94.3, 102.2, 70, 93.5, 102.3, 102.5, 101.4, 94.5, 100.5, 100, 105.1, 96.3, 102.1, 97.8, 75.1, 94.3, 102, 110.4, 102.8, 92.9,
            99.4, 97.2, 105.5, 102.6, 99.7, 101, 79.6, 93.5, 107.7, 114, 104.5, 95.4, 104.1, 100.6, 104.6, 109, 95.7, 104.4, 82.5, 93.5, 109.6, 113.4, 100.6, 97.8,
            101.2, 101.7, 110.8, 108.7, 101.8, 107.2, 83, 97.5, 114.3, 116.4, 107.5, 101.5, 108.5, 109.3, 119, 111.3, 108.5, 117.5, 84.7, 107, 121.8, 117.7, 116, 108.5,
            118.4, 113, 122.5, 117.1, 112, 122.6, 90.2, 112.3, 122.4, 125.4, 120.7, 107.2, 126.8, 118.8, 132.9, 117.7, 121.8, 123.9, 90.3, 113.2, 124.7, 135.4, 126.3, 110.1,
            126.8, 117.7, 126.6, 123, 118.1, 123.7, 93.5, 105.4, 125, 131.9, 119.9, 110.3, 126.2, 121.6, 130.9, 123.6, 116.1, 126.9, 95, 107.6, 128.4, 127.1, 116.3, 109.5,
            113.4, 114, 128.5, 118.3, 108.6, 124.2, 86.7, 104.2, 124.1, 121.2, 112.6, 114.1, 120.3, 117.6, 133.6, 117.7, 113.8, 126.6, 81.6, 108.7, 125.9, 123, 120.7, 109.7};*/

        //TsData input = new TsData(TsFrequency.Monthly, 1987, 0, datiDaDestagionalizzare, false);
        TsData input = new TsData(TsFrequency.Monthly, anno, mese, datiDaDestagionalizzare, false);

        // Just to store the results (not necessary in other usages)
        TsDataTable table = new TsDataTable();
        table.insert(-1, input);
        // Using a pre-defined specification
        TramoSeatsSpecification rsafull = TramoSeatsSpecification.RSAfull.clone();
        // Actual number of forecasts
        // rsafull.getSeatsSpecification().setPredictionLength(50);
        // Or number of years
        rsafull.getSeatsSpecification().setPredictionLength(-4);
        // Process
        IProcResults rslts = TramoSeatsProcessingFactory.process(input, rsafull);

        TsData sa = rslts.getData("sa", TsData.class);
        table.insert(-1, sa);
        TsData trend = rslts.getData("t", TsData.class);
        table.insert(-1, trend);
        TsData saf = rslts.getData("sa_f", TsData.class);
        table.insert(-1, saf);
        TsData trendf = rslts.getData("t_f", TsData.class);
        table.insert(-1, trendf);

        // Create a user defined specification (starting from a copy of RSAfull)         
        TramoSeatsSpecification mySpec = rsafull.clone();
        EasterSpec es;
        // Very sensitive outliers detection
        mySpec.getTramoSpecification().getOutliers().setCriticalValue(2.5);
        // Allow benchmarking
        mySpec.getBenchmarkingSpecification().setEnabled(true);
        // Process
        IProcResults myrslts = TramoSeatsProcessingFactory.process(input, mySpec);

        TsData mysa = myrslts.getData("sa", TsData.class);
        table.insert(-1, mysa);
        TsData mytrend = myrslts.getData("t", TsData.class);
        table.insert(-1, mytrend);
//        System.out.println(sa);
        TsData mybench = myrslts.getData("benchmarking.result", TsData.class);
        table.insert(-1, mybench);
        DefaultTransformationType tt = null; //= "None"
        TsData diagnostics =  myrslts.getData("diagnostics", TsData.class);

        StatisticalTest skewness = rslts.getData("residuals.skewness", StatisticalTest.class);
        if(skewness!=null)System.out.println("skewness "+skewness.pvalue);
        StatisticalTest myskewness = myrslts.getData("residuals.skewness", StatisticalTest.class);
        if(myskewness!=null)System.out.println("myskewness "+myskewness.pvalue);
        System.out.println();
        System.out.println("TsData\n"+table);

        System.out.println("Forecasts");
        System.out.println(myrslts.getData("fcasts(-5)", TsData.class));
        System.out.println("Backcasts");
        System.out.println(myrslts.getData("bcasts(-1)", TsData.class));
        
        //hw12
        // Using a pre-defined specification
        // Process
        CompositeResults crslts = TramoSeatsProcessingFactory.process(input, rsafull);
        SeatsResults seats= crslts.get(TramoSeatsProcessingFactory.DECOMPOSITION, SeatsResults.class);
        if (seats!=null) {
        // Ucarima decomposition
            UcarimaModel ucarima = seats.getUcarimaModel();
            // trend model
            if(ucarima!=null)System.out.println("ucarima component\n"+ucarima.getComponent(0));
            // complement of the trend (=seasonal + irregular). Computed dynamically (not part of the usual  output)
            if(ucarima!=null)System.out.println("ucarima complement\n"+ucarima.getComplement(0));
        } else {System.out.println("null decomposition");}
        // Get the output of Tramo
        PreprocessingModel model= crslts.get(TramoSeatsProcessingFactory.PREPROCESSING, PreprocessingModel.class);
        if (model!=null) {
            // compute backcasts (dynamically)
            TsData backcast = model.backcast(100, false);
            System.out.println("preprocessing backcast\n"+backcast);
            TsData forecast = model.forecast(100, false);
            System.out.println("preprocessing forecast\n"+forecast);
        }else {System.out.println("null preprocessing");}

        return myrslts.toString();
    }
    
}
