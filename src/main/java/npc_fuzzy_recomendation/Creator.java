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

		logger.log(Level.INFO, "Agents started...");
		pauseSystem();

		// send them a message demanding start
		logger.log(Level.INFO, "Starting system!");

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
			AgentContainer container = getContainerController(); // get a container controller for creating new agents
			AgentController newAgent = container.createNewAgent(agentName, className, args);
			newAgent.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE LAUNCHING AGENTS %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}

	@Override
	protected OneShotBehaviour handleRequest(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				if (msg.getContent().startsWith(CREATE)) {
					String [] splittedMsg = msg.getContent().split(" ");

					if(splittedMsg[1].equals("Ballot")){
						try {
							String ballot = String.format("%s%d", "ballot_", ballotCnt);

							ballotCnt++;
							
							launchAgent(ballot, "election_structure.Ballot", null);	
							
							AgentContainer container = getContainerController();
							logger.log(Level.INFO, String.format("%s CREATED AND STARTED NEW BALLOT: %s ON CONTAINER %s",
							getLocalName(), ballot, container.getName()));	
						} catch (Exception any) {
							logger.log(Level.SEVERE, String.format("%s ERROR WHILE CREATING AGENTS %s", ANSI_RED, ANSI_RESET));
							any.printStackTrace();
						}
					}else{
						logger.log(Level.SEVERE, String.format("%s UNEXPECTED AGENT REQUESTED %s", ANSI_YELLOW, ANSI_RESET));
					}
				}
			}
		};
	}
}