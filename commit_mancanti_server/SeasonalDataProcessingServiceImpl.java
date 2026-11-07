package it.istat.koala.service.impl;

import it.istat.koala.controller.AliveController;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Longs;

import it.istat.koala.data.entity.DestCatalogoDett;
import it.istat.koala.data.entity.DestCopia;
import it.istat.koala.data.entity.DestDiretta;
import it.istat.koala.data.entity.DestIndirettaComp;
import it.istat.koala.data.entity.DestSerieOutput;
import it.istat.koala.data.entity.GerarchiaDestagTF;
import it.istat.koala.data.repository.CombinazioneRepository;
import it.istat.koala.data.repository.DestCatalogoDettRepository;
import it.istat.koala.data.repository.DestCopiaRepository;
import it.istat.koala.data.repository.DestDirettaRepository;
import it.istat.koala.data.repository.DestIndirettaCompRepository;
import it.istat.koala.data.repository.GerarchiaDestagTFRepository;
import it.istat.koala.data.repository.SerieOutputRepository;
import it.istat.koala.dto.PseudoIvDTO;
import it.istat.koala.dto.JobDTO;
import it.istat.koala.enumerator.Approccio;
import it.istat.koala.enumerator.Funzione;
import it.istat.koala.enumerator.StatoJob;
import it.istat.koala.enumerator.TipoIndicatore;
import it.istat.koala.service.JDemetraService;
import it.istat.koala.service.JobErroriService;
import it.istat.koala.service.JobFilesService;
import it.istat.koala.service.JobService;
import it.istat.koala.service.SeasonalDataProcessingService;
import it.istat.koala.utility.BasicSpecDefaults;
import it.istat.koala.utility.DestBodyForSSurvey;
import it.istat.koala.utility.DestOutputJobFileJson;
import it.istat.koala.utility.DestPartialResult;
import it.istat.koala.utility.DestRebased;
import it.istat.koala.utility.DestReport;
import it.istat.koala.utility.DestSpecificationsModel;
import it.istat.koala.utility.DiagnosticReports;
import it.istat.koala.utility.FunzioniDestIndiretta;
import it.istat.koala.utility.ListUtils;
import it.istat.koala.utility.ObjDbJobFileJson;
import it.istat.koala.utility.SeasonalData;
import it.istat.koala.utility.TipoDati;
import it.istat.koala.utility.ValoreData;
import it.istat.koala.utility.ValorePesoUtility;
import it.istat.smetabrowser.data.entity.Indagine;
import it.istat.smetabrowser.data.repository.IndagineRepository;
import it.istat.smetabrowser.dto.IndagineParametriDTO;
import it.istat.smetabrowser.dto.RestResponseDTO;
import it.istat.smetabrowser.dto.jms.DestagionalizzazioneMessageDTO;
import it.istat.smetabrowser.dto.jms.RigaDettaglioDTO;
import it.istat.smetabrowser.enumerator.IndagineParametro;
import it.istat.smetabrowser.exception.BusinessServiceException;
import it.istat.smetabrowser.service.IndagineParametriService;
import it.istat.smetabrowser.util.ClobUtils;

@Service("seasonalDataProcessingService")
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SeasonalDataProcessingServiceImpl implements SeasonalDataProcessingService {

    private final AliveController aliveController;

    private final DataProcessingServiceImpl dataProcessingServiceImpl;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobFilesService jobFilesService;

    @Autowired
    private JobErroriService jobErroriService;

    @Autowired
    private JDemetraService jDemetraService;

    @Autowired
    private DestDirettaRepository destDirettaRepository;

    @Autowired
    private DestCatalogoDettRepository destCatalogoDettRepository;

    @Autowired
    private SerieOutputRepository serieOutputRepository;

    @Autowired
    private DestCopiaRepository destCopiaRepository;

    @Autowired
    private DestIndirettaCompRepository destIndirettaCompRepository;

    @Autowired
    private CombinazioneRepository combinazioneRepository;

    @Autowired
    private IndagineRepository indagineRepository;

    @Autowired
    private GerarchiaDestagTFRepository gerarchiaDestagTFRepository;

    @Autowired
    private IndagineParametriService indagineparametriService;

    @Autowired
    private FunzioniDestIndiretta funzioniDestIndiretta;

    @Value("${url.ssurvey.indicatore.valori}")
    private String urlSsurveyIndicatore;

    /*
     * Report diagnostici della destagionalizzazione diretta (come create_diagnostic_report1/2 in R).
     * Impostabili da application.properties; i default sono quelli indicati dopo i due punti.
     * - dest.report.outputOnFile:    scrive i report su file in dest.report.outputDir
     * - dest.report.outputOnStrings: rende disponibili i report in getReportDiagnostici()
     *                                (mappa nome file -> testo del report)
     * Con entrambi a false i report non vengono prodotti.
     */
    @Value("${dest.report.outputOnFile:false}")
    private boolean reportOutputOnFile;

    @Value("${dest.report.outputOnStrings:false}")
    private boolean reportOutputOnStrings;

    @Value("${dest.report.outputDir:${java.io.tmpdir}}")
    private String reportOutputDir;

    @Value("${dest.report.problemsFile:" + DiagnosticReports.DEFAULT_PROBLEMS_FILE + "}")
    private String reportProblemsFile;

    @Value("${dest.report.detailsFile:" + DiagnosticReports.DEFAULT_DETAILS_FILE + "}")
    private String reportDetailsFile;

    @Value("${dest.report.excludeNonSeas:true}")
    private boolean reportExcludeNonSeas;

    /* Report dell'ultimo job elaborato (vuota se dest.report.outputOnStrings=false) */
    private Map<String, String> reportDiagnostici = new HashMap<>();

    /*
     * La key della mappa datiDaDestagionalizzare è la combinazione che determina
     * la serie ed è calcolata in calcKey in base ai contenuti di RigaDettaglioDTO
     */
    private HashMap<String, SeasonalData> datiDaDestagionalizzare = new HashMap<>();
    
    /*
     * Lista di errori non bloccanti da inserire in Job errori alla fine del
     * processo
     */
    private HashMap<String, String> errori = new HashMap<>();

    /*
     * Diagnostica sul numero di serie trattate
     * - dirette: {destagionalizzate: X1, corrette: X2}
     * - indirette: {destagionalizzate: X1, corrette: X2}
     * - copiate: Z {destagionalizzate: X1, corrette: X2}
     */
    private DestReport diagnostica;

    // OUTPUT_MAIN.NAME per fase Destagionalizzazione diretta
    /*
     * TODO i nomi degli output si devono estrarre da OUTPUT_MAIN in base a
     * id_indagine e id_destinatario (destagionalizzazione)
     */
    // private final String DEST_DIRETTA_RESULT = "DEST_DIRETTA_RSLT";
    // private final String DEST_DIRETTA_ERRORI = "DEST_DIRETTA_ERR";
    private final String DEST_RESULT = "DEST_RESULT";
    private final String DEST_ERRORI = "DEST_ERRORI";
    private final String DEST_DIRETTA = "DEST_DIRETTA";
    private final String DEST_DIAGNOSTICA = "DEST_DIAGNOSTICA";

    // Dimensione minima della serie per poter essere elaborata
    private final int MIN_SERIES_LENGTH = 36;

    private String fase;
    private boolean isInterna = false;

    public final static Long PERIODICITA_MENSILE = 1L;

    private Long periodsPerYear;

    SeasonalDataProcessingServiceImpl(DataProcessingServiceImpl dataProcessingServiceImpl, AliveController aliveController) {
        this.dataProcessingServiceImpl = dataProcessingServiceImpl;
        this.aliveController = aliveController;
    }

    /**
     * 1. legge tutti le RigaDettaglioDTO contenute in
     * DestagionalizzazioneMessageDTO e le raggruppa in SeasonalData per chiave
     * della serie determinata in SeasonalData.calcKey 
     * 
     * 2. Per ogni serie (SeasonalData in datiDaDestagionalizzare)
     * - riordina i dati in base al periodo (idPeriodo in RigaDettaglioDTO)
     * - verifica la correttezza e completezza della serie
     * - estrae dal DB il modello json e i tipi di output richiesti
     * - deserializza il modello json effettuando gli opportuni aggiustamenti
     * - lancia il processo di calcolo della destagionalizzazione
     * - inserisce gli output in datiElaborati
     * - in caso di errore inserisce in JOB_ERRORI e se possibile elabora la prossima serie
     * 
     * 3. Salva gli output della destagionalizzazione diretta in JOB_FILES in formato json 
     * 
     * 4. DESTAGIONALIZZAZIONE INDIRETTA
     *
     * @param job Dati del job in elaborazione
     * @param dto Dati di input per l'elaborazione
     * @throws BusinessServiceException
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    @Transactional
    public void process(JobDTO job, DestagionalizzazioneMessageDTO dto) throws BusinessServiceException {

        Long idIndagine = dto.getIdIndagine();
        Long startProcessPeriod = dto.getIdPeriodoStart();

        // initialize diagnostics
        diagnostica = new DestReport();

        // Compute number of maximum periods per year from survey
        Optional<Indagine> optIndagine = indagineRepository.findById(idIndagine);
        if (!optIndagine.isPresent()) {
            throw new BusinessServiceException("Errore nei dati: impossibile recuperare l'indagine con id " + idIndagine);
        }
        Long idPeriodicita = optIndagine.get().getPeriodicita().getId();
        periodsPerYear = idPeriodicita.equals(PERIODICITA_MENSILE) ? 12L : 4L;

        // prepare final output objects
        List<DestOutputJobFileJson> finalDestOutputList = new ArrayList<>();
        List<PseudoIvDTO> finalIndicatoreValoriList = new ArrayList<>();

        // Construct a map of all partial results, 
        // ie data that will be used in subsequent steps of the algorithm.
        DestPartialResult partialResult = new DestPartialResult();
        // Same thing but with based data, for Confidence processing
        DestPartialResult basedPartialResult = new DestPartialResult();

        // other useful collections
        datiDaDestagionalizzare = new HashMap();
        errori = new HashMap();

        /////////////////////////////////////////////
        ////////////// 0.PREPARA i DATI /////////////
        /////////////////////////////////////////////
        jobService.updateStato(job, StatoJob.PREPARA);

        // Find out if this survey is in Sintesi
        Optional<IndagineParametriDTO> isInternaDTO = indagineparametriService.findOne(idIndagine,
            IndagineParametro.INTERNA_A_SINTESI.getCodice());
        if (isInternaDTO.isPresent()) {
            isInterna = isInternaDTO.get().getValore() == 1;
        }

        // Start filling partial result array with whatever RAW data is a component
        // of indirect-approach computation
        List<DestIndirettaComp> compGrezze = destIndirettaCompRepository.findComponentsByInputType(idIndagine, TipoIndicatore.GREZZO.getCodice());
        
        for (RigaDettaglioDTO dettaglioGrezzo : dto.getDettagli()) {
            for (DestIndirettaComp compGrezza : compGrezze) {
                if (compGrezza.getCoreComp().matchesDettaglio(dettaglioGrezzo)) {
                    String seriesKey = compGrezza.getCoreComp() + "-" + TipoIndicatore.GREZZO.getCodice();
                    Long idPeriodo = dettaglioGrezzo.getIdPeriodoIndicatore();
                    Double value = dettaglioGrezzo.getValore();
                    partialResult.setValue(seriesKey, idPeriodo, value);
                }
            }
        }
        
        /////////////////////////////////////////////
        /////////////    1. COPIE     ///////////////
        /////////////////////////////////////////////

        // series + output type, suitable for INDICATORE_VALORI
        List<DestCopia> copiaOutputs = destCopiaRepository.findByIdIndagine(idIndagine);

        // series "containers", abstracted from output type, suitable for the JSON output
        List<DestCatalogoDett> copiaSeries = destCatalogoDettRepository.findByIdIndagineIdApproccio(idIndagine, Approccio.COPIA.getCodice());
        
        // gather into JSON and IV list
        List<DestOutputJobFileJson> jsonCopie = DestOutputJobFileJson.fromCopySeries(copiaSeries, dto.getDettagli(), funzioniDestIndiretta);
        List<PseudoIvDTO> ivCopie = PseudoIvDTO.fromSerieOutputAndDetails(copiaOutputs, dto.getDettagli(), funzioniDestIndiretta);
        
        // add to final outputs
        finalDestOutputList.addAll(jsonCopie);
        finalIndicatoreValoriList.addAll(ivCopie);
        
        // add all copied data to partial results map
        for (DestOutputJobFileJson singleJsonSerie: jsonCopie) {
            for (TipoDati singleRisultato: singleJsonSerie.getRisultati()) {
                for (ValoreData singleDato: singleRisultato.getDati()) {
                    partialResult.setValue(
                        singleJsonSerie.getIdCore() + "-" + singleRisultato.getIdTipoDato(),
                        singleDato.getData(),
                        singleDato.getValore()
                    );
                }
                // if at least one value was copied for this output type, update diagnostics
                if (singleRisultato.getDati().size() > 0) {
                    if (singleRisultato.getIdTipoDato() == TipoIndicatore.DESTAGIONALIZZATO.getCodice()) {
                        diagnostica.getCopiate().incDest();
                    } else if (singleRisultato.getIdTipoDato() == TipoIndicatore.CORRETTO.getCodice()) {
                        diagnostica.getCopiate().incCorr();
                    }
                }
            }
        }

        try {
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                    "Processing Seasonal Adjustment request");
            try {
                fase = "Elab.RigaDett";
                // legge tutti i RigaDettaglioDTO e li aggrega in base alla chiave
                // determinata in calcKey
                datiDaDestagionalizzare = elabora(dto.getDettagli(), dto.getIdIndagine(), dto.getIdPeriodoStart());
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        "Errore nella elaborazione", e);

                jobService.updateStato(job, StatoJob.ERRORE);

                jobErroriService.save(job.getId(), "Errore nell'elaborare i dati in input " + e.getMessage(),
                        Calendar.getInstance());
                return;
            }

            /////////////////////////////////////////////
            /// 2.DESTAGIONALIZZAZIONE DIRETTA //////////
            /////////////////////////////////////////////
            jobService.updateStato(job, StatoJob.DIRETTA);

            // report diagnostici: raccolgono gli indicatori di ogni serie elaborata
            final DiagnosticReports reports = new DiagnosticReports(reportOutputOnFile, reportOutputOnStrings);
            reports.setOutputDir(reportOutputDir);
            reports.setProblemsFileName(reportProblemsFile);
            reports.setDetailsFileName(reportDetailsFile);
            reports.setExcludeNonSeas(reportExcludeNonSeas);
            reportDiagnostici = new HashMap<>();

            try {
                fase = "Elab.Dest.Diretta";
                datiDaDestagionalizzare.forEach((key, datiSerie) -> {
                    try {
                        datiSerie.setEsito(null);

                        // per ogni raggruppamento calcola i metadati, estrai il modello e riordina i
                        // valori
                        elaboraDettaglio(key, datiSerie, idIndagine);

                        if (datiSerie.getEsito() == null) {
                            // la serie può essere elaborata
                            /*System.out.println("Inizio elaborazione: indagine:" + idIndagine
                                    + " periodoStart:" + dto.getIdPeriodoStart() + " serie:" + datiSerie.getOutputKey()
                                    + " numVal:" + datiSerie.getValori().length);
                            */
                            // Calcola valori destagionalizzati per questa serie

                            // THIS IS WHERE THE REAL ADJUSTMENT IS DONE
                            String newEsito = jDemetraService.process(datiSerie, job.getId(), idIndagine);
                            datiSerie.setEsito(newEsito);

                            if (!"ok".equals(datiSerie.getEsito())) {
                                errori.put(datiSerie.getOutputKey(), datiSerie.getEsito());
                            } else {
                                // report diagnostici: un problema nei report non deve bloccare i dati della serie
                                try {
                                    reports.add(datiSerie.getSeriesName(), datiSerie.getJdResults());
                                } catch (Exception e) {
                                    java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName())
                                            .log(Level.WARNING, "Report diagnostici: serie " + key + " non inclusa", e);
                                } finally {
                                    datiSerie.setJdResults(null); // rilascia i risultati completi di JD+
                                }

                                // PATCH when risultati is null
                                Long startPeriod = datiSerie.getSeriesStart();
                                Map<String, Map<String, Double>> newRisultati = datiSerie.getOutput().getRisultati().entrySet().stream()
                                    .collect(Collectors.toMap(
                                        (Entry<String, Map<String, Double>> e) -> e.getKey(),
                                        (Entry<String, Map<String, Double>> e) -> e.getValue() == null ? new HashMap<>() : e.getValue()
                                    ));
                                
                                // In addition, if SA and/or WDA outputs are trimmed (as dictated by "series_start" in the DEST_DIRETTA model),
                                // trim raw data accordingly.
                                Map<String, Double> trimmedRaw = newRisultati.get(TipoIndicatore.GREZZO.getDescForOutput())
                                    .entrySet().stream()
                                    .filter(x -> {
                                        if (startPeriod == null) {
                                            return true;
                                        }
                                        ValoreData v = new ValoreData(x.getKey(), x.getValue());
                                        return v.getData() >= startPeriod;
                                    })
                                    .collect(Collectors.toMap(
                                        e -> e.getKey(),
                                        e -> e.getValue()
                                    ));
                                
                                newRisultati.put(TipoIndicatore.GREZZO.getDescForOutput(), trimmedRaw);

                                datiSerie.getOutput().impostaRisultato(newRisultati);

                                // add to final outputs
                                DestOutputJobFileJson destOutputToAdd = new DestOutputJobFileJson(datiSerie);
                                finalDestOutputList.add(destOutputToAdd);
                                finalIndicatoreValoriList.addAll(PseudoIvDTO.fromDestOutputJobFileJson(destOutputToAdd));

                                // add to partial result map
                                for (TipoDati singleRisultato: destOutputToAdd.getRisultati()) {
                                    for (ValoreData singleDato: singleRisultato.getDati()) {
                                        partialResult.setValue(
                                            destOutputToAdd.getIdCore() + "-" + TipoIndicatore.getCodiceFromDesc(singleRisultato.getTipoDato()),
                                            singleDato.getData(),
                                            singleDato.getValore()
                                        );
                                    }
                                }

                                // update diagnostics tally
                                if (datiSerie.getOutput().getRisultati().containsKey(TipoIndicatore.DESTAGIONALIZZATO.getDescForOutput())
                                    && datiSerie.getOutput().getRisultati().get(TipoIndicatore.DESTAGIONALIZZATO.getDescForOutput()) != null
                                    && !datiSerie.getOutput().getRisultati().get(TipoIndicatore.DESTAGIONALIZZATO.getDescForOutput()).isEmpty()
                                ) {
                                    diagnostica.getDirette().incDest();
                                }
                                if (datiSerie.getOutput().getRisultati().containsKey(TipoIndicatore.CORRETTO.getDescForOutput())
                                    && datiSerie.getOutput().getRisultati().get(TipoIndicatore.CORRETTO.getDescForOutput()) != null
                                    && !datiSerie.getOutput().getRisultati().get(TipoIndicatore.CORRETTO.getDescForOutput()).isEmpty()
                                ) {
                                    diagnostica.getDirette().incCorr();
                                }
                            }

                        } else {
                            switch (datiSerie.getEsito()) {
                                case "KO":
                                    break;

                                case "Grezzo da copiare":
                                    break;

                                default:
                                    // errore generico di elaborazione
                                    String testoErrore = ("\nImpossibile elaborare serie: indagine:"
                                            + idIndagine
                                            + " periodoStart:" + dto.getIdPeriodoStart()
                                            + " variab:" + datiSerie.getOutputKey() + " numVal:"
                                            + datiSerie.getDati().size()
                                            + " per " + datiSerie.getEsito());
                                    System.out.println(testoErrore);
                                    errori.put(datiSerie.getOutputKey(), testoErrore);
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        String testoErrore = "Errore nel generare dati per " + key + ": " + e.getMessage();
                        java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName())
                                .log(Level.SEVERE, testoErrore, e);
                        errori.put(datiSerie.getOutputKey(), testoErrore);
                    }
                });
                
            } catch (Exception e) {
                String testoErrore = "Errore nell'elaborazione dettagli " + e.getMessage();
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        testoErrore, e);
                errori.put(fase, testoErrore);
                // return;
            } finally {
                try {
                    fase = "Dest.Diretta.Output";
                } catch (Exception e) {
                    String testoErrore = "Dest. diretta - Errore nella scrittura dei risultati: " + e.getMessage();
                    java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName())
                            .log(Level.SEVERE, testoErrore, e);
                    errori.put(fase, testoErrore);
                }
            }
            // report diagnostici della destagionalizzazione diretta (non bloccanti)
            try {
                reportDiagnostici = new HashMap<>(reports.produce());
            } catch (Exception e) {
                String testoErrore = "Report diagnostici non prodotti: " + e.getMessage();
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.WARNING,
                        testoErrore, e);
                errori.put("Report diagnostici", testoErrore);
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                String jsonTxt = mapper.writeValueAsString(datiDaDestagionalizzare);
                Clob content = ClobUtils.toClob(jsonTxt);
                jobFilesService.save(job.getId(), DEST_DIRETTA, content);
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        "Errore nell'inserimento della diretta in jobfile");
            }

            /////////////////////////////////////////////
            /// 3.DESTAGIONALIZZAZIONE INDIRETTA ////////
            /////////////////////////////////////////////

            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                        "Inizio destagionalizzazione indiretta");
            
            fase = "Dest.Indiretta";
            jobService.updateStato(job, StatoJob.INDIRETTA);

            // Query the indirect-approach hierarchy TF
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                        "Query sulla TF gerarchia");
            // TODO : se la TF va in errore gestirlo
            List<GerarchiaDestagTF> gerarchiaDestagEntityTFList = gerarchiaDestagTFRepository.findAllTF(
                    idIndagine, Approccio.INDIRETTO.getCodice(), dto.getIdPeriodoStart(),
                    dto.getIdPeriodoEnd());

            // chunk TF output first by id_core_agg-id_tipo_output, then chunk each sublist by period.
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                "Chunking dei risultati della TF");
            List<List<GerarchiaDestagTF>> chunkedBySeries = ListUtils.chunkBy(
                gerarchiaDestagEntityTFList,
                sr -> sr.getIdCoreAgg() + "-" + sr.getIdTipoOutput()
            );

            List<List<List<GerarchiaDestagTF>>> chunkedByPeriodAndSeries = chunkedBySeries.stream()
                .map(sc -> ListUtils.chunkBy(sc, sr -> sr.getIdPeriodo()))
                .collect(Collectors.toList());
            
            
            /////////////////////////////////////////////
            /// 3.1. RIBASAMENTO PRELIMINARE CLIMA  /////
            /////////////////////////////////////////////
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                "Operazioni preliminari al trattamento delle serie della FIDUCIA");

            // When function_id = FIDUCIA (8), we need to rebase component index values beforehand.
            DestRebased rebaseDenominators = new DestRebased(gerarchiaDestagEntityTFList, partialResult, periodsPerYear);

            gerarchiaDestagEntityTFList.stream()
                .filter((GerarchiaDestagTF g) -> g.getIdFunzione().equals(Funzione.FIDUCIA.getCodice()))
                .forEach((GerarchiaDestagTF g) -> {
                    Double unbasedValue = partialResult.getValue(g.componentKey(), g.getIdPeriodo());
                    Double denominator = rebaseDenominators.getDenom(g.componentKey());
                    basedPartialResult.setValue(g.componentKey(), g.getIdPeriodo(), (unbasedValue + 100.0) * 100.0 / denominator);
                });

            ////////////////////////////////////////////////////
            /// 3.2. SVOLGIMENTO REGOLARE DEST. INDIRETTA  /////
            ////////////////////////////////////////////////////
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.INFO,
                        "Svolgimento dell'indiretta con calcolo sequenziale");
            
            // retrieve full set of desired indirect-approach series and outputs
            List<DestSerieOutput> indirectSerieOutput = serieOutputRepository.findByIdIndagineIdApproccio(idIndagine, Approccio.INDIRETTO.getCodice());

            /* main, 2-level for-loop over doubly-chunked TF output */
            iterateSeriesChunk: for (int i = 0; i < chunkedByPeriodAndSeries.size(); i ++) {
                List<List<GerarchiaDestagTF>> singleSeriesChunk = chunkedByPeriodAndSeries.get(i);
                /* pick period from first aggregate */
                List<GerarchiaDestagTF> firstPeriodChunk = singleSeriesChunk.get(0);

                /* pick aggregate info from first component of subchunk, since it's all the same anyway */
                GerarchiaDestagTF aggregate = firstPeriodChunk.get(0);
                DestSerieOutput dso = indirectSerieOutput.stream().filter(
                    x -> x.getId().getIdCore().equals(aggregate.getIdCoreAgg())
                    && x.getId().getIdTipoOutput().equals(aggregate.getIdTipoOutput())
                ).findFirst().orElse(null);
                if (dso == null) { // indirect series couldn't be found for whatever reason
                    continue iterateSeriesChunk;
                }

                // initialize Json object. Results will be added later.
                Long idPeriodoStart = aggregate.getIdPeriodo();
                DestOutputJobFileJson destOutput = new DestOutputJobFileJson(dso, idPeriodoStart);

                List<TipoDati> listRisultati = new ArrayList<TipoDati>();
                TipoDati singleOutputResult = new TipoDati();
                singleOutputResult.setTipoDato(TipoIndicatore.getDescForOutput(aggregate.getIdTipoOutput()));
                List<ValoreData> singleOutputValues = new ArrayList<>();

                iteratePeriodChunk: for (int j = 0; j < singleSeriesChunk.size(); j++) {
                    List<GerarchiaDestagTF> singlePeriodChunk = singleSeriesChunk.get(j);
                    GerarchiaDestagTF periodAggregate = singlePeriodChunk.get(0);
                    Long functionId = periodAggregate.getIdFunzione();
                    Long idPeriodo = periodAggregate.getIdPeriodo();
                    String aggregateKey = periodAggregate.aggregateKey();
                    Double indexResult = null;
                    Double sogliaMin = periodAggregate.getSogliaMin();
                    Double sogliaMax = periodAggregate.getSogliaMax();

                    List<ValorePesoUtility> valoriPesi;

                    if (functionId.equals(Funzione.FIDUCIA.getCodice())) {
                        valoriPesi = ValorePesoUtility.fromChunk(singlePeriodChunk, basedPartialResult);
                    } else {
                        valoriPesi = ValorePesoUtility.fromChunk(singlePeriodChunk, partialResult);
                    }
                    if (valoriPesi == null) {
                        // Skip computation if it isn't possible to retrieve values and weights for all components
                        continue iteratePeriodChunk;
                    }

                    String componentKeys = String.join(
                        ", ", 
                        singlePeriodChunk.stream().map(GerarchiaDestagTF::componentKey).collect(Collectors.toList())
                    );

                    indexResult = computeAggregateValue(valoriPesi, functionId, aggregateKey, componentKeys);

                    if (indexResult != null) {
                        // apply thresholds, if found
                        indexResult = funzioniDestIndiretta.clampToThresholds(indexResult, sogliaMin, sogliaMax);
                        
                        // insert into final output objects
                        if (isInterna) {
                            // iv: only if it's in Sintesi
                            PseudoIvDTO indicatoreValori = new PseudoIvDTO(
                                aggregate.getIdTipoOutput(),
                                aggregate.getIdAggregazioneAgg(),
                                aggregate.getIdBaseAgg(),
                                aggregate.getIdVariabileAgg(),
                                idPeriodo,
                                indexResult
                            );
                            finalIndicatoreValoriList.add(indicatoreValori);
                        }
                        // for subsequent insertion into JSON file
                        singleOutputValues.add(new ValoreData(idPeriodo, indexResult));

                        // partial result map
                        partialResult.setValue(aggregateKey, idPeriodo, indexResult);
                    }
                }

                // if at least one value in the series was computed:
                if (singleOutputValues.size() > 0) {
                    // populate JSON object and push it to final JSON list
                    singleOutputResult.setDati(singleOutputValues);
                    listRisultati.add(singleOutputResult);
                    destOutput.setRisultati(listRisultati);
                    // reset starting period, as we cannot be sure results start together with metadata.
                    destOutput.setIdPeriodoStart(singleOutputValues.get(0).getData());
                    finalDestOutputList.add(destOutput);

                    // increment series count for corresponding idTipoOutput
                    if (TipoIndicatore.DESTAGIONALIZZATO.getCodice().equals(aggregate.getIdTipoOutput())) {
                        diagnostica.getIndirette().incDest();
                    } else if (TipoIndicatore.CORRETTO.getCodice().equals(aggregate.getIdTipoOutput())) {
                        diagnostica.getIndirette().incCorr();
                    }
                }
            }

            ///////////////////////////////////////////////////
            // 4. POPOLAMENTO JOB FILES e INDICATORE_VALORI ///
            ///////////////////////////////////////////////////
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName())
                .log(Level.INFO, "Inizio popolamento JOB_FILES");

            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjDbJobFileJson jobFileIns = new ObjDbJobFileJson();
                jobFileIns.setSeries(finalDestOutputList);
                String jsonTxt = mapper.writeValueAsString(jobFileIns);
                System.out.println("------------------JSON DIRETTA E INDIRETTA----------------------------------------");
                System.out.println("Compilato json per JOB_FILES comprendente " + finalDestOutputList.size() + " serie");
                
                Clob content = ClobUtils.toClob(jsonTxt);
                jobFilesService.save(job.getId(), DEST_RESULT, content);
                jsonTxt = mapper.writeValueAsString(errori);
                
                content = ClobUtils.toClob(jsonTxt);
                jobFilesService.save(job.getId(), DEST_ERRORI, content);

                jsonTxt = diagnostica.toJson();
                content = ClobUtils.toClob(jsonTxt);
                jobFilesService.save(job.getId(), DEST_DIAGNOSTICA, content);
                errori.clear();
            } catch (Exception e) {
                String testoErrore = "Dest. diretta - Errore nella scrittura dei risultati: " + e.getMessage();
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        testoErrore, e);
                errori.put(fase, testoErrore);
                jobService.updateStato(job, StatoJob.ERRORE);
            }

            // INDICATORE_VALORI
            if (isInterna) {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders requestHeaders = new HttpHeaders();
                requestHeaders.setContentType(MediaType.APPLICATION_JSON);

                DestBodyForSSurvey bodyForSSurvey = new DestBodyForSSurvey(
                    finalIndicatoreValoriList,
                    idIndagine, startProcessPeriod, job.getId());
                
                HttpEntity<DestBodyForSSurvey> request = new HttpEntity<>(
                    bodyForSSurvey, requestHeaders);

                System.out.println("------------------JSON INDICATORE VALORI----------------------------------------");
                System.out.println("Numero di righe da salvare su INDICATORE_VALORI: " + finalIndicatoreValoriList.size());

                RestResponseDTO ivResult = restTemplate.postForObject(urlSsurveyIndicatore, request,
                        RestResponseDTO.class);
                if (ivResult == null) {
                    throw new BusinessServiceException("Errore sconosciuto durante il salvataggio su INDICATORE_VALORI");
                } else if (ivResult.getCode() != RestResponseDTO.RESPONSE_OK) {
                    throw new BusinessServiceException(ivResult.getMessage());
                }
            }

            // errori.put(fase,"Destagionalizzazione indiretta non elaborata");
            // 5.. ESTRAZIONE DATI E CREAZIONE FILES
            jobService.updateStato(job, StatoJob.GENERA_OUTPUT);
            if (!errori.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonTxt = mapper.writeValueAsString(errori);
                    jobErroriService.save(job.getId(), jsonTxt, Calendar.getInstance());
                } catch (Exception e) {
                }
            }

            ////// FINE JOB /////////////
            jobService.updateStato(job, StatoJob.COMPLETATO);
            jobService.save(job);

        } catch (Exception ex) {

            String testoErrore = "Dest. indiretta - Errore nella scrittura dei risultati: " + ex.getMessage();
            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                    testoErrore, ex);
            jobService.updateStato(job, StatoJob.ERRORE);

            try {
                errori.put(fase, ex.getMessage());
                ObjectMapper mapper = new ObjectMapper();
                String jsonTxt = mapper.writeValueAsString(errori);
                jobErroriService.save(job.getId(), jsonTxt, Calendar.getInstance());

            } catch (Exception e) {
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        "Errore  nell'inserimento in job_errori");
            }

            java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                    ex.getMessage());
        }

    }


    /**
     * Legge tutte le righe di dettaglio del DTO e crea una mappa non ordinata
     * di aggregazioni corrispondenti alla serie (SeasonalData) in base a
     * SeasonalData.calcKey in cui viene inserita la lista, non ordinata, di
     * RigaDettaglioDTO appartenenti alla serie
     *
     * @param dettagli: tutte le righe di dettaglio del DTO
     * @param idPeriodoStart : numerico che rappresenta aaaamm di inizio serie
     *
     * @return mappa con chiavi idAgg-idBase-idTipoInd-idVariabile e valori i
     * corrispondenti valori presi da INDICATORE_VALORI
     */
    /**
     * Report diagnostici dell'ultimo job (nome file -> testo del report).
     * Vuota se dest.report.outputOnStrings=false o se i report non sono stati prodotti.
     */
    public Map<String, String> getReportDiagnostici() {
        return reportDiagnostici;
    }

    private HashMap<String, SeasonalData> elabora(List<RigaDettaglioDTO> dettagli, Long idIndagine, Long idPeriodoStart) {
        Map<String, SeasonalData> result;
        List<DestDiretta> directSeries = destDirettaRepository.findDestDirettaByIdIndagine(idIndagine);

        Map<String, List<RigaDettaglioDTO>> groupedRigaDettaglio = dettagli.stream()
            // toss out all irrelevant details
            .filter(singleDett -> directSeries.stream().anyMatch(ds -> ds.getDett().getCore().matchesDettaglio(singleDett)))
            .collect(Collectors.groupingBy(SeasonalData::calcKey));

        result = groupedRigaDettaglio.entrySet().stream()
            .map(entry -> {
                SeasonalData sd = new SeasonalData(entry.getValue().get(0));
                sd.setIdPeriodoStart(idPeriodoStart);
                Map<Long, RigaDettaglioDTO> dati = entry.getValue().stream().collect(Collectors.toMap(
                    (singleRow) -> singleRow.getIdPeriodoIndicatore(),
                    (singleRow) -> singleRow
                ));
                sd.setDati(dati);
                return sd;
            }).collect(Collectors.toMap(
                (sd) -> sd.getKey(),
                (sd) -> sd)
            );
        return new HashMap<>(result);
    }


    /**
     * Per la serie in elaborazione (SeasonalData): - riordina i valori in base
     * al periodo di riferimento - prepara il modello e le specifications per
     * jDemetra+
     *
     * @param key
     * @param datiSerie: serie (SeasonalData) da elaborare
     */
    private void elaboraDettaglio(String key, SeasonalData datiSerie, Long idIndagine) {
        if (datiSerie.getDati() != null && !datiSerie.getDati().isEmpty()) {

            // imposto il modello di destagionalizzazione e i tipi output
            setSeasonalModel(datiSerie, idIndagine);

            if (datiSerie.getModello() != null) {
                
                // **************************************
                // riempiSerie(datiSerie); //solo per poter eseguire i test
                // **************************************

                int numValori = datiSerie.getDati().size();

                if (numValori < MIN_SERIES_LENGTH) {
                    datiSerie.setEsito(
                            "Il numero di valori (" + numValori + ") della serie non è sufficiente per l'elaborazione");
                } else {
                    Long[] periodi = datiSerie.getDati().keySet().toArray(new Long[datiSerie.getDati().size()]);
                    Arrays.sort(periodi);// riordino gli idPeriodo                   
                    //Controlla che la serie sia continua
                    if (checkSerie(periodi, periodsPerYear)) {// verificare per frequenze diverse da mensile
                        // Valori mancanti (null): la serie non viene elaborata e l'errore indica i periodi.
                        // (prima si aveva un NullPointerException senza indicazione del periodo)
                        List<Long> periodiMancanti = new ArrayList<>();
                        for (int idx = 0; idx < numValori; idx++) {
                            if (datiSerie.getDati().get(periodi[idx]).getValore() == null) {
                                periodiMancanti.add(periodi[idx]);
                            }
                        }
                        if (!periodiMancanti.isEmpty()) {
                            datiSerie.setEsito("La serie ha " + periodiMancanti.size() + " valori mancanti nei periodi "
                                    + formatArray(periodiMancanti.toArray()));
                        } else {
                            double[] valori = new double[numValori];
                            datiSerie.setValori(valori);
                            for (int idx = 0; idx < numValori; idx++) {
                                // inserisco i valori in ordine di idperiodo
                                valori[idx] = datiSerie.getDati().get(periodi[idx]).getValore();
                            }
                            // Set starting year and month for series, based on periods actually found therein.
                            datiSerie.setIdPeriodoStart(periodi[0]);
                        }
                    } else {// la serie è discontinua
                        datiSerie.setEsito("La serie è discontinua " + formatArray(periodi));
                    }
                }
            } else {
                datiSerie.setEsito("KO");                
            }
        } else {
            datiSerie.setEsito("La serie è vuota");
        }
    }

    /**
     * Per la serie in elaborazione (SeasonalData): - verifica la corrispondenza
     * del primo elemento al periodo in input - verifica la completezza e
     * continuità della serie
     *
     * @param periodiInput array di Long (o oggetti trasformabili in Long) 
     * che contiene i periodi di cui si vuole controllare la continuità
     * @param periodsPerYear numero di periodi in un anno (mesi = 12, trimestri = 4 ecc)
     * @return boolean true sse la lista di periodi è senza interruzioni
     */
    private boolean checkSerie(Object[] periodiInput, Long periodsPerYear) {
        Long[] periodi = ((Long[]) periodiInput);

        if (periodi.length == 0) { return true; }
        Long periodoPrec = periodi[0];

        for (int idx = 1; idx < periodi.length; idx++) {
            Long nextComputedPeriod = nextPeriod(periodoPrec, periodsPerYear);
            if (!nextComputedPeriod.equals(periodi[idx])) {
                return false;
            }
            periodoPrec = nextComputedPeriod;
        }
        return true;
    }

    /**
     * Compute next period given current in the form YYYYMM (base-10 number)
     * and a maximum number of periods per year (ie. months = 12, quarters = 4 etc)
     * 
     * @param currPeriod current period in the form YYYYMM expressed as a 6-digit, base-10 number
     * @param periodsPerYear if operating with months, this should be 12. With quarters, 4.
     */
    private Long nextPeriod(Long currPeriod, Long periodsPerYear) {
        if (periodsPerYear.equals(currPeriod % 100)) {
            // if period count is at maximum value
            // advance one year then reset periods to 1 (ie. take away maximum value and then add 1)
            return currPeriod + 100 - periodsPerYear + 1;
        }
        return currPeriod + 1;
    }

    /**
     * Per la serie in elaborazione (SeasonalData): - estrae il modello Json e i
     * tipi output corrispondenti alla serie - converte il modello Json in
     * DestSpecificationsModel che sarà poi utilizzato da jDemetra+ per
     * impostare le specifications e generare gli output
     *
     * @param datiSerie SeasonalData che raccoglie tutte le informazioni della
     * serie in elaborazione
     */
    private void setSeasonalModel(SeasonalData datiSerie, Long idIndagine) {
        // Leggo il modello e i tipi output dal DB
        setConfigDestDiretta(datiSerie, idIndagine);
        //
        if (datiSerie.getModelloInput() != null && !datiSerie.getModelloInput().trim().isEmpty()
                && datiSerie.getIdTipoOutput() != null && datiSerie.getIdTipoOutput().length > 0) {
            DestSpecificationsModel modello = null;
            try {
                // deserializziamo per prendere i campi custom o che non sono direttamente
                // trattati da jDemetra in base al json
                ObjectMapper mapper = new ObjectMapper();
                // ignora le chiavi non definite (sono quelle trattate direttamente)
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                // accetta singolo valore dove prevista anche array
                mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
                // imposta a null le stringhe vuote
                mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                // crea l'oggetto corrispondente
                // JSON ridotto -> completo: i campi mancanti prendono il default della specifica di base
                // indicata in "spec" (RSA0, RSA1, ...). Un JSON gia' completo resta invariato.
                Map<String, Object> json = mapper.readValue(datiSerie.getModelloInput(),
                        new TypeReference<Map<String, Object>>() {});
                modello = mapper.convertValue(BasicSpecDefaults.complete(json), DestSpecificationsModel.class);
                datiSerie.setModello(modello);

            } catch (Exception e) {
                String testoErrore = "Errore nella conversione del modello: " + e.getMessage();
                java.util.logging.Logger.getLogger(SeasonalDataProcessingServiceImpl.class.getName()).log(Level.SEVERE,
                        testoErrore, e);
                datiSerie.setEsito(testoErrore);
            } finally {
                if (modello != null) {
                    //System.out.println("Serie storica:" + modello.getSeriesName());
                }
            }
        }
    }

    private void setConfigDestDiretta(SeasonalData datiSerie, Long idIndagine) {
        // prendiamo dal DB il modello json e tipi di output richiesti
        // in caso di errore impostiamo a null sia il modelloInput sia idTipoOutput e
        // inseriamo l'errore in esito
        Long idCombinazione = null;
        if (datiSerie.getIdAggregazione() == null) {
            idCombinazione = datiSerie.getIdCombinazione();
        } else {
            idCombinazione = combinazioneRepository.findByIdIndagineIdVariabileIdAggregazione(idIndagine,
                    datiSerie.getIdVariabile(), Long.valueOf(datiSerie.getIdAggregazione()));
            // non sempre univoco per 40722-12-1-1007
        }

        DestDiretta dest = destDirettaRepository.findDestDiretta(idIndagine, datiSerie.getIdVariabile(),
                datiSerie.getIdBase(), idCombinazione, datiSerie.getIdTipoIndicatore());

        // List<DestDiretta> dest = destDirettaRepository.findAll();
        if (dest != null) {
            datiSerie.getOutput().setIdCore(dest.getId().getIdCore());
            if (dest.getSpecifications() != null) {
                String clobAsString;
                try (Reader reader = dest.getSpecifications().getCharacterStream(); StringWriter w = new StringWriter()) {
                    char[] buffer = new char[4096];
                    int charsRead;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        w.write(buffer, 0, charsRead);
                    }
                    clobAsString = w.toString();
                    datiSerie.setModelloInput(clobAsString);// leggere il modello da DEST_DIRETTA

                } catch (IOException | SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            List<Long> idTipoOutput = serieOutputRepository.findIdTipiIndicatoreByIdCore(idIndagine, datiSerie.getOutput().getIdCore());
            datiSerie.setIdTipoOutput(Longs.toArray(idTipoOutput));

        }
    }

    private String formatArray(Object[] righe) {
        StringBuilder res = new StringBuilder();
        String sep = "";
        for (Object riga : righe) {
            res.append(sep).append(riga);
            sep = ", ";
        }
        String result = res.toString();
        if (result.length() > 500) {
            result = result.substring(0, 499);
        }
        return result;
    }

    public Double computeAggregateValue(List<ValorePesoUtility> valoriPesi, Long functionId, String aggregateKey, String componentKeys) {
        Double indexResult = null;

        if (functionId.equals(Funzione.NA.getCodice())) {
            if (valoriPesi.size() != 1) {
                throw new BusinessServiceException("Errore in calcolo serie " + aggregateKey
                    + " : per la funzione identità serve esattamente 1 componente. Componenti trovate: " + componentKeys);
            }
            indexResult = valoriPesi.get(0).getValore();
        }
        else if (functionId.equals(Funzione.MEDIA_P1.getCodice())) {
            indexResult = funzioniDestIndiretta.mediaPonderata(valoriPesi);
        }
        else if (functionId.equals(Funzione.SOMMA.getCodice())) {
            indexResult = funzioniDestIndiretta.somma(valoriPesi);
        }
        else if (functionId.equals(Funzione.MEDIA_P.getCodice())) {
            indexResult = funzioniDestIndiretta.sommaPonderata(valoriPesi);
        }
        else if (functionId.equals(Funzione.DIFF_P.getCodice())) {
            if (valoriPesi.size() != 2) {
                throw new BusinessServiceException("Errore in calcolo serie " + aggregateKey
                    + " : per la funzione differenza ponderata servono esattamente 2 componenti. Componenti trovate: " + componentKeys);
            }
            indexResult = funzioniDestIndiretta.differenzaPonderata(valoriPesi);
        }
        else if (functionId.equals(Funzione.RAPP_P.getCodice())) {
            if (valoriPesi.size() != 2) {
                throw new BusinessServiceException("Errore in calcolo serie " + aggregateKey
                        + " : per la funzione rapporto ponderato servono esattamente 2 componenti. Componenti trovate: " + componentKeys);
            }
            indexResult = funzioniDestIndiretta.rapportoPonderato(valoriPesi);
        }
        else if (functionId.equals(Funzione.DIFF.getCodice())) {
            if (valoriPesi.size() != 2) {
                throw new BusinessServiceException("Errore in calcolo serie " + aggregateKey
                    + " : per la funzione differenza servono esattamente 2 componenti. Componenti trovate: " + componentKeys);
            }
            indexResult = funzioniDestIndiretta.differenza(valoriPesi);
        }
        else if (functionId.equals(Funzione.RAPP.getCodice())) {
            if (valoriPesi.size() != 2) {
                throw new BusinessServiceException("Errore in calcolo serie " + aggregateKey
                        + " : per la funzione rapporto servono esattamente 2 componenti. Componenti trovate: " + componentKeys);
            }
            indexResult = funzioniDestIndiretta.rapporto(valoriPesi);
        }
        else if (functionId.equals(Funzione.TASSO.getCodice())) {
            indexResult = funzioniDestIndiretta.tasso(valoriPesi);
        }
        else if (functionId.equals(Funzione.FIDUCIA.getCodice())) {
            indexResult = funzioniDestIndiretta.mediaPonderata(valoriPesi);
        }
        else {
            throw new BusinessServiceException("funzione non implementata, id funzione: " + functionId);
        }

        return indexResult;
    }
}