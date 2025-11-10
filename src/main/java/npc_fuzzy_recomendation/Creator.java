package npc_fuzzy_recomendation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;

import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Class that set the main agent and it's actions
 */
public class Creator extends BaseAgent {

	private static final long serialVersionUID = 1L;

	private int ballotCnt = 0;

	@Override
	protected void setup() {

		loggerSetup();

		registerDF(this, CREATOR, CREATOR);
		addBehaviour(handleMessages());

		logger.log(Level.INFO, "Starting Agents...");

		logger.log(Level.INFO, "Creating Workers...");

		ArrayList<String> votersName = new ArrayList<>();
		
		Object[] args = getArguments();
		int votersQuorum = 0;
		if (args != null && args.length > 0) {
			votersQuorum = Integer.parseInt(args[0].toString());
		}

		launchAgent("Player", PlayerAgent.class.getName(), null);

		launchAgent("NPC1", NPCAgent.class.getName(), new Object[] {"Agressivo e competitivo"});
		launchAgent("NPC2", NPCAgent.class.getName(), new Object[] {"Ambicioso e estratégico"});
		launchAgent("NPC3", NPCAgent.class.getName(), new Object[] {"Impulsivo e resiliente"});

		launchAgent("Subordinate1", Subordinate.class.getName(), new Object[] { LLM });
		launchAgent("Subordinate2", Subordinate.class.getName(), new Object[] { FUZZY });
		launchAgent("Subordinate3", Subordinate.class.getName(), new Object[] { STATISTIC });


		logger.log(Level.INFO, "Agents started...");
		pauseSystem();

		logger.log(Level.INFO, "Starting system!");
		sendMessage("Player", ACLMessage.INFORM, String.format("%s", START));
	}

	private void pauseSystem() {
		try {
			logger.log(Level.WARNING, String.format(
					"%s The system is paused -- this action is here only to let you activate the sniffer on the agents, if you want (see documentation) %s",
					ANSI_YELLOW, ANSI_RESET));
			logger.log(Level.WARNING,
					String.format("%s Press enter in the console to start the agents %s", ANSI_YELLOW, ANSI_RESET));
			System.in.read();
		} catch (IOException e) {
			logger.log(Level.SEVERE, String.format("%s ERROR STARTING THE SYSTEM %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}

	private void launchAgent(String agentName, String className, Object[] args) {
		try {
			AgentContainer container = getContainerController(); 
			AgentController newAgent = container.createNewAgent(agentName, className, args);
			newAgent.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE LAUNCHING AGENTS %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}
}