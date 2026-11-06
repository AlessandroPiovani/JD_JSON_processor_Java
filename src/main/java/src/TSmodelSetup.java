package src;

import ec.satoolkit.seats.SeatsSpecification;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.Parameter;
import ec.tstoolkit.ParameterType;
import ec.tstoolkit.algorithm.ProcessingContext;
import ec.tstoolkit.modelling.DefaultTransformationType;
import ec.tstoolkit.modelling.RegressionTestType;
import ec.tstoolkit.modelling.TsVariableDescriptor;
import ec.tstoolkit.modelling.arima.tramo.ArimaSpec;
import ec.tstoolkit.modelling.arima.tramo.AutoModelSpec;
import ec.tstoolkit.modelling.arima.tramo.EasterSpec;
import ec.tstoolkit.modelling.arima.tramo.EstimateSpec;
import ec.tstoolkit.modelling.arima.tramo.OutlierSpec;
import ec.tstoolkit.modelling.arima.tramo.RegressionSpec;
import ec.tstoolkit.modelling.arima.tramo.TradingDaysSpec;
import ec.tstoolkit.modelling.arima.tramo.TransformSpec;
import ec.tstoolkit.timeseries.Day;
import ec.tstoolkit.timeseries.TsPeriodSelector;
import ec.tstoolkit.timeseries.calendars.TradingDaysType;
import ec.tstoolkit.timeseries.regression.InterventionVariable;
import ec.tstoolkit.timeseries.regression.OutlierDefinition;
import ec.tstoolkit.timeseries.regression.OutlierType;
import ec.tstoolkit.timeseries.regression.Ramp;
import ec.tstoolkit.timeseries.regression.TsVariable;
import ec.tstoolkit.timeseries.regression.TsVariables;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.isNull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static src.ArimaSplitter.splitArimaCoefficients;

/**
 * @authors 
 * cazora 
 * apiovani (alessandro.piovani@istat.it)
 */
public class TSmodelSetup {

    public static final String EXTERNAL = "external";
    private final DestSpecificationsModel model;
    private final ProcessingContext context; // Alessandro  
    private final TramoSeatsSpecification tsSpec; // TOLTO FINAL
    private String[] tdVarNames     = {}; //added
    private String[] usrDefVarNames = {}; //added/**

    private TsData tsData; //added
    
    public TsData getTsData() {
        return tsData;
    }

    //added
    public void setTsData(TsData tsData) {
        this.tsData = tsData;
    }

    public ProcessingContext getContext() {
        return context;
    }

    public DestSpecificationsModel getModel() {
        return model;
    }

    public TramoSeatsSpecification getTsSpec() {
        return tsSpec;
    }
     
    public TSmodelSetup(DestSpecificationsModel model, ProcessingContext context, String directoryPathExtReg, TsData tsData) {
        this.model = model;
        this.context = context;
        this.tsData=tsData;
        TsVariables vars = context.getTsVariables(EXTERNAL);
        if (vars == null) {
            context.getTsVariableManagers().set(EXTERNAL, new TsVariables());
        }

        if (model != null && model.getSpec() != null) {
            //String jsonString = model.getSpec();
            // Don't forget to clone the default spec
            this.tsSpec = TramoSeatsSpecification.fromString(model.getSpec()).clone();
            setupTSmodel(directoryPathExtReg);
        } else {
            // Don't forget to clone the default spec
            this.tsSpec = TramoSeatsSpecification.RSAfull.clone();
        }

    }

//    public TSmodelSetup(DestSpecificationsModel model, ProcessingContext context, String directoryPathExtReg, TsData tsData, Double oldCV) {
//        this(model, context, directoryPathExtReg, tsData);
//     
//        adjustOutliersCV(oldCV);
//
//    }
    
    
    private void setupTSmodel(String directoryPathExtReg) {
        setInputSeries();
        setTransform();
        setEstimate();
        setTradingDays(directoryPathExtReg);
        setUserDefinedVariables(directoryPathExtReg); //Added by Alessandro
        setEaster();
        setOutliers();
        setAutoModeling();
        setArima();
        setSeats();
        setRamps(); // Alessandro, very last update.
        setInterventionVariables(); // Alessandro, very last update. 
        //EXAMPLE OF RAMPS FROM R: "[{\"start\":\"2020-01-01\",\"end\":\"2020-12-31\", \"fixed_coef\": 2},{\"start\":\"2008-01-01\",\"end\":\"2009-01-01\", \"fixed_coef\":\"NA\"}]" // fixed_coef is a double or "NA"
        //EXAMPLE of INTERVENTION VARS FROM R "[{\"delta\":1,\"delta_s\":1,\"seq\":[{\"start\":\"2001-01-01\",\"end\":\"2001-12-31\"}, \"D1DS\":true]},{\"delta\":0.75,\"delta_s\":0,\"seq\":[{\"start\":\"2004-01-01\",\"end\":\"2005-12-31\"}]\"D1DS\":false}]"
        
        fixOutliersAndVariablesCoefficients(); // to be completed
        fixDefaultJDplusCalendarCoefficients(); //Easter, JD+ automatic TDs and Leap Year
    }

   
    private void setInputSeries(){
        // Span della serie come in JDemetra+: date (series_start / series_end) oppure
        // numero di osservazioni (series_first / series_last / series_exclFirst / series_exclLast)
        TsPeriodSelector periodSelector = buildSpan("series", model.getSeriesStart(), model.getSeriesEnd(),
                model.getSeriesFirst(), model.getSeriesLast(), model.getSeriesExclFirst(), model.getSeriesExclLast());
        this.tsData = tsData.select(periodSelector);
    }

    /**
     * Costruisce uno span (TsPeriodSelector) con la stessa logica di JDemetra+, dove ogni span
     * e' di UN solo tipo: All, From, To, Between (date) oppure First, Last, Excluding (numero di
     * osservazioni, contate sul dominio a cui lo span si applica: la serie per serie e stima,
     * lo span di stima per gli outlier). Il tipo dinamico viene mantenuto, quindi resta tale
     * anche nel workspace salvato.
     *
     * Regole sui campi del JSON ("NA" o assente = non specificato; excl = 0 = non specificato):
     *  - date e conteggi insieme non sono rappresentabili in JD+: errore;
     *  - first e last insieme: precedenza a first (scelta di progetto);
     *  - nessun campo: All.
     */
    private static TsPeriodSelector buildSpan(String label, String from, String to,
            String first, String last, int exclFirst, int exclLast) {
        boolean hasFrom  = isPresent(from);
        boolean hasTo    = isPresent(to);
        boolean hasFirst = isPresent(first);
        boolean hasLast  = isPresent(last);
        boolean hasExcl  = exclFirst != 0 || exclLast != 0;

        if ((hasFrom || hasTo) && (hasFirst || hasLast || hasExcl)) {
            throw new IllegalArgumentException("Span '" + label + "': date (from/to) e numero di osservazioni"
                    + " (first/last/excl) insieme non sono supportati da JDemetra+");
        }
        if ((hasFirst || hasLast) && hasExcl) {
            throw new IllegalArgumentException("Span '" + label + "': first/last ed excl insieme non sono"
                    + " supportati da JDemetra+");
        }

        TsPeriodSelector sel = new TsPeriodSelector();
        try {
            if (hasFirst) {                       // precedenza a first se c'e' anche last
                sel.first(Integer.parseInt(first.trim()));
            } else if (hasLast) {
                sel.last(Integer.parseInt(last.trim()));
            } else if (hasExcl) {
                sel.excluding(exclFirst, exclLast);
            } else if (hasFrom && hasTo) {
                sel.between(Day.fromString(from.trim()), Day.fromString(to.trim()));
            } else if (hasFrom) {
                sel.from(Day.fromString(from.trim()));
            } else if (hasTo) {
                sel.to(Day.fromString(to.trim()));
            } else {
                sel.all();
            }
        } catch (NumberFormatException | ParseException ex) {
            throw new IllegalArgumentException("Span '" + label + "': valore non valido (from=" + from
                    + ", to=" + to + ", first=" + first + ", last=" + last + ")", ex);
        }
        return sel;
    }

    /** Campo stringa valorizzato: non null, non vuoto, non "NA". */
    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty() && !"NA".equalsIgnoreCase(s.trim());
    }
    
    
    private void setTransform() {
        TransformSpec tf = tsSpec.getTramoSpecification().getTransform();
        if (tf == null) {
            tf = new TransformSpec();
            tsSpec.getTramoSpecification().setTransform(tf);
        }
        tf.setFunction(DefaultTransformationType.valueOf(model.getTransformFunction()));
        tf.setFct(model.getTransformFct());
        tf.setPreliminaryCheck(model.isPreliminaryCheck());
    }

    private void setEstimate() {
        EstimateSpec espec = tsSpec.getTramoSpecification().getEstimate();
        if (espec == null) {
            espec = new EstimateSpec();
            tsSpec.getTramoSpecification().setEstimate(espec);
        }
        /*
            "estimate.urfinal":0.96,
         */
        espec.setUbp(model.getEstimateUrfinal()); // Alessandro
        espec.setTol(model.getEstimateTol());
        espec.setEML(model.isEstimateEml());

        espec.setSpan(buildSpan("estimate", model.getEstimateFrom(), model.getEstimateTo(),
                model.getEstimateFirst(), model.getEstimateLast(),
                model.getEstimateExclFirst(), model.getEstimateExclLast()));
    }

    /** Numero di variabili contenute in un file di regressori (almeno 1). */
    private static int numVarOf(DestSpecVarFromFileInfo info) {
        return Math.max(1, info.getNumVar());
    }

    private void setTradingDays(String directoryPathExtReg) {

        TradingDaysSpec tdspec = tsSpec.getTramoSpecification().getRegression().getCalendar().getTradingDays();
        if (tdspec == null) {
            tdspec = new TradingDaysSpec();
            tsSpec.getTramoSpecification().getRegression().getCalendar().setTradingDays(tdspec);
        }
        
        if (!isNull(model.getTradingdaysOption())) {
            switch (model.getTradingdaysOption()) {
                case "TradingDays":
                    tdspec.setTradingDaysType(TradingDaysType.TradingDays);
                    break;
                case "WorkingDays":
                    tdspec.setTradingDaysType(TradingDaysType.WorkingDays);
                    break;
                case "UserDefined":
                    List<DestSpecVarFromFileInfo> userDefVarFileInfoList = model.getUserDefVarFileInfo();
                    // PUNTO 1 - i tipi (usrdef.varType) sono uno per VARIABILE, nell'ordine dei file:
                    // il tipo di un file e' quello della sua prima variabile
                    int idxUsrDefVarTypes = 0;
                    List<String> usrdefVarTypes = model.getUsrdefVarType();
                    TsVariables vars = context.getTsVariables(EXTERNAL);
                    List<String> varNames = new ArrayList<>(); // AFTER
                    for (DestSpecVarFromFileInfo usrDefVar : userDefVarFileInfoList)
                    {
                        if (usrdefVarTypes.get(idxUsrDefVarTypes).equals("Calendar"))
                        {
                            Map<String, TsData> usrDefVariables;
                            String varNameForDescriptor = usrDefVar.getContainer().substring(0, usrDefVar.getContainer().lastIndexOf('.'));
                            if (!vars.contains(varNameForDescriptor) && !vars.contains(varNameForDescriptor + "_1"))
                            {
                                // not yet read
                                try {
                                    String startDate = usrDefVar.getStart();
                                    int startYear = Integer.parseInt(startDate.substring(0, 4));
                                    int startMonth = Integer.parseInt(startDate.substring(5, 7));
                                    
                                    //double[] extRegDataArray = ; // external regressor data
                                    //TsData extRegData = new TsData(TsFrequency.Monthly, startYear, startMonth-1, extRegDataArray, true);
                                    TsFrequency freq = TsFrequency.valueOf(model.getFrequency());
                                    usrDefVariables = ExtRegDataReaderTSplus.readTsFile(directoryPathExtReg, usrDefVar.getContainer(), freq, startYear, startMonth - 1);
                                    usrDefVar.setNumVar(usrDefVariables.size());
                                    for (Map.Entry<String, TsData> variable : usrDefVariables.entrySet()) {
                                        String key = variable.getKey();
                                        TsData value = variable.getValue();
                                        TsVariable var = new TsVariable(key, value); // KEY AS A DESC IS FUNDAMENTAL TO PLOT THE NAME!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                                        var.setName(key);
                                        vars.set(key, var);
                                        varNames.add(key);
                                        
                                    }
                                    System.out.print("External regressors loaded for Trading Days: ");
                                    System.out.println(varNameForDescriptor);
                                } catch (IOException ex) {
                                    // Regressore di calendario mancante o illeggibile: il programma si ferma
                                    // (prima l'errore veniva solo registrato e la serie era stimata senza regressore)
                                    throw new IllegalStateException("Serie " + model.getSeriesName()
                                            + ": lettura non riuscita del regressore di calendario "
                                            + usrDefVar.getContainer() + " - " + ex.getMessage(), ex);
                                }
                            } else if (usrDefVar.getNumVar() == 1) {
                                varNames.add(varNameForDescriptor);
                            } else {
                                for (int i = 1; i <= usrDefVar.getNumVar(); ++i) {
                                    StringBuilder builder = new StringBuilder();
                                    builder.append(varNameForDescriptor)
                                            .append('_').append(i);
                                    varNames.add(builder.toString());
                                }
                            }
                            String[] vDescStr = varNames.stream().map(name -> EXTERNAL + '.' +name).toArray(String[]::new);
                            tdspec.setUserVariables(vDescStr);
                            
                            this.tdVarNames = vDescStr; //ADDED
                        }
                        idxUsrDefVarTypes += numVarOf(usrDefVar); // PUNTO 1: avanza di tutte le variabili del file
                    }   break;
                default:
                    if (!model.getTradingdaysOption().equals("None") && !model.getTradingdaysOption().equals("NA")) {
                        System.out.println("tradingdays.option field has an unknown value");
                        tdspec.setTradingDaysType(TradingDaysType.None);
                        // Stop the program and throw Exception?
                    } else {
                        tdspec.setTradingDaysType(TradingDaysType.None);
                    }   break;
            }

        }

        tdspec.setAutomaticMethod(TradingDaysSpec.AutoMethod.valueOf(model.getTradingdaysMauto()));
        tdspec.setProbabibilityForFTest(model.getTradingdaysPftd());
        tdspec.setLeapYear(model.isTradingdaysLeapyear());
        int w = model.getTradingdaysStocktd();
        if (w != 0) {
            tdspec.setStockTradingDays(w);
        }
        tdspec.setTest(model.isTradingdaysTest());
        tdspec.setRegressionTestType(RegressionTestType.valueOf(model.getTradingdaysTest())); // Added 28/01/2025
    }

    private void setUserDefinedVariables(String directoryPathExtReg)
    {
        RegressionSpec regSpec = tsSpec.getTramoSpecification().getRegression();
        if (regSpec == null) 
        {
            regSpec = new RegressionSpec();
            tsSpec.getTramoSpecification().setRegression(regSpec);
        }

        List<DestSpecVarFromFileInfo> userDefVarFileInfoList = model.getUserDefVarFileInfo();
        List<String> usrdefVarTypes = model.getUsrdefVarType();

        if (isNull(userDefVarFileInfoList) || isNull(usrdefVarTypes)) 
        {
            return;
        }

        // PUNTO 1 - i tipi (usrdef.varType) sono uno per VARIABILE, nell'ordine dei file
        // (stessa convenzione dei coefficienti usrdef.varCoef): idxUsrDefVarTypes e' l'indice
        // globale della prima variabile del file corrente.
        int idxUsrDefVarTypes = 0;

        List<String> varDescrNames = new ArrayList<String>();
        for (DestSpecVarFromFileInfo usrDefVar : userDefVarFileInfoList) 
        {
            if (idxUsrDefVarTypes >= usrdefVarTypes.size())
            {
                System.out.println("usrdef.varType: nessun tipo per il file " + usrDefVar.getContainer() + ", file ignorato");
                idxUsrDefVarTypes += numVarOf(usrDefVar);
                continue;
            }

            if (!usrdefVarTypes.get(idxUsrDefVarTypes).equals("Calendar")) //Calendar are for trading days
            {
                LinkedHashMap<String, TsData> usrDefVariables = null;  // NOTA: LinkedHashMap invece di MAP anche nel ExtRegDataReader

                try 
                {
                    String startDate = usrDefVar.getStart();
                    if (!isValidDate(startDate)) {
                        System.out.println(startDate + " is not correctly formatted (yyyy-MM-dd)");
                        // exception (?)
                    }
                    int startYear  = Integer.parseInt(startDate.substring(0, 4)); //YYYY
                    int startMonth = Integer.parseInt(startDate.substring(5, 7)); //MM

                    TsFrequency freq = TsFrequency.valueOf(model.getFrequency());

                    usrDefVariables = ExtRegDataReaderTSplus.readTsFile(directoryPathExtReg, usrDefVar.getContainer(), freq, startYear, startMonth - 1);

                } catch (IOException ex) {
                    // Regressore mancante o illeggibile: il programma si ferma
                    throw new IllegalStateException("Serie " + model.getSeriesName()
                            + ": lettura non riuscita del regressore " + usrDefVar.getContainer()
                            + " - " + ex.getMessage(), ex);
                }

                if (usrDefVariables == null || usrDefVariables.isEmpty())
                {
                    throw new IllegalStateException("Serie " + model.getSeriesName()
                            + ": nessuna variabile letta dal file di regressori " + usrDefVar.getContainer());
                }
                if (usrDefVariables.size() != numVarOf(usrDefVar))
                {
                    System.out.println("Attenzione: " + usrDefVar.getContainer() + " contiene " + usrDefVariables.size()
                            + " variabili, ma n_var nel JSON e' " + usrDefVar.getNumVar());
                }
                usrDefVar.setNumVar(usrDefVariables.size());

                TsVariables vars = new TsVariables();
                String varNameForDescriptor = usrDefVar.getContainer().substring(0, usrDefVar.getContainer().lastIndexOf('.'));
                List<String> varNames = new ArrayList<String>();

                for (Map.Entry<String, TsData> variable : usrDefVariables.entrySet())
                {
                    String key = variable.getKey();
                    TsData value = variable.getValue();

                    TsVariable var = new TsVariable(value);
                    var.setName(key);
                    vars.set(key, var);
                    varNames.add(key);

                }
                // get filename without extension, to be used as vars Name
                if(!this.context.getTsVariableManagers().contains(varNameForDescriptor))
                {
                    this.context.getTsVariableManagers().set(varNameForDescriptor, vars);
                    System.out.print("External regressors loaded as UsrDef variables: ");
                    System.out.println(varNameForDescriptor);
                }    
                
                // PUNTO 1 - un descrittore per OGNI variabile del file (prima veniva aggiunta solo l'ultima),
                // con il tipo della variabile corrispondente (indice globale, non ripartendo da 0 per ogni file)
                for (int k = 0; k < varNames.size(); k++) 
                {
                    String fullName = varNameForDescriptor + "." + varNames.get(k);

                    TsVariableDescriptor vDesc = new TsVariableDescriptor();
                    vDesc.setName(fullName);
                    varDescrNames.add(fullName);

                    int typeIdx = idxUsrDefVarTypes + k;
                    String type = typeIdx < usrdefVarTypes.size() ? usrdefVarTypes.get(typeIdx) : null;
                    if (type != null) {
                        vDesc.setEffect(TsVariableDescriptor.UserComponentType.valueOf(type));
                    }
                    this.tsSpec.getTramoSpecification().getRegression().add(vDesc);
                }
            }
            idxUsrDefVarTypes += numVarOf(usrDefVar);
        }
        this.usrDefVarNames = varDescrNames.toArray(new String[0]);
    }

    // End "Added by Alessandro" block
    private void setEaster() {
        EasterSpec ieast = tsSpec.getTramoSpecification().getRegression().getCalendar().getEaster();
        if (ieast == null) {
            ieast = new EasterSpec();
            tsSpec.getTramoSpecification().getRegression().getCalendar().setEaster(ieast);
        }
        ieast.setOption(EasterSpec.Type.valueOf(model.getEasterType()));
        ieast.setDuration(model.getEasterDuration());
        ieast.setJulian(model.isEasterJulian());
        ieast.setTest(model.isEasterTest());

    }

    private void setOutliers() {
        // Alessandro
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date;
        String type;

        // Outlier definiti dall'utente: liste assenti, vuote o "NA" = nessun outlier
        List<String> outDates = model.getUsrdefOutliersDate();
        List<String> outTypes = model.getUsrdefOutliersType();
        boolean hasUserOutliers = outDates != null && !outDates.isEmpty() && !"NA".equals(outDates.get(0));
        if (hasUserOutliers) {
            if (outTypes == null || outTypes.size() != outDates.size()) {
                throw new IllegalArgumentException("usrdef.outliersDate (" + outDates.size()
                        + ") e usrdef.outliersType (" + (outTypes == null ? 0 : outTypes.size())
                        + ") hanno lunghezze diverse");
            }
            for (int i = 0; i < outDates.size(); i++) {
                try {
                    date = dateFormat.parse(outDates.get(i));
                    TsPeriod outlierPeriod = new TsPeriod(TsFrequency.valueOf(model.getFrequency()), date);

                    type = outTypes.get(i);
                    tsSpec.getTramoSpecification().getRegression().add(new OutlierDefinition(outlierPeriod, type));

                } catch (ParseException ex) {
                    throw new IllegalArgumentException("Data dell'outlier non valida: " + outDates.get(i), ex);
                }
            }
        }
        // end Alessandro's part

        if (model.isOutlierEnabled()) 
        {
            OutlierSpec o = tsSpec.getTramoSpecification().getOutliers();
            if (o == null) {
                o = new OutlierSpec();
                tsSpec.getTramoSpecification().setOutliers(o);
            }

            // Span di ricerca degli outlier: come in JD+ si applica allo span di STIMA
            // (All = tutto lo span di stima; First/Last/Excluding contano le osservazioni di quello span)
            o.setSpan(buildSpan("outlier", model.getOutlierFrom(), model.getOutlierTo(),
                    model.getOutlierFirst(), model.getOutlierLast(),
                    model.getOutlierExclFirst(), model.getOutlierExclLast()));

            o.setDeltaTC(model.getOutlierTcrate()); //Alessandro

            // Valore critico come in JDemetra+: 0 = automatico.
            // JD+ lo calcola a ogni stima sulla lunghezza della serie nello span di stima
            // (3.3 fino a 50 oss., 3.3 + 0.0025*(n-50) fino a 450, poi 4.3).
            if (model.isOutlierUsedefcv() || model.getOutlierCv() == 0.0) {
                o.setCriticalValue(0);
            } else if (model.getOutlierCv() >= 2) {
                o.setCriticalValue(model.getOutlierCv());
            } else {
                throw new IllegalArgumentException("outlier.cv = " + model.getOutlierCv()
                        + " non valido (deve essere 0 o almeno 2)");
            }

            // --- Versione precedente (valore critico calcolato e scritto esplicitamente nella spec,
            //     per vederlo nella GUI di JD+). Da riattivare con calculateCriticalValue().
            //     Nota: per coincidere con JD+ il numero di osservazioni deve essere quello dello
            //     span di stima, contato per intero:
            //     int nObsB = tsData.select(tsSpec.getTramoSpecification().getEstimate().getSpan()).getLength();
//          double CV=3.5;
//          TsPeriodSelector span = o.getSpan();
//          TsPeriod startPeriod = new TsPeriod(tsData.getFrequency(), span.getD0());
//          TsPeriod endPeriod   = new TsPeriod(tsData.getFrequency(), span.getD1());
//          int nObsB = endPeriod.minus(startPeriod);
//          if (!model.isOutlierUsedefcv())
//          {
//              if(model.getOutlierCv()!=0.0 && model.getOutlierCv()>=2) //&& !("NA".equals(model.getOutlierCv()))
//              {
//                  //o.setCriticalValue(model.getOutlierCv());
//                  CV = model.getOutlierCv();
//              }               
//          } else 
//          {
//              if(model.isOutlierEnabled())
//              {
//                  CV = calculateCriticalValue(nObsB);
//              }
//          }
//          o.setCriticalValue(CV);
            
            
            // Alessandro: Added !o.contains(OutlierType.AO) && in every if
            if (!o.contains(OutlierType.AO) && model.isOutlierAo()) {
                o.add(OutlierType.AO);
            }
            if (!o.contains(OutlierType.TC) && model.isOutlierTc()) {
                o.add(OutlierType.TC);
            }
            if (!o.contains(OutlierType.LS) && model.isOutlierLs()) {
                o.add(OutlierType.LS);
            }
            if (!o.contains(OutlierType.SO) && model.isOutlierSo()) {
                o.add(OutlierType.SO);
            }
            // Alessandro's block begin
            if (o.contains(OutlierType.AO) && !model.isOutlierAo()) {
                o.remove(OutlierType.AO);
            }
            if (o.contains(OutlierType.TC) && !model.isOutlierTc()) {
                o.remove(OutlierType.TC);
            }
            if (o.contains(OutlierType.LS) && !model.isOutlierLs()) {
                o.remove(OutlierType.LS);
            }
            if (o.contains(OutlierType.SO) && !model.isOutlierSo()) {
                o.remove(OutlierType.SO);
            }

            o.setEML(model.isOutlierEml());
        } else {
            // outlier.enabled = false: nessuna ricerca automatica, anche se la specifica di base
            // (es. RSA1..RSAfull) la prevede. Prima la ricerca restava attiva in questo caso.
            tsSpec.getTramoSpecification().setOutliers(new OutlierSpec());
        }
    }

    // CONVENZIONE: un coefficiente 0 nel JSON significa "non fissato" (stimato liberamente),
    // come in JDemetra+ 2, dove fissare un regressore a 0 non e' possibile.
    // Per ottenere l'effetto di "fissato a 0" bisogna escludere il regressore.
    // "NA" (anche in mezzo alla lista) significa "non fissato".
    private void fixOutliersAndVariablesCoefficients() {
//   E.G. names: "Java-Object{{AO (2020-03-01)=[D@4ff3af97, AO (2020-06-01)=[D@5680f009, AO (2007-12-01)=[D@3a4e6da6, _ts_external_3@LYM_02_0=[D@73e0c775, _ts_external_2@TDU02M_0=[D@213d5189}}"
//   return "_ts_external_3@LYM_02_0" "_ts_external_2@TDU02M_0"

        List<String> usrDefOutlierCoefs = model.getUsrdefOutliersCoef();
        List<String> usrDefVarCoefs     = model.getUsrdefVarCoef();
        List<String> usrDefVarTypes     = model.getUsrdefVarType();
        List<Double> tdCoef         = getUsrDefCoefTd(usrDefVarCoefs, usrDefVarTypes);
        List<Double> usrDefVarsCoef = getUsrDefCoefVars(usrDefVarCoefs, usrDefVarTypes);

        // PUNTO 3 - outlier: ogni coefficiente e' letto singolarmente, "NA" = non fissato
        if (usrDefOutlierCoefs != null && !usrDefOutlierCoefs.isEmpty())
        {
            OutlierDefinition[] outs = tsSpec.getTramoSpecification().getRegression().getOutliers();
            for (int i = 0; i < outs.length && i < usrDefOutlierCoefs.size(); i++)
            {
                Double coef = parseCoef(usrDefOutlierCoefs.get(i));
                if (coef != null && coef != 0)
                {
                    String outName = transformOutlierStringFromOutlierDef(outs[i].toString());
                    tsSpec.getTramoSpecification().getRegression().setFixedCoefficients(outName, new double[]{coef});
                }
            }
        }
        
        int j=0;
        for(String vName : this.tdVarNames)
        {   

            vName = "td|"+vName.replaceAll("\\.", "@");
            
            if(j<tdCoef.size())
            {    
                Double varVal = tdCoef.get(j);    // null = NA = non fissato
                
                if(varVal != null && varVal != 0)
                {
                    tsSpec.getTramoSpecification().getRegression().setFixedCoefficients(vName, new double[]{varVal});
                }    
               
                j++;
            }

        }
        
        j=0;
        for(String vName : this.usrDefVarNames)
        {   
            vName = vName.replaceAll("\\.", "@");
            
            if(j<usrDefVarsCoef.size())
            {    
                Double varVal = usrDefVarsCoef.get(j);    // null = NA = non fissato
                
                if(varVal != null && varVal != 0)
                {
                    tsSpec.getTramoSpecification().getRegression().setFixedCoefficients(vName, new double[]{varVal});
                }    
                j++;
            }

        }
    }

    /** PUNTO 3 - "NA", vuoto o null -> null (non fissato); altrimenti il valore numerico. */
    private static Double parseCoef(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "NA".equalsIgnoreCase(t)) {
            return null;
        }
        return Double.parseDouble(t);
    }

    private void setAutoModeling() {
        AutoModelSpec aspec = tsSpec.getTramoSpecification().getAutoModel();
        if (aspec == null) {
            aspec = new AutoModelSpec();
            tsSpec.getTramoSpecification().setAutoModel(aspec);
        }

        aspec.setTsig(model.getAutomdlArmalimit()); 
        aspec.setPc(model.getAutomdlReducecv()); 
        aspec.setPcr(model.getAutomdlLjungboxlimit()); 
        aspec.setEnabled(model.isAutomdlEnabled());
        aspec.setAcceptDefault(model.isAutomdlAcceptdefault());
        aspec.setCancel(model.getAutomdlCancel());
        aspec.setUb1(model.getAutomdlUb1());
        aspec.setUb2(model.getAutomdlUb2());
        aspec.setAmiCompare(model.isAutomdlCompare());

    }

    private void setArima() 
    {
        ArimaSpec aspec = tsSpec.getTramoSpecification().getArima();

        if (aspec == null) {
            aspec = new ArimaSpec();
            tsSpec.getTramoSpecification().setArima(aspec);
        }

        List<String> arimaCoefs     = model.getArimaCoef();
        List<String> arimaCoefTypes = model.getArimaCoefType();
        int p  = model.getArimaP();
        int d  = model.getArimaD();
        int q  = model.getArimaQ();
        int bp = model.getArimaBP();
        int bd = model.getArimaBD();
        int bq = model.getArimaBQ();

        aspec.setMean(model.isArimaMu());
        // Media fissata (estensione ISTAT, non disponibile nella GUI di JD+): solo se la media e' nel modello
        if (model.isArimaMu() && isPresent(model.getArimaMuFixedValue())) {
            try {
                aspec.setMu(new Parameter(Double.parseDouble(model.getArimaMuFixedValue().trim()), ParameterType.Fixed));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("arima.muFixedValue non numerico: " + model.getArimaMuFixedValue(), ex);
            }
        }
        aspec.setP(p);
        aspec.setD(d);
        aspec.setQ(q);
        aspec.setBP(bp);
        aspec.setBD(bd);
        aspec.setBQ(bq);

        if (arimaCoefs != null && !("NA".equals(arimaCoefs.get(0)) && arimaCoefs.size() == 1))
        {
            List<List<ArimaCoefficient>> coefsLists = splitArimaCoefficients(arimaCoefs, arimaCoefTypes, p, bp, q, bq);

            if (p > 0)  aspec.setPhi(buildParameters(p,  coefsLists.get(0), "phi"));
            if (q > 0)  aspec.setTheta(buildParameters(q,  coefsLists.get(1), "theta"));
            if (bp > 0) aspec.setBPhi(buildParameters(bp, coefsLists.get(2), "bphi"));
            if (bq > 0) aspec.setBTheta(buildParameters(bq, coefsLists.get(3), "btheta"));
        }
    }

    /**
     * Crea l'array dei parametri ARIMA di lunghezza 'order'.
     * Tutte le posizioni sono inizializzate (Undefined = stimato liberamente),
     * cosi' non restano elementi null.
     */
    private Parameter[] buildParameters(int order, List<ArimaCoefficient> coefs, String label)
    {
        Parameter[] params = new Parameter[order];
        for (int k = 0; k < order; k++) {
            params[k] = new Parameter();                 // Undefined
        }
        for (ArimaCoefficient c : coefs) {
            int idx = c.getIndex() - 1;
            if (idx < 0 || idx >= order) {
                System.err.println(label + "(" + c.getIndex() + ") fuori dall'ordine " + order + ", ignorato");
                continue;
            }
            String coef = c.getCoef();
            boolean hasValue = coef != null && !"NA".equals(coef);
            switch (c.getType()) {
                case "Fixed":
                    if (hasValue) {
                        params[idx].setType(ParameterType.Fixed);
                        params[idx].setValue(Double.parseDouble(coef));
                    }
                    break;
                case "Initial":
                    if (hasValue) {
                        params[idx].setType(ParameterType.Initial);
                        params[idx].setValue(Double.parseDouble(coef));
                    }
                    break;
                default:                                  // "Undefined": resta stimato
                    break;
            }
        }
        return params;
    }

    private void setSeats() {
        SeatsSpecification sspec = tsSpec.getSeatsSpecification();
        if (sspec == null) {
            sspec = new SeatsSpecification();
            tsSpec.setSeatsSpecification(sspec);
        }
        sspec.setXlBoundary(model.getSeatsMaBoundary()); 
        sspec.setPredictionLength(model.getSeatsPredictionLength());
        sspec.setApproximationMode(SeatsSpecification.ApproximationMode.valueOf(model.getSeatsApprox()));
        sspec.setTrendBoundary(model.getSeatsTrendBoundary());
        sspec.setSeasBoundary(model.getSeatsSeasdBoundary());
        sspec.setSeasBoundary1(model.getSeatsSeasdBoundary1());
        sspec.setSeasTolerance(model.getSeatsSeasTol());
        sspec.setMethod(SeatsSpecification.EstimationMethod.valueOf(model.getSeatsMethod()));
    }
    // Method added by Alessandro
    private boolean isValidDate(String dateStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            // Try to parse the string
            LocalDate date = LocalDate.parse(dateStr, formatter);
            // If the Date is correctly parsed, then the string is correctly formatted
            return true;
        } catch (DateTimeParseException e) {
            // If an exception is catched, the Date is not well formatted
            return false;
        }
    }

    // Aggiunta - Added - Nuovo
    private void setRamps()
    {
        List<RampsInfo> ramps = model.getRamps(); 
          
        if(ramps != null)
        {    
            Day dayStart, dayEnd;
            Date dateStart, dateEnd;
            Ramp rp;
            ArrayList<Ramp> rampsList = new ArrayList<Ramp>();
            String rampName;

            int year_s, month_s, day_s, year_e, month_e, day_e;

            for(RampsInfo r : ramps)
            {   
                year_s  = Integer.parseInt(r.getStart().substring(0, 4));//substr(ramp$start, 1, 4))
                month_s = Integer.parseInt(r.getStart().substring(5, 7));
                day_s   = Integer.parseInt(r.getStart().substring(8, 10));
                // day_start_int = Day.calc(year_s, month_s-1, day_s-1));

                dateStart = new Date(year_s-1900, month_s-1, day_s);
                dayStart  = new Day(dateStart);

                year_e  = Integer.parseInt(r.getEnd().substring(0, 4));//substr(ramp$start, 1, 4))
                month_e = Integer.parseInt(r.getEnd().substring(5, 7));
                day_e   = Integer.parseInt(r.getEnd().substring(8, 10));

                dateEnd = new Date(year_e-1900, month_e-1, day_e);
                dayEnd  = new Day(dateEnd);

                rp = new Ramp(dayStart, dayEnd);

                rampsList.add(rp);

                if(r.getFixed_coef()!=0.0)
                {
                    rampName = "rp$" + r.getStart() + "$" + r.getEnd();
                    tsSpec.getTramoSpecification().getRegression().setFixedCoefficients(rampName, new double[]{r.getFixed_coef()});
                }    
            }

            Ramp[] rampsArray = rampsList.toArray(new Ramp[0]);
            tsSpec.getTramoSpecification().getRegression().setRamps(rampsArray);
        }

    }        
    
    private void setInterventionVariables()
    {
        List<InterventionVariablesInfo> ivs = model.getInterventionVariables(); 
        if(ivs != null)
        {    
            Day dayStart, dayEnd;
            Date dateStart, dateEnd;
            InterventionVariable interventionVar = null;

            int year_s, month_s, day_s, year_e, month_e, day_e;
            //List<Sequence> seqList=new ArrayList<Sequence>();

            for(InterventionVariablesInfo iv : ivs)
            {   
                interventionVar = new InterventionVariable();
                interventionVar.setDelta(iv.getDelta());
                interventionVar.setDeltaS(iv.getDelta_s());
                if(iv.getD1DS()) //because setting of D1DS resets all the deltas
                {
                    interventionVar.setD1DS(iv.getD1DS());
                }    


                List<SequenceInfo> seqs = iv.getSeq();
                for(SequenceInfo s : seqs)
                {
                    year_s  = Integer.parseInt(s.getStart().substring(0, 4));//substr(ramp$start, 1, 4))
                    month_s = Integer.parseInt(s.getStart().substring(5, 7));
                    day_s   = Integer.parseInt(s.getStart().substring(8, 10));
                    // day_start_int = Day.calc(year_s, month_s-1, day_s-1));

                    dateStart = new Date(year_s-1900, month_s-1, day_s);
                    dayStart  = new Day(dateStart);

                    year_e  = Integer.parseInt(s.getEnd().substring(0, 4));//substr(ramp$start, 1, 4))
                    month_e = Integer.parseInt(s.getEnd().substring(5, 7));
                    day_e   = Integer.parseInt(s.getEnd().substring(8, 10));

                    dateEnd = new Date(year_e-1900, month_e-1, day_e);
                    dayEnd  = new Day(dateEnd);

                    interventionVar.add(dayStart, dayEnd);
                    
                    // Fix coefficients
                    if(iv.getFixed_coef()!=0.0)
                    {
                        String deltaStr  = TSmodelSetup.getDeltaStr(iv);
                        String ivName = "{I:" + s.getStart() + "$" + s.getEnd()+"}"+deltaStr;
                        tsSpec.getTramoSpecification().getRegression().setFixedCoefficients(ivName, new double[]{iv.getFixed_coef()});
                    } 
                    
                }    
                tsSpec.getTramoSpecification().getRegression().add(interventionVar);
            }
        }
    }
    
    
    private void fixDefaultJDplusCalendarCoefficients() 
    {
        double easterCoef = model.getEasterCoef();
        if(easterCoef!=0.0)
        {    
            tsSpec.getTramoSpecification().getRegression().setFixedCoefficients("easter", new double[]{easterCoef});
        }
        
    }
    
    

    public static String transformOutlierStringFromOutlierDef(String input) {
        // Verifica che la stringa sia del formato corretto
        if (input == null || !input.matches("^[A-Za-z]+\\.\\d{4}-\\d{2}-\\d{2}$")) {
            throw new IllegalArgumentException("Formato della stringa non valido.");
        }

        // Estrai il prefisso (es. TC, AO) e la data (es. 2001-10-01)
        String[] parts = input.split("\\.");
        String outPrefix = parts[0];
        String dateString = parts[1];

        // Parsing della data
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        try {
            Date date = dateFormat.parse(dateString);
            String formattedDate = outputDateFormat.format(date);

            // Cambia il mese in base al formato richiesto
            String[] dateParts = formattedDate.split("-");
            String year  = dateParts[0];
            String month = dateParts[1];
            String day   = dateParts[2];


            // Se il mese è tra 10 e 12, usiamo il mese con due cifre (10, 11, 12)
//            if (Integer.parseInt(month) < 10) {
//                month = String.valueOf(Integer.parseInt(month));  // Rimuove lo zero iniziale se presente
//            }

            // Formatta l'output come richiesto
            return outPrefix + " (" + year + "-" + month + "-" + day + ")";
        } catch (ParseException e) {
            throw new IllegalArgumentException("Errore nel parsing della data.", e);
        }
    }    
    
    public static String[] filterStringsStartingWithTd(String[] input) {
        if (input == null) {
            return new String[0];  // Restituisce un array vuoto se l'input è nullo
        }
        
        List<String> filteredList = new ArrayList<>();
        
        for (String str : input) {
            if (str != null && str.startsWith("td|")) {
                filteredList.add(str);
            }
        }
        
        // Converte la lista filtrata in un array di stringhe
        return filteredList.toArray(new String[0]);
    }
    
    
    public static List<Double> getUsrDefCoefTd(List<String> usrDefVarCoefs, List<String> usrDefVarTypes) {
        
        List<Double> usrDefVarCoefsTd = new ArrayList<Double>();

        // PUNTO 3 - un "NA" in mezzo alla lista non blocca piu' il programma: diventa null (non fissato)
        if (usrDefVarCoefs == null || usrDefVarTypes == null || usrDefVarTypes.isEmpty() || "NA".equals(usrDefVarTypes.get(0))) {
            return usrDefVarCoefsTd;  // Restituisce una lista vuota se uno degli input è nullo
        }

        // Itera sulle liste fino alla lunghezza minima per evitare IndexOutOfBoundsException
        int minSize = Math.min(usrDefVarCoefs.size(), usrDefVarTypes.size());
        for (int i = 0; i < minSize; i++) {
            if ("calendar".equalsIgnoreCase(usrDefVarTypes.get(i))) {
                usrDefVarCoefsTd.add(parseCoef(usrDefVarCoefs.get(i)));
            }
        }
        return usrDefVarCoefsTd;
    }
 
    
    
    public static List<Double> getUsrDefCoefVars(List<String> usrDefVarCoefs, List<String> usrDefVarTypes) {
        
        List<Double> usrDefVarCoefsVars = new ArrayList<Double>();

        // PUNTO 3 - un "NA" in mezzo alla lista non blocca piu' il programma: diventa null (non fissato)
        if (usrDefVarCoefs == null || usrDefVarTypes == null || usrDefVarTypes.isEmpty() || "NA".equals(usrDefVarTypes.get(0))) {
            return usrDefVarCoefsVars;  // Restituisce una lista vuota se uno degli input è nullo
        }

        // Itera sulle liste fino alla lunghezza minima per evitare IndexOutOfBoundsException
        int minSize = Math.min(usrDefVarCoefs.size(), usrDefVarTypes.size());
        for (int i = 0; i < minSize; i++) {
            if (!"calendar".equalsIgnoreCase(usrDefVarTypes.get(i))) {
                usrDefVarCoefsVars.add(parseCoef(usrDefVarCoefs.get(i)));
            }
        }
        return usrDefVarCoefsVars;
    }

    public double calculateCriticalValue(int numberOfObservations)
    {
        double CV;
        
        if (numberOfObservations <= 50) {
            CV = 3.3;
        } else if (numberOfObservations >= 450) {
            CV = 4.3;
        } else {
            CV = 3.3 + 0.0025 * (numberOfObservations- 50);
        }
                
        return CV;
    }

    // NOT TO BE USED
    public double adjustOutliersCV(Double CV)
    {
       
        if(CV != null && this.model.isOutlierEnabled())
        {
            CV = (1- this.model.getAutomdlReducecv()) * CV;

        }
        System.out.println("Automatic outliers CV computed: "+ CV);
        this.getTsSpec().getTramoSpecification().getOutliers().setCriticalValue(CV);

        
        return CV;
    }        

    private static String getDeltaStr(InterventionVariablesInfo iv)
    {
        boolean deltaPresent  = (iv.getDelta()   != 0.0);
        boolean deltaSpresent = (iv.getDelta_s() != 0.0);
        String result=null;
        
        if(!deltaPresent && !deltaSpresent)
        {
            result = "";
        } else if(!deltaPresent && deltaSpresent)
        {
            result = "(deltas="+Double.toString(iv.getDelta_s()).replace('.', ',')+")";
        } else if(deltaPresent && !deltaSpresent)   // PUNTO 4: prima era la stessa condizione del ramo precedente
        {
            result = "(delta="+Double.toString(iv.getDelta()).replace('.', ',')+")";       
        } else if(deltaPresent && deltaSpresent)
        {
            result = "(delta="+Double.toString(iv.getDelta()).replace('.', ',')+", deltas="+Double.toString(iv.getDelta_s()).replace('.', ',')+")";       
        }    

        return result;
    }


    
}