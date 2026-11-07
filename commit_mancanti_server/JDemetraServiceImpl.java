package it.istat.koala.service.impl;

import it.istat.koala.controller.AliveController;
import it.istat.koala.utility.FunzioniDestIndiretta;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.algorithm.IProcResults;
import ec.tstoolkit.algorithm.ProcessingContext;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsDataTable;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import it.istat.koala.data.repository.DestCatalogoCoreRepository;
import it.istat.koala.data.repository.SerieOutputRepository;
import it.istat.koala.dto.SoglieDTO;
import it.istat.koala.service.JDemetraService;
import it.istat.koala.service.JobErroriService;
import it.istat.koala.utility.DestSpecificationsModel;
import it.istat.koala.utility.SeasonalData;
import it.istat.koala.utility.TSmodelSetup;
import it.istat.smetabrowser.data.entity.TipoIndicatore;
import it.istat.smetabrowser.data.repository.TipoIndicatoreRepository;

/**
 *
 * @authors giacomi, apiovani (alessandro.piovani@istat.it)
 */
@Service("JDemetraService")
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class JDemetraServiceImpl implements JDemetraService {

    private final FunzioniDestIndiretta funzioniDestIndiretta;

    private final AliveController aliveController;

    public static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static class SogliePair {
        private Double sogliaMin;
        private Double sogliaMax;

        private SogliePair(Double sogliaMin, Double sogliaMax) {
            this.sogliaMin = sogliaMin;
            this.sogliaMax = sogliaMax;
        }

        private Double getSogliaMin() {
            return this.sogliaMin;
        }

        private void setSogliaMin(Double sogliaMin) {
            this.sogliaMin = sogliaMin;
        }

        private Double getSogliaMax() {
            return this.sogliaMax;
        }

        private void setSogliaMax(Double sogliaMax) {
            this.sogliaMax = sogliaMax;
        }
    }

    private final HashMap<Long, String> tipiIndicatore = new HashMap();

    private final HashMap<String, Long> tipiIndicatoreDescKey = new HashMap();

    @Autowired
    private TipoIndicatoreRepository tipoIndicatoreRepository;

    @Autowired
    private TSmodelSetup TSmodelSetup;

    @Autowired
    private DestCatalogoCoreRepository destCatalogoCoreRepository;

    @Autowired
    private SerieOutputRepository serieOutputRepository;

    JDemetraServiceImpl(AliveController aliveController, FunzioniDestIndiretta funzioniDestIndiretta) {
        this.aliveController = aliveController;
        this.funzioniDestIndiretta = funzioniDestIndiretta;
    }

    @Override
    public String apply(Long cubeId) throws Exception {

        return "todo";
    }

    @Override
    public String process(SeasonalData datiSerie, Long id, Long idIndagine) throws Exception {
        List<TipoIndicatore> listTipiInd = tipoIndicatoreRepository.findAll();
        for (TipoIndicatore tipo : listTipiInd) {
            tipiIndicatore.put(tipo.getId(), tipo.getDescTipoIndicatore());
            tipiIndicatoreDescKey.put(tipo.getDescTipoIndicatore(), tipo.getId());
        }
        if (datiSerie.getModello() != null) {

            if ("X".equals(datiSerie.getModello().getMethod())) { // X11 family processing (TODO)
                processXspec(datiSerie);
            } else { // TRAMO-SEATS processing
                processTSspec(datiSerie, idIndagine);
            }
        }
        return "ok";
    }

    private long[] getDefaultResultTypes() {
        long[] rsltTypes = new long[1];
        rsltTypes[0] = 2; // DestTipoDestag.SA.getIdTipoIndicatoreOutput();
        return rsltTypes;
    }

    private String processTSspec(SeasonalData datiSerie, Long idIndagine) {
        TramoSeatsSpecification specifications = null;
        String seriesName = destCatalogoCoreRepository.findByIdCore(datiSerie.getOutput().getIdCore());
        // What we really need is a map from (idCore, idTipoOutput) to a pair of (sogliaMin, sogliaMax)
        HashMap<String, SogliePair> mapSoglie = this.mapDestCatalogoDett(idIndagine);

        ProcessingContext context = new ProcessingContext();

        long[] rsltTypes = datiSerie.getIdTipoOutput();
        if (rsltTypes == null || rsltTypes.length == 0) {
            rsltTypes = getDefaultResultTypes();
        }

        TsData inputData = new TsData(TsFrequency.valueOf(datiSerie.getModello().getFrequency()), datiSerie.getAnno(),
                datiSerie.getMese(), datiSerie.getValori(), true);

        try {
            DestSpecificationsModel model = datiSerie.getModello();
            TSmodelSetup.TSmodelSetupMetod(model, context, inputData);

            specifications = TSmodelSetup.getTsSpec();

        } catch (Exception e) {
            System.out.println("conversione del modello non riuscita: " + e.getStackTrace());
            throw e;
        }

        // System.out.println("Dati grezzi in input
        // "+datiSerie.getOutputKey()+"\n"+inputData);
        /* TODO Decidere se inserire anche i dati di input o no */
        datiSerie.addRisultato(tipiIndicatore.get(1L), inputData);

        // Just to store the results (not necessary in other usages)
        TsDataTable table = new TsDataTable();
        table.insert(-1, inputData);

        // Process
        // new, qui prendo i dati di input dopo che sono stati filtrati con le date di
        // inizio e fine che ho messo nel modello
        IProcResults rslts = TramoSeatsProcessingFactory.process(TSmodelSetup.getTsData(), specifications, context);
        // risultati completi per i report diagnostici (letti e rilasciati da SeasonalDataProcessingServiceImpl)
        if (rslts instanceof CompositeResults) {
            datiSerie.setJdResults((CompositeResults) rslts);
        }

        /*
         * TODO need to rehaul this for loop.
         * We loop across result types, but then there's a foreach across all result
         * types added up to that point.
         * So in general, in the second iteration, we loop again over the first result
         * type.
         * In addition, we need to reliably throw an exception when model fails to be
         * applied, ie when 'ris' is null and can't be foreach'ed over.
         * If that happens, ideally we don't want to return "ok" and the result/summary
         * object in the caller method should not include this series at all.
         */
        for (int idx = 0; idx < rsltTypes.length; idx++) {
            String resType = null;
            try {
                resType = tipiIndicatore.get(rsltTypes[idx]);
                if (resType != null && resType.contains("-")) {
                    String typeTSSearch = StringUtils.substringBefore(resType, "-").toLowerCase(); // returns "abc"

                    TsData result = rslts.getData(typeTSSearch, TsData.class);

                    datiSerie.addRisultato(resType, result);

                    Long idCore = datiSerie.getOutput().getIdCore();
                    datiSerie.getRisultati().forEach((key, ris) -> {
                        if (!key.equals("GREZZO")) {
                            Long idTipoInd = tipiIndicatoreDescKey.get(key);
                            SogliePair soglie = mapSoglie.get(idCore + "_" + idTipoInd);
                            if (soglie != null) {
                                if (ris == null) {
                                    System.out.println("risultato NULL per serie " + datiSerie.getKey());
                                } else {
                                    ris.forEach((keyValue, value) -> {
                                        value = funzioniDestIndiretta.clampToThresholds(value, soglie.getSogliaMin(), soglie.getSogliaMax());
                                        ris.put(keyValue, value);
                                    });
                                }
                            }
                        }
                    });
                    table.insert(-1, result);
                    // System.out.println("TsData "+resType+"\n"+result);
                }
            } catch (Exception e) {
                System.out.println("Errore in elab risultato " + resType + ":\n" + e.getMessage());
            }
        }
        datiSerie.setLikelihoodBIC(rslts.getData("likelihood.bicc", Double.class));   
        return "ok";
    }

    private String processXspec(SeasonalData datiSerie) {
        return "ok";
    }

    private HashMap<String, SogliePair> mapDestCatalogoDett(Long idIndagine) {
        HashMap<String, SogliePair> map = new HashMap<String, SogliePair>();
        // need: something that outputs idCore and idTipoOutput for all series with any thresholds at all.
        // ALSO NEED DestCatalogoDett entity for some reason?
        List<SoglieDTO> serieList = serieOutputRepository.findByIdIndAndSoglie(idIndagine);
        for (SoglieDTO s : serieList) {
            map.put(s.getIdCore() + "_" + s.getIdTipoOutput(), new SogliePair(s.getSogliaMin(), s.getSogliaMax()));
        }
        return map;

    }

}
