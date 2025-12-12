package src;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class DestSpecificationsModel {
 
    //private TramoSeatsSpecification tsSpec=null;
    //private X13Specification x13Spec=null;
    
    @JsonProperty("series_name")
    private String seriesName;
    
    @JsonProperty("series_start")
    private String seriesStart;
    @JsonProperty("series_end")
    private String seriesEnd;
 
    @JsonProperty("frequency")
    private int frequency;
    @JsonProperty("method")
    private String method;
    @JsonProperty("userdef.varFromFile")
    private boolean userdefVarFromFile;
    @JsonProperty("userdef.varFromFile.infoList")
    private List<DestSpecVarFromFileInfo> userDefVarFileInfo;
    @JsonProperty("spec")
    private String spec;
    @JsonProperty("preliminary.check")
    private boolean preliminaryCheck;
    @JsonProperty("estimate.from")
    private String estimateFrom;
    @JsonProperty("estimate.to")
    private String estimateTo;
    @JsonProperty("estimate.first")
    @JsonDeserialize(using = StringOrNumberDeserializer.class)
    private String estimateFirst;
    @JsonProperty("estimate.last")
    @JsonDeserialize(using = StringOrNumberDeserializer.class)
    private String estimateLast;
    @JsonProperty("estimate.exclFirst")
    private int estimateExclFirst;
    @JsonProperty("estimate.exclLast")
    private int estimateExclLast;
    @JsonProperty("estimate.tol")
    private double estimateTol;
    @JsonProperty("estimate.eml")
    private boolean estimateEml;
    @JsonProperty("estimate.urfinal")
    private double estimateUrfinal;
    @JsonProperty("transform.function")
    private String transformFunction;
    @JsonProperty("transform.fct")
    private double transformFct;
    @JsonProperty("usrdef.outliersEnabled")
    private boolean usrdefOutliersEnabled;
    @JsonProperty("usrdef.outliersType")
    private List<String> usrdefOutliersType;
    @JsonProperty("usrdef.outliersDate")
    private List<String> usrdefOutliersDate;
    @JsonProperty("usrdef.outliersCoef")
    private List<String> usrdefOutliersCoef; 
    @JsonProperty("usrdef.varEnabled")
    private boolean usrdefVarEnabled;
    @JsonProperty("usrdef.var")
    private String usrdefVar;
    @JsonProperty("usrdef.varType")
    private List<String> usrdefVarType;
    @JsonProperty("usrdef.varCoef")
    private List<String> usrdefVarCoef; 
    @JsonProperty("tradingdays.mauto")
    private String tradingdaysMauto;
    @JsonProperty("tradingdays.pftd")
    private double tradingdaysPftd;
    @JsonProperty("tradingdays.option") 
    private String tradingdaysOption;
    @JsonProperty("tradingdays.leapyear")
    private boolean tradingdaysLeapyear;
    @JsonProperty("tradingdays.stocktd")
    private int tradingdaysStocktd;
    @JsonProperty("tradingdays.test")
    private String tradingdaysTest;
    private boolean tradingdaysTestB;
    @JsonProperty("easter.type")
    private String easterType;
    @JsonProperty("easter.julian")
    private boolean easterJulian;
    @JsonProperty("easter.duration")
    private int easterDuration;
    @JsonProperty("easter.test")
    private boolean easterTest;
    @JsonProperty("outlier.enabled")
    private boolean outlierEnabled;
    @JsonProperty("outlier.from")
    private String outlierFrom;
    @JsonProperty("outlier.to")
    private String outlierTo;
    @JsonProperty("outlier.first")
    @JsonDeserialize(using = StringOrNumberDeserializer.class)
    private String outlierFirst;
    @JsonProperty("outlier.last")
    @JsonDeserialize(using = StringOrNumberDeserializer.class)
    private String outlierLast;
    @JsonProperty("outlier.exclFirst")
    private int outlierExclFirst;
    @JsonProperty("outlier.exclLast")
    private int outlierExclLast;
    @JsonProperty("outlier.ao")
    private boolean outlierAo;
    @JsonProperty("outlier.tc")
    private boolean outlierTc;
    @JsonProperty("outlier.ls")
    private boolean outlierLs;
    @JsonProperty("outlier.so")
    private boolean outlierSo;
    @JsonProperty("outlier.usedefcv") 
    private boolean outlierUsedefcv;
    @JsonProperty("outlier.cv")
    private double outlierCv;
    @JsonProperty("outlier.eml")
    private boolean outlierEml;
    @JsonProperty("outlier.tcrate") 
    private double outlierTcrate;
    @JsonProperty("automdl.enabled")
    private boolean automdlEnabled;
    @JsonProperty("automdl.acceptdefault")
    private boolean automdlAcceptdefault;
    @JsonProperty("automdl.cancel")
    private double automdlCancel;
    @JsonProperty("automdl.ub1")
    private double automdlUb1;
    @JsonProperty("automdl.ub2")
    private double automdlUb2;
    @JsonProperty("automdl.armalimit") 
    private double automdlArmalimit;
    @JsonProperty("automdl.reducecv")
    private double automdlReducecv;
    @JsonProperty("automdl.ljungboxlimit")
    private double automdlLjungboxlimit;
    @JsonProperty("automdl.compare")
    private boolean automdlCompare;
    @JsonProperty("arima.mu")
    private boolean arimaMu;
    @JsonProperty("arima.p")
    private int arimaP;
    @JsonProperty("arima.d")
    private int arimaD;
    @JsonProperty("arima.q")
    private int arimaQ;
    @JsonProperty("arima.bp")
    private int arimaBP;
    @JsonProperty("arima.bd")
    private int arimaBD;
    @JsonProperty("arima.bq")
    private int arimaBQ;
    @JsonProperty("arima.coefEnabled") 
    private boolean arimaCoefEnabled;
    @JsonProperty("arima.coef") 
    private List<String> arimaCoef;
    @JsonProperty("arima.coefType") 
    private List<String> arimaCoefType;
    @JsonProperty("fcst.horizon") 
    private double fcstHorizon;
    @JsonProperty("seats.predictionLength")
    private int seatsPredictionLength;
    @JsonProperty("seats.approx")
    private String seatsApprox;
    @JsonProperty("seats.trendBoundary")
    private double seatsTrendBoundary;
    @JsonProperty("seats.seasdBoundary")
    private double seatsSeasdBoundary;
    @JsonProperty("seats.seasdBoundary1")
    private double seatsSeasdBoundary1;
    @JsonProperty("seats.seasTol")
    private double seatsSeasTol;
    @JsonProperty("seats.maBoundary") 
    private double seatsMaBoundary;
    @JsonProperty("seats.method")
    private String seatsMethod;
    @JsonDeserialize(using = RampsInfoDeserializer.class) // per gestire ramps dal duplice valore: ramps="NA" o List<RampsInfo>
    @JsonProperty("ramps") // nuovo
    private List<RampsInfo> ramps;    
    @JsonDeserialize(using = InterventionVariablesInfoDeserializer.class) // per gestire ramps dal duplice valore: ramps="NA" o List<RampsInfo>
    @JsonProperty("intervention_variables") // nuovo
    private List<InterventionVariablesInfo> interventionVariables;
    @JsonProperty("easterCoef") // nuovo
    private double easterCoef;

    
    @JsonCreator
    public DestSpecificationsModel(
            @JsonProperty("method") String method,
            @JsonProperty("spec") String spec
    ){
        super();
        this.method=method;
        this.spec=spec;
        switch (this.method) {
         case "TS":
             //this.tsSpec=TramoSeatsSpecification.fromString(spec);
             break;
         case "X":
             //this.x13Spec=X13Specification.fromString(spec);
             break;
         default:
             //this.tsSpec=TramoSeatsSpecification.fromString(spec);
        }
    }
    
    private boolean isNull(String valore) {
        return (valore==null || valore.trim().isEmpty() || "NA".equals(valore.trim().toUpperCase()));
    }

    /*public TramoSeatsSpecification getTsSpec() {
        return tsSpec;
    }

    public X13Specification getX13Spec() {
        return x13Spec;
    }*/
    
    public void setupModel() {
        switch (this.method) {
         case "TS":
             setupTSmodel();
             break;
         case "X":
             setupX13model();
             break;
         default:
             setupTSmodel();
        }        
    }
    
    private void setupTSmodel() {
        /*setTransform();
        setEstimate();
        setTradingDays();
        setEaster();
        setOutliers();
        setAutoModeling();
        setArima();
        setSeats();*/
    }
    
    private void setupX13model() {
        
    }
    
    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getSeriesStart() {
        return seriesStart;
    }

    public void setSeriesStart(String seriesStart) {
        this.seriesStart = seriesStart;
    }
    
    public String getSeriesEnd() {
        return seriesEnd;
    }

    public void setSeriesEnd(String seriesEnd) {
        this.seriesEnd = seriesEnd;
    }
    
    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public boolean isUserdefVarFromFile() {
        return userdefVarFromFile;
    }

    public void setUserdefVarFromFile(boolean userdefVarFromFile) {
        this.userdefVarFromFile = userdefVarFromFile;
    }

    public List<DestSpecVarFromFileInfo> getUserDefVarFileInfo() {
        return userDefVarFileInfo;
    }

    public void setUserDefVarFileInfo(List<DestSpecVarFromFileInfo> userDefVarFileInfo) {
        this.userDefVarFileInfo = userDefVarFileInfo;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = isNull(spec)?null:spec;
    }

    public boolean isPreliminaryCheck() {
        return preliminaryCheck;
    }

    public void setPreliminaryCheck(boolean preliminaryCheck) {
        this.preliminaryCheck = preliminaryCheck;
    }

    public String getEstimateFrom() {
        return estimateFrom;
    }

    public void setEstimateFrom(String estimateFrom) {
        this.estimateFrom = isNull(estimateFrom)?null:estimateFrom;
    }

    public String getEstimateTo() {
        return estimateTo;
    }

    public void setEstimateTo(String estimateTo) {
        this.estimateTo = isNull(estimateTo)?null:estimateTo;
    }

    public String getEstimateFirst() {
        return estimateFirst;
    }

    public void setEstimateFirst(String estimateFirst) {
        this.estimateFirst = estimateFirst;
    }

    public String getEstimateLast() {
        return estimateLast;
    }

    public void setEstimateLast(String estimateLast) {
        this.estimateLast = estimateLast;
    }

    public int getEstimateExclFirst() {
        return estimateExclFirst;
    }

    public void setEstimateExclFirst(int estimateExclFirst) {
        this.estimateExclFirst = estimateExclFirst;
    }

    public int getEstimateExclLast() {
        return estimateExclLast;
    }

    public void setEstimateExclLast(int estimateExclLast) {
        this.estimateExclLast = estimateExclLast;
    }

    public double getEstimateTol() {
        return estimateTol;
    }

    public void setEstimateTol(double estimateTol) {
        this.estimateTol = estimateTol;
    }

    public boolean isEstimateEml() {
        return estimateEml;
    }

    public void setEstimateEml(boolean estimateEml) {
        this.estimateEml = estimateEml;
    }

    public double getEstimateUrfinal() {
        return estimateUrfinal;
    }

    public void setEstimateUrfinal(double estimateUrfinal) {
        this.estimateUrfinal = estimateUrfinal;
    }

    public String getTransformFunction() {
        return transformFunction;
    }

    public void setTransformFunction(String transformFunction) {
        this.transformFunction = transformFunction;
    }

    public double getTransformFct() {
        return transformFct;
    }

    public void setTransformFct(double transformFct) {
        this.transformFct = transformFct;
    }

    public boolean isUsrdefOutliersEnabled() {
        return usrdefOutliersEnabled;
    }

    public void setUsrdefOutliersEnabled(boolean usrdefOutliersEnabled) {
        this.usrdefOutliersEnabled = usrdefOutliersEnabled;
    }

    public List<String> getUsrdefOutliersType() {
        return usrdefOutliersType;
    }

    public void setUsrdefOutliersType(List<String> usrdefOutliersType) {
        this.usrdefOutliersType = usrdefOutliersType;
    }

    public List<String> getUsrdefOutliersDate() {
        return usrdefOutliersDate;
    }

    public void setUsrdefOutliersDate(List<String> usrdefOutliersDate) {
        this.usrdefOutliersDate = usrdefOutliersDate;
    }

    public List<String> getUsrdefOutliersCoef() {
        return usrdefOutliersCoef;
    }

    public void setUsrdefOutliersCoef(List<String> usrdefOutliersCoef) {
        this.usrdefOutliersCoef = usrdefOutliersCoef;
    }

    public boolean isUsrdefVarEnabled() {
        return usrdefVarEnabled;
    }

    public void setUsrdefVarEnabled(boolean usrdefVarEnabled) {
        this.usrdefVarEnabled = usrdefVarEnabled;
    }

    public String getUsrdefVar() {
        return usrdefVar;
    }

    public void setUsrdefVar(String usrdefVar) {
        this.usrdefVar = usrdefVar;
    }

    public List<String> getUsrdefVarType() {
        return usrdefVarType;
    }

    public void setUsrdefVarType(List<String> usrdefVarType) {
        this.usrdefVarType = usrdefVarType;
    }

    public List<String> getUsrdefVarCoef() { //Alessandro
        return usrdefVarCoef;
    }

    public void setUsrdefVarCoef(List<String> usrdefVarCoef) { //Alessandro
        this.usrdefVarCoef = usrdefVarCoef;
    }

    public String getTradingdaysMauto() {
        return tradingdaysMauto;
    }

    public void setTradingdaysMauto(String tradingdaysMauto) {
        this.tradingdaysMauto = tradingdaysMauto;
    }

    public double getTradingdaysPftd() {
        return tradingdaysPftd;
    }

    public void setTradingdaysPftd(double tradingdaysPftd) {
        this.tradingdaysPftd = tradingdaysPftd;
    }

    public String getTradingdaysOption() {
        return tradingdaysOption;
    }

    public void setTradingdaysOption(String tradingdaysOption) {
        this.tradingdaysOption = tradingdaysOption;
    }

    public boolean isTradingdaysLeapyear() {
        return tradingdaysLeapyear;
    }

    public void setTradingdaysLeapyear(boolean tradingdaysLeapyear) {
        this.tradingdaysLeapyear = tradingdaysLeapyear;
    }

    public int getTradingdaysStocktd() {
        return tradingdaysStocktd;
    }

    public void setTradingdaysStocktd(int tradingdaysStocktd) {
        this.tradingdaysStocktd = tradingdaysStocktd;
    }

    public boolean isTradingdaysTest() {
        return tradingdaysTestB;
    }

    public void setTradingdaysTest(String tradingdaysTest) {
        this.tradingdaysTest = tradingdaysTest;
        tradingdaysTestB=(!isNull(tradingdaysTest) && "true".equals(tradingdaysTest.toLowerCase()));
    }
    
    public String getTradingdaysTest() {
        return(tradingdaysTest);
    }

    public String getEasterType() {
        return easterType;
    }

    public void setEasterType(String easterType) {
        this.easterType = easterType;
    }

    public boolean isEasterJulian() {
        return easterJulian;
    }

    public void setEasterJulian(boolean easterJulian) {
        this.easterJulian = easterJulian;
    }

    public int getEasterDuration() {
        return easterDuration;
    }

    public void setEasterDuration(int easterDuration) {
        this.easterDuration = easterDuration;
    }

    public boolean isEasterTest() {
        return easterTest;
    }

    public void setEasterTest(boolean easterTest) {
        this.easterTest = easterTest;
    }

    public boolean isOutlierEnabled() {
        return outlierEnabled;
    }

    public void setOutlierEnabled(boolean outlierEnabled) {
        this.outlierEnabled = outlierEnabled;
    }

    public String getOutlierFrom() {
        return outlierFrom;
    }

    public void setOutlierFrom(String outlierFrom) {
        this.outlierFrom = isNull(outlierFrom)?null:outlierFrom;
    }

    public String getOutlierTo() {
        return outlierTo;
    }

    public void setOutlierTo(String outlierTo) {
        this.outlierTo = isNull(outlierTo)?null:outlierTo;
    }

    public String getOutlierFirst() {
        return outlierFirst;
    }

    public void setOutlierFirst(String outlierFirst) {
        this.outlierFirst = outlierFirst;
    }

    public String getOutlierLast() {
        return outlierLast;
    }

    public void setOutlierLast(String outlierLast) {
        this.outlierLast = outlierLast;
    }

    public int getOutlierExclFirst() {
        return outlierExclFirst;
    }

    public void setOutlierExclFirst(int outlierExclFirst) {
        this.outlierExclFirst = outlierExclFirst;
    }

    public int getOutlierExclLast() {
        return outlierExclLast;
    }

    public void setOutlierExclLast(int outlierExclLast) {
        this.outlierExclLast = outlierExclLast;
    }

    public boolean isOutlierAo() {
        return outlierAo;
    }

    public void setOutlierAo(boolean outlierAo) {
        this.outlierAo = outlierAo;
    }

    public boolean isOutlierTc() {
        return outlierTc;
    }

    public void setOutlierTc(boolean outlierTc) {
        this.outlierTc = outlierTc;
    }

    public boolean isOutlierLs() {
        return outlierLs;
    }

    public void setOutlierLs(boolean outlierLs) {
        this.outlierLs = outlierLs;
    }

    public boolean isOutlierSo() {
        return outlierSo;
    }

    public void setOutlierSo(boolean outlierSo) {
        this.outlierSo = outlierSo;
    }

    public boolean isOutlierUsedefcv() {
        return outlierUsedefcv;
    }

    public void setOutlierUsedefcv(boolean outlierUsedefcv) {
        this.outlierUsedefcv = outlierUsedefcv;
    }

    public double getOutlierCv() {
        return outlierCv;
    }

    public void setOutlierCv(double outlierCv) {
        this.outlierCv = outlierCv;
    }

    public boolean isOutlierEml() {
        return outlierEml;
    }

    public void setOutlierEml(boolean outlierEml) {
        this.outlierEml = outlierEml;
    }

    public double getOutlierTcrate() {
        return outlierTcrate;
    }

    public void setOutlierTcrate(double outlierTcrate) {
        this.outlierTcrate = outlierTcrate;
    }

    public boolean isAutomdlEnabled() {
        return automdlEnabled;
    }

    public void setAutomdlEnabled(boolean automdlEnabled) {
        this.automdlEnabled = automdlEnabled;
    }

    public boolean isAutomdlAcceptdefault() {
        return automdlAcceptdefault;
    }

    public void setAutomdlAcceptdefault(boolean automdlAcceptdefault) {
        this.automdlAcceptdefault = automdlAcceptdefault;
    }

    public double getAutomdlCancel() {
        return automdlCancel;
    }

    public void setAutomdlCancel(double automdlCancel) {
        this.automdlCancel = automdlCancel;
    }

    public double getAutomdlUb1() {
        return automdlUb1;
    }

    public void setAutomdlUb1(double automdlUb1) {
        this.automdlUb1 = automdlUb1;
    }

    public double getAutomdlUb2() {
        return automdlUb2;
    }

    public void setAutomdlUb2(double automdlUb2) {
        this.automdlUb2 = automdlUb2;
    }

    public double getAutomdlArmalimit() {
        return automdlArmalimit;
    }

    public void setAutomdlArmalimit(double automdlArmalimit) {
        this.automdlArmalimit = automdlArmalimit;
    }

    public double getAutomdlReducecv() {
        return automdlReducecv;
    }

    public void setAutomdlReducecv(double automdlReducecv) {
        this.automdlReducecv = automdlReducecv;
    }

    public double getAutomdlLjungboxlimit() {
        return automdlLjungboxlimit;
    }

    public void setAutomdlLjungboxlimit(double automdlLjungboxlimit) {
        this.automdlLjungboxlimit = automdlLjungboxlimit;
    }

    public boolean isAutomdlCompare() {
        return automdlCompare;
    }

    public void setAutomdlCompare(boolean automdlCompare) {
        this.automdlCompare = automdlCompare;
    }

    public boolean isArimaMu() {
        return arimaMu;
    }

    public void setArimaMu(boolean arimaMu) {
        this.arimaMu = arimaMu;
    }

    public int getArimaP() {
        return arimaP;
    }

    public void setArimaP(int arimaP) {
        this.arimaP = arimaP;
    }

    public int getArimaD() {
        return arimaD;
    }

    public void setArimaD(int arimaD) {
        this.arimaD = arimaD;
    }

    public int getArimaQ() {
        return arimaQ;
    }

    public void setArimaQ(int arimaQ) {
        this.arimaQ = arimaQ;
    }

    public int getArimaBP() {
        return arimaBP;
    }

    public void setArimaBP(int arimaBP) {
        this.arimaBP = arimaBP;
    }

    public int getArimaBD() {
        return arimaBD;
    }

    public void setArimaBD(int arimaBD) {
        this.arimaBD = arimaBD;
    }

    public int getArimaBQ() {
        return arimaBQ;
    }

    public void setArimaBQ(int arimaBQ) {
        this.arimaBQ = arimaBQ;
    }

    public boolean isArimaCoefEnabled() {
        return arimaCoefEnabled;
    }

    public void setArimaCoefEnabled(boolean arimaCoefEnabled) {
        this.arimaCoefEnabled = arimaCoefEnabled;
    }

    public List<String> getArimaCoef() {
        return arimaCoef;
    }

    public void setArimaCoef(List<String> arimaCoef) {
        this.arimaCoef = arimaCoef;
    }

    public List<String> getArimaCoefType() {
        return arimaCoefType;
    }

    public void setArimaCoefType(List<String> arimaCoefType) {
        this.arimaCoefType = arimaCoefType;
    }

    public double getFcstHorizon() {
        return fcstHorizon;
    }

    public void setFcstHorizon(double fcstHorizon) {
        this.fcstHorizon = fcstHorizon;
    }

    public int getSeatsPredictionLength() {
        return seatsPredictionLength;
    }

    public void setSeatsPredictionLength(int seatsPredictionLength) {
        this.seatsPredictionLength = seatsPredictionLength;
    }

    public String getSeatsApprox() {
        return seatsApprox;
    }

    public void setSeatsApprox(String seatsApprox) {
        this.seatsApprox = seatsApprox;
    }

    public double getSeatsTrendBoundary() {
        return seatsTrendBoundary;
    }

    public void setSeatsTrendBoundary(double seatsTrendBoundary) {
        this.seatsTrendBoundary = seatsTrendBoundary;
    }

    public double getSeatsSeasdBoundary() {
        return seatsSeasdBoundary;
    }

    public void setSeatsSeasdBoundary(double seatsSeasdBoundary) {
        this.seatsSeasdBoundary = seatsSeasdBoundary;
    }

    public double getSeatsSeasdBoundary1() {
        return seatsSeasdBoundary1;
    }

    public void setSeatsSeasdBoundary1(double seatsSeasdBoundary1) {
        this.seatsSeasdBoundary1 = seatsSeasdBoundary1;
    }

    public double getSeatsSeasTol() {
        return seatsSeasTol;
    }

    public void setSeatsSeasTol(double seatsSeasTol) {
        this.seatsSeasTol = seatsSeasTol;
    }

    public double getSeatsMaBoundary() {
        return seatsMaBoundary;
    }

    public void setSeatsMaBoundary(double seatsMaBoundary) {
        this.seatsMaBoundary = seatsMaBoundary;
    }

    public String getSeatsMethod() {
        return seatsMethod;
    }

    public void setSeatsMethod(String seatsMethod) {
        this.seatsMethod = seatsMethod;
    }

    
    // NUOVI DA QUI IN POI
    
    // per gestire ramps dal duplice valore: ramps="NA" o List<RampsInfo>
    private static class RampsInfoDeserializer extends JsonDeserializer<List<RampsInfo>> {
        @Override
        public List<RampsInfo> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String text = parser.getText();
            if ("NA".equalsIgnoreCase(text)) {
                return new ArrayList<RampsInfo>();  // Restituisci null quando il valore è "NA"
            }
            return ctxt.readValue(parser, ctxt.getTypeFactory().constructCollectionType(List.class, RampsInfo.class));
        }
    }
    
    public List<RampsInfo> getRamps() {
        return ramps;
    }

    public void setRamps(List<RampsInfo> ramps) {
        this.ramps = ramps;
    }
    
    
    // per gestire IV dal duplice valore: ramps="NA" o List<InterventionVariablesInfo>
    private static class InterventionVariablesInfoDeserializer extends JsonDeserializer<List<InterventionVariablesInfo>> {
       @Override
        public List<InterventionVariablesInfo> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String text = parser.getText();
            if ("NA".equalsIgnoreCase(text)) {
                return new ArrayList<InterventionVariablesInfo>();  // Restituisci null quando il valore è "NA"
            }
            return ctxt.readValue(parser, ctxt.getTypeFactory().constructCollectionType(List.class, InterventionVariablesInfo.class));
        }
    }
    
    public List<InterventionVariablesInfo> getInterventionVariables() {
        return interventionVariables;
    }

    public void setInterventionVariables(List<InterventionVariablesInfo> interventionVariables) {
        this.interventionVariables = interventionVariables;
    }

    public double getEasterCoef() {
        return easterCoef;
    }

    public void setEasterCoef(double easterCoef) {
        this.easterCoef = easterCoef;
    }

    // legge eventuali interi come stringhe 3 --> "3", "NA" --> "NA"
    public static class StringOrNumberDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.getCurrentToken().isNumeric()) {
                return String.valueOf(p.getIntValue()); // Converte numero in stringa
            } else {
                return p.getText(); // Ritorna la stringa originale
            }
        }
    }
    
}

