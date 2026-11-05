package src;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 *
 * @author alessandro.piovani@istat.it
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonReader {

    public static List<Map<String, Object>> readJsonFile(String filePath) throws IOException {
        // Crea un ObjectMapper, che è la classe principale di Jackson per il mapping JSON<->Java
        ObjectMapper objectMapper = new ObjectMapper();

        // Accetta i commenti in stile Java nel file JSON: // fino a fine riga e /* ... */
        // (anche su piu' righe). Vengono ignorati nella lettura.
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

        // Legge il file JSON e lo converte in una lista di mappe
        List<Map<String, Object>> jsonData = objectMapper.readValue(
                new File(filePath), new TypeReference<List<Map<String, Object>>>() {});

        // JSON ridotto -> completo: i campi mancanti prendono il default della specifica di base
        // indicata in "spec" (RSA0, RSA1, ...). Un JSON gia' completo resta invariato.
        List<Map<String, Object>> completed = new ArrayList<>(jsonData.size());
        for (Map<String, Object> series : jsonData) {
            completed.add(BasicSpecDefaults.complete(series));
        }
        return completed;
    }
}