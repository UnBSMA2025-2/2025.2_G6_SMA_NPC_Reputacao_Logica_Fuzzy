package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;

public class StatisticStrategy implements Strategy {
    @Override
    public ArrayList<Double> executeOperation(ArrayList<Object> recvData) {
        ArrayList<Double> result = new ArrayList<>();
        if (recvData.size() >= 4) {
            double kills = (double) recvData.get(0);
            double level = (double) recvData.get(1);
            double achievements = (double) recvData.get(2);
            double deaths = (double) recvData.get(3);
            
            double kdRatio = deaths > 0 ? kills / deaths : kills;
            double kdScore = Math.min(100, (kdRatio / 3.0) * 100);
            double experienceScore = Math.min(100, (level / 60.0) * 100);
            double achievementBonus = Math.min(20, achievements * 2);
            
            double approvalScore = (kdScore * 0.5 + experienceScore * 0.3) * (1 + achievementBonus / 100.0);
            result.add(Math.min(100, approvalScore));
        } else {
            result.add(0.0);
        }
        return result;
    }
}