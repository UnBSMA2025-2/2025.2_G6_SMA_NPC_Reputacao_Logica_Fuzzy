package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;

public class LlmStrategy implements Strategy {
    @Override
    public ArrayList<Double> executeOperation(ArrayList<Double> recvData) {
        ArrayList<Double> result = new ArrayList<>();
        if (recvData.size() >= 4) {
            double kills = recvData.get(0);
            double level = recvData.get(1);
            double achievements = recvData.get(2);
            double deaths = recvData.get(3);
            
            // LLM simulation with focus on level and achievements:
            // - Level is primary indicator
            // - Achievements show dedication
            // - K/D ratio is secondary
            double levelScore = Math.min(100, (level / 80.0) * 100);  // Level 80 is considered max
            double achievementScore = Math.min(100, (achievements / 8.0) * 100);
            double kdRatio = deaths > 0 ? kills / deaths : kills;
            double kdScore = Math.min(100, (kdRatio / 5.0) * 100);  // 5:1 ratio is considered excellent
            
            // Weighted scoring with emphasis on level and achievements
            double approvalScore = (levelScore * 0.4 + achievementScore * 0.4 + kdScore * 0.2);
            result.add(approvalScore);
        } else {
            result.add(0.0);  // Invalid data
        }
        return result;
    }
}