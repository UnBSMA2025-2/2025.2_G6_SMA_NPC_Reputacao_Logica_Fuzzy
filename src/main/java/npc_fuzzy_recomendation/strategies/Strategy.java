package npc_fuzzy_recomendation.strategies;

import java.util.ArrayList;

public interface Strategy {
    ArrayList<Double> executeOperation(ArrayList<Double> recvData);
}