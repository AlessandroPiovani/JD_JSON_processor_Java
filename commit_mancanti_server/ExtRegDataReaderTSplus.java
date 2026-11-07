package it.istat.koala.utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import it.istat.koala.data.repository.DestRegressoreRepository;

/**
 * Legge un regressore in formato TS+ (valori separati da tabulazione, una riga per periodo,
 * una colonna per variabile) dalla tabella dei regressori.
 * Stessa logica della versione locale che legge da file: righe vuote ignorate, spazi a inizio
 * e fine riga ignorati, stesso numero di colonne su tutte le righe, stessi nomi delle variabili.
 * Ogni problema genera una IOException con il dettaglio (regressore, riga, colonna): in
 * TSmodelSetup diventa un errore che ferma la serie, invece di stimarla senza regressore.
 *
 * @author alessandro.piovani@istat.it
 */
@Service
public class ExtRegDataReaderTSplus {
	@Autowired
	private DestRegressoreRepository destRegressoreRepository;

    public LinkedHashMap<String, TsData> readTsFile(String fileName, TsFrequency frequency, int startYear, int startPositionInYear) throws IOException {
        // Lettura del contenuto dal DB
        String regressorData;
        try {
            Clob regressorDataClob = destRegressoreRepository.findByFileName(fileName);
            if (regressorDataClob == null) {
                throw new IOException("regressore " + fileName + " non trovato nel DB");
            }
            long length = regressorDataClob.length();
            regressorData = length == 0 ? "" : regressorDataClob.getSubString(1, (int) length);
        } catch (SQLException e) {
            // prima l'errore veniva solo stampato e il regressore risultava vuoto
            throw new IOException("regressore " + fileName + ": lettura dal DB non riuscita - " + e.getMessage(), e);
        }

        String fileNameWithoutExtension = fileName.replaceFirst("[.][^.]+$", "");
        LinkedHashMap<String, TsData> tsDataMap = new LinkedHashMap<>();
        List<List<Double>> columns = new ArrayList<>();

        // BufferedReader: gestisce allo stesso modo fine riga Windows (\r\n) e Unix (\n)
        try (BufferedReader br = new BufferedReader(new StringReader(regressorData))) {
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                // Salta le righe vuote o di soli spazi/tabulazioni (es. "a capo" finale)
                if (line.trim().isEmpty()) {
                    continue;
                }
                // trim(): ignora tabulazioni e spazi a inizio/fine riga
                String[] values = line.trim().split("\\s*\t\\s*");

                // Inizializza le colonne al primo passaggio
                if (columns.isEmpty()) {
                    for (String value : values) {
                        columns.add(new ArrayList<>());
                    }
                }
                if (values.length != columns.size()) {
                    throw new IOException("regressore " + fileName + ", riga " + lineNumber + ": trovate "
                            + values.length + " colonne invece di " + columns.size());
                }

                // Aggiungi i valori alle rispettive colonne
                for (int i = 0; i < values.length; i++) {
                    try {
                        columns.get(i).add(Double.valueOf(values[i]));
                    } catch (NumberFormatException ex) {
                        throw new IOException("regressore " + fileName + ", riga " + lineNumber + ", colonna "
                                + (i + 1) + ": valore non numerico '" + values[i] + "'", ex);
                    }
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IOException("regressore " + fileName + ": contenuto vuoto nel DB");
        }

        // Crea un oggetto TsData per ogni colonna e aggiungilo alla mappa.
        // Nomi come nella versione locale: "nome" se il file ha una sola colonna, "nome_1", "nome_2", ... se ne ha piu' di una
        for (int i = 0; i < columns.size(); i++) {
            String variableName = columns.size() == 1 ? fileNameWithoutExtension
                    : fileNameWithoutExtension + "_" + (i + 1);
            double[] valuesArray = columns.get(i).stream().mapToDouble(Double::doubleValue).toArray();
            TsPeriod firstPeriod = new TsPeriod(frequency, startYear, startPositionInYear);
            TsData tsData = new TsData(firstPeriod, valuesArray, true);
            tsDataMap.put(variableName, tsData);
        }
        return tsDataMap;
    }
}
