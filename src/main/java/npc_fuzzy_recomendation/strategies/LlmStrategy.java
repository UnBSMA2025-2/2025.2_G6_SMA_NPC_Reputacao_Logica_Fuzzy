package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;
import java.io.IOException;
import io.github.cdimascio.dotenv.Dotenv;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LlmStrategy implements Strategy {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("API_KEY");
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    @Override
    public ArrayList<Double> executeOperation(ArrayList<Double> recvData) {
        
        ArrayList<Double> result = new ArrayList<>();
        
        if (recvData.size() < 4) {
            result.add(0.0);
            return result;
        }
        
        double kills = recvData.get(0);
        double level = recvData.get(1);
        double achievements = recvData.get(2);
        double deaths = recvData.get(3);

        double levelScore = Math.min(100, (level / 80.0) * 100); 
        double achievementScore = Math.min(100, (achievements / 8.0) * 100);
        double kdRatio = deaths > 0 ? kills / deaths : kills;
        double kdScore = Math.min(100, (kdRatio / 5.0) * 100); 
        double calculatedApprovalScore = (levelScore * 0.4 + achievementScore * 0.4 + kdScore * 0.2);
        
        String npcPersona = "";
        if (recvData.size() > 4) {
            npcPersona = String.valueOf(recvData.get(4)).trim();
        }

        String prompt = String.format(
            "Você está atuando como um avaliador com a seguinte personalidade: \"%s\".\n" + 
            "Avalie este jogador com base na personalidade e os seguintes dados, então retorne uma pontuação de aprovação entre 0 e 100 no campo 'score' do JSON.\n" +
            "Dados do jogador:\n" +
            "- Kills: %.0f\n- Level: %.0f\n- Achievements: %.0f\n- Deaths: %.0f\n\n" +
            "Considere que um nível 80 e 8 achievements são excelentes. Retorne SOMENTE o objeto JSON.",
            npcPersona, kills, level, achievements, deaths);

        try {
            double llmApprovalScore = callGemini(prompt);
            result.add(llmApprovalScore); 
        } catch (Exception e) {
            System.err.println("Erro ao chamar API Gemini. Usando pontuação calculada como fallback.");
            e.printStackTrace();
            result.add(calculatedApprovalScore); 
        }

        result.add(calculatedApprovalScore);
        
        return result;
    }

    private double callGemini(String prompt) throws IOException {

        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        JsonObject scoreProperty = new JsonObject();
        scoreProperty.addProperty("type", "number");
        scoreProperty.addProperty("description", "A pontuação de aprovação do jogador de 0 a 100.");

        JsonObject responseSchema = new JsonObject();
        responseSchema.addProperty("type", "object");
        responseSchema.add("properties", new JsonObject());
        responseSchema.getAsJsonObject("properties").add("score", scoreProperty);
        
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.add("responseSchema", responseSchema);


        JsonObject requestBodyJson = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject item = new JsonObject();
        item.add("parts", parts);
        contents.add(item);
        
        requestBodyJson.add("contents", contents);
        requestBodyJson.add("generationConfig", generationConfig);


        RequestBody body = RequestBody.create(
            gson.toJson(requestBodyJson),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(GEMINI_API_URL)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", API_KEY)
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Erro API: " + response.code() + ". Body: " + (response.body() != null ? response.body().string() : "N/A"));
                throw new IOException("Erro ao chamar API Gemini: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                System.err.println("Resposta inválida (sem candidates). Corpo: " + responseBody);
                return 0.0;
            }

            JsonElement textElement = candidates.get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text");

            if (textElement == null) {
                System.err.println("Resposta inválida (texto vazio). Corpo: " + responseBody);
                return 0.0;
            }

            String responseJsonString = textElement.getAsString();

            try {
                JsonObject scoreObject = gson.fromJson(responseJsonString, JsonObject.class);
                if (scoreObject != null && scoreObject.has("score")) {
                    System.err.println(responseBody);
                    return scoreObject.get("score").getAsDouble();
                } else {
                    System.err.println("JSON retornado pelo Gemini não contém o campo 'score'. JSON: " + responseJsonString);
                    return 0.0;
                }
            } catch (Exception e) {
                System.err.println("Erro ao converter resposta do Gemini para JSON: " + responseJsonString);
                e.printStackTrace();
                return 0.0;
            }
        }
    }
}
