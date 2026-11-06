package src;

/**
 *
 * @author alessandro.piovani@istat.it
 */

import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.timeseries.simplets.TsPeriod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtRegDataReaderTSplus {
    
    public static String name(String fileName){
        Matcher matcher = Pattern.compile("[.][^.]+$").matcher(fileName);
        int last=0;
        while (matcher.find()){
            last=matcher.start();
        }
        return last == 0 ? fileName : fileName.substring(0, last);
    }

    // RESTITUISCE UNA LINKED HASHMAP INVECE DI UNA MAP
    public static LinkedHashMap<String, TsData> readTsFile(String directoryPath, String fileName, TsFrequency frequency, int startYear, int startPositionInYear) throws IOException {
        // Crea il percorso completo del file
        
        
        File file = new File(directoryPath, fileName);
//        int end = Pattern.compile("[.][^.]+$").matcher(fileName).start();
        //       String fileNameWithoutExtension = fileName.substring(0, end);
        String fileNameWithoutExtension = name(fileName);
        LinkedHashMap<String, TsData> tsDataMap = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            List<List<Double>> columns = new ArrayList<>();
            int lineNumber = 0;

            // Leggi ogni riga e processa i valori
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
                    throw new IOException(file + ", riga " + lineNumber + ": trovate " + values.length
                            + " colonne invece di " + columns.size());
                }

                // Aggiungi i valori alle rispettive colonne
                for (int i = 0; i < values.length; i++) {
                    try {
                        columns.get(i).add(Double.valueOf(values[i]));
                    } catch (NumberFormatException ex) {
                        throw new IOException(file + ", riga " + lineNumber + ", colonna " + (i + 1)
                                + ": valore non numerico '" + values[i] + "'", ex);
                    }
                }
            }
            if (columns.isEmpty()) {
                throw new IOException(file + ": file vuoto");
            }

            // Crea un oggetto TsData per ogni colonna e aggiungilo alla mappa            
            for (int i = 0; i < columns.size(); i++) {
                String variableName = columns.size() == 1 ? fileNameWithoutExtension
                        : fileNameWithoutExtension + "_" + (i + 1);
                double[] valuesArray = columns.get(i).stream().mapToDouble(Double::doubleValue).toArray();
                TsPeriod firstPeriod = new TsPeriod(frequency, startYear, startPositionInYear);
                TsData tsData = new TsData(firstPeriod, valuesArray, true);
                tsDataMap.put(variableName, tsData);
            }
        }

        return tsDataMap;
    }

//    public static void main(String[] args) {
//        try {
//            String directoryPath = "C:\\Users\\UTENTE\\Documents\\NetBeansProjects\\JD_JSON_processor\\src\\resources\\regr";
//            //String fileName = "bb05ipi.txt";
//            String fileName = "lym_02.txt";
//            TsFrequency frequency = TsFrequency.Monthly; // Frequenza dei dati
//            int startYear = 1990; // Anno di inizio
//            int startPositionInYear = 0; // Posizione di inizio nell'anno
//
//            Map<String, TsData> tsDataMap = readTsFile(directoryPath, fileName, frequency, startYear, startPositionInYear);
//
//            // Stampa i risultati
//            tsDataMap.forEach((key, value) -> System.out.println(key + " -> " + value));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}