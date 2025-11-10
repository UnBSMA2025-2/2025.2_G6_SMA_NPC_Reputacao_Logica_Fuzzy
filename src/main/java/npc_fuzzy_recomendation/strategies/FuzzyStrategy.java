package npc_fuzzy_recomendation.strategies;

import java.io.InputStream;
import java.util.ArrayList;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;

public class FuzzyStrategy implements Strategy {
    @Override
    public ArrayList<Double> executeOperation(ArrayList<Object> recvData) {
        ArrayList<Double> result = new ArrayList<>();
        if (recvData.size() >= 4) {
            double kills = (double) recvData.get(0);
            double level = (double) recvData.get(1);
            double achievements = (double) recvData.get(2);
            double deaths = (double) recvData.get(3);

            String resourcePath = "/fuzzy/fuzzy_reputation.fcl";
            try (InputStream fclStream = getClass().getResourceAsStream(resourcePath)) {

                if (fclStream == null) {
                    System.err.println("Erro: Não foi possível encontrar o arquivo FCL: " + resourcePath);
                    result.add(0.0);
                    return result;
                }

                FIS fis = FIS.load(fclStream, true);

                if (fis == null) {
                    System.err.println("Erro: Falha ao carregar o FIS.");
                    result.add(0.0);
                    return result;
                }

                Variable vKills = fis.getVariable("kills");
                Variable vLevel = fis.getVariable("level");
                Variable vAchievements = fis.getVariable("achievements");
                Variable vDeaths = fis.getVariable("deaths");

                if (vKills != null) vKills.setValue(kills);
                if (vLevel != null) vLevel.setValue(level);
                if (vAchievements != null) vAchievements.setValue(achievements);
                if (vDeaths != null) vDeaths.setValue(deaths);

                fis.evaluate();

                Variable vApproval = fis.getVariable("approval");
                double approval = (vApproval != null) ? vApproval.getValue() : 0.0;

                result.add(approval);

            } catch (Exception e) {
                e.printStackTrace();
                result.add(0.0);
            }
        } else {
            result.add(0.0);
        }
        return result;
    }
}