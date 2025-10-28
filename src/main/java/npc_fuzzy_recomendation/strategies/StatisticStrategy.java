package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;

public class StatisticStrategy implements Strategy {
    @Override
    public ArrayList<Double> executeOperation(ArrayList<Double> recvData) {
        ArrayList<Double> result = new ArrayList<>();
        if (recvData.size() >= 4) {
            double kills = recvData.get(0);
            double level = recvData.get(1);
            double achievements = recvData.get(2);
            double deaths = recvData.get(3);
            
            // Statistical analysis focusing on performance metrics:
            // - K/D ratio is primary
            // - Level indicates experience
            // - Achievements are bonus points
            double kdRatio = deaths > 0 ? kills / deaths : kills;
            double kdScore = Math.min(100, (kdRatio / 3.0) * 100);  // 3:1 ratio is considered good
            double experienceScore = Math.min(100, (level / 60.0) * 100);  // Level 60 is baseline for experienced
            double achievementBonus = Math.min(20, achievements * 2);  // Each achievement adds 2% up to 20%
            
            // Calculate final score with statistical weights
            double approvalScore = (kdScore * 0.5 + experienceScore * 0.3) * (1 + achievementBonus / 100.0);
            result.add(Math.min(100, approvalScore));  // Cap at 100%
        } else {
            result.add(0.0);  // Invalid data
        }
        return result;
    }
}