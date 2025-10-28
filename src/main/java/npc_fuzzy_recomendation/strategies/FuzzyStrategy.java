package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;

public class FuzzyStrategy implements Strategy {
    @Override
    public ArrayList<Double> executeOperation(ArrayList<Double> recvData) {
        ArrayList<Double> result = new ArrayList<>();
        if (recvData.size() >= 4) {
            double kills = recvData.get(0);
            double level = recvData.get(1);
            double achievements = recvData.get(2);
            double deaths = recvData.get(3);
            
            // Simple fuzzy logic:
            // - High kills (>500) and low deaths (<50) is good
            // - High level (>50) is good
            // - Many achievements (>5) is good
            double killScore = Math.min(100, (kills / 1000.0) * 100);
            double deathPenalty = Math.max(0, 100 - (deaths / 50.0) * 100);
            double levelScore = Math.min(100, (level / 100.0) * 100);
            double achievementScore = Math.min(100, (achievements / 10.0) * 100);
            
            // Calculate weighted average
            double approvalScore = (killScore * 0.3 + deathPenalty * 0.2 + levelScore * 0.3 + achievementScore * 0.2);
            result.add(approvalScore);
        } else {
            result.add(0.0);  // Invalid data
        }
        return result;
    }
}