package npc_fuzzy_recomendation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import npc_fuzzy_recomendation.strategies.FuzzyStrategy;
import npc_fuzzy_recomendation.strategies.LlmStrategy;
import npc_fuzzy_recomendation.strategies.StatisticStrategy;
import npc_fuzzy_recomendation.strategies.Strategy;

public class Subordinate extends BaseAgent {

	private static final long serialVersionUID = 1L;
	private transient Strategy strategyOp;

	private Map<String, Integer> agentSpeciality = new HashMap<>();

	@Override
	protected void setup() {
		addBehaviour(handleMessages());

		logger.log(Level.INFO, String.format("I'm the %s!", getLocalName()));
		
		registerServices();

		ArrayList<DFAgentDescription> foundAgent = new ArrayList<>(
			Arrays.asList(searchAgentByType(CREATOR)));

		StringBuilder strBld = new StringBuilder();
		agentSpeciality.keySet().forEach(el -> 
			strBld.append(String.format("%s ", el))
		);
		sendMessage(foundAgent.get(0).getName().getLocalName(), ACLMessage.INFORM, String.format("%s %s", "CHECK", strBld.toString().trim()));
	}

	private void registerServices() {
		Object[] args = getArguments();

		if (args != null && args.length > 0) {
			ArrayList<String> agServices = new ArrayList<>();
			agServices.add("Subordinate");
			agServices.add(args[0].toString());
			agentSpeciality.put(args[0].toString(), 1);
			registerDF(this, agServices);
		}
	}

	@Override
	protected OneShotBehaviour handleInform(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				if (msg.getContent().startsWith(THANKS)) {
					logger.log(Level.INFO, String.format("%s RECEIVED THANKS FROM %s!", 
						getLocalName(), msg.getSender().getLocalName()));
				} else {
					logger.log(Level.INFO,
							String.format("%s RECEIVED AN UNEXPECTED MESSAGE FROM %s", getLocalName(),
								msg.getSender().getLocalName()));
				}
			}
		};
	}
	@Override
	protected OneShotBehaviour handleCfp(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				int recvPerformative = msg.getPerformative();

				switch ( recvPerformative ) {
					case ACLMessage.CFP:
						receivedCfpHandler(msg);
						break;
					case ACLMessage.ACCEPT_PROPOSAL:
						receivedAcceptedProposalHandler(msg);
						break;
					case ACLMessage.REJECT_PROPOSAL:
						logger.log(Level.INFO, 
							String.format("PROPOSAL SENT BY %s WAS REJECTED!",
									getLocalName()));
						break;
					default:
						logger.log(Level.INFO,
							String.format("%s %s RECEIVED UNEXPECTED MESSAGE PERFORMATIVE FROM %s %s", ANSI_YELLOW, getLocalName(),
									msg.getSender().getLocalName(), ANSI_RESET));
				}
			}
		};
	}

	private void receivedAcceptedProposalHandler(ACLMessage msg) {
		String reqOperation = msg.getContent().split(" ")[0];
			
		ACLMessage msg2 = msg.createReply();
		boolean strategySet = true;

		if ( !agentSpeciality.containsKey(reqOperation) ) {
		
			String msgContent = String.format("OPERATION %s UNKNOWN", reqOperation);
			msg2.setContent(msgContent);
			msg2.setPerformative(ACLMessage.REFUSE);
			logger.log(Level.INFO, String.format("%s SENT OPERATION UNKNOWN MESSAGE TO %s", getLocalName(),
				msg.getSender().getLocalName()));
		
		} else {

			workingData.clear();
			workingData = parseData(msg);
			dataSize = workingData.size();

			logger.log(Level.INFO, String.format("%s AGENT RECEIVED A TASK (%s) AND DATA: %s!",
					getLocalName(), reqOperation, workingData.toString()));

			switch (reqOperation) {
				case LLM:
					strategyOp = new LlmStrategy();
					break;
				case FUZZY:
					strategyOp = new FuzzyStrategy();
					break;
				case STATISTIC:
					strategyOp = new StatisticStrategy();
					break;
				default:
					strategySet = false;
			}
			
			msg2 = handleSetStrategy(msg, reqOperation, msg2, strategySet);
		}
		send(msg2);
	}

	private ACLMessage handleSetStrategy(ACLMessage msg, String reqOperation, ACLMessage msg2, boolean strategySet) {
		if (strategySet) {
			ArrayList<Double> result = new ArrayList<>();
			ArrayList<Object> numericData = new ArrayList<>();
			if(reqOperation.equals(LLM)) {
				numericData.addAll(workingData);
			} else {
				for (String data : workingData) {
					try {
						numericData.add(Double.parseDouble(data));
					} catch (NumberFormatException e) {
						continue;
					}
				}
			}

			
			ArrayList<Double> objRet = strategyOp.executeOperation(numericData);
			String strRet = objRet.stream().map(val -> String.format("%s", Double.toString(val)))
				.collect(Collectors.joining(" ")).trim();
			
			logger.log(Level.INFO, String.format("%s I'm %s and I performed %s on data, resulting on: %s %s", ANSI_GREEN, 
				getLocalName(), reqOperation, strRet, ANSI_RESET));
			
			msg2.setPerformative(ACLMessage.INFORM);
			msg2.setContent(String.format("%s %s %s %d %s", INFORM, reqOperation, PLAYER_DATA, objRet.size(), strRet));
			
			logger.log(Level.INFO, String.format("%s SENT RETURN PLAYER_DATA MESSAGE TO %s", getLocalName(),
				msg.getSender().getLocalName()));
		} else {
			logger.log(Level.INFO,
				String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG,
					msg.getSender().getLocalName()));
				
			String msgContent = String.format("OPERATION %s UNKNOWN", reqOperation);
			msg2.setContent(msgContent);
			msg2.setPerformative(ACLMessage.REFUSE);
			logger.log(Level.INFO, String.format("%s SENT OPERATION UNKNOWN MESSAGE TO %s", getLocalName(),
				msg.getSender().getLocalName()));
		}

		return msg2;
	}

	private void receivedCfpHandler(ACLMessage msg) {
		// Extract the requested operation from the message
		String reqOperation = msg.getContent().split(" ")[0];
		
		ACLMessage reply = msg.createReply();
		
		if (agentSpeciality.containsKey(reqOperation)) {
			// Agent can perform this operation
			reply.setPerformative(ACLMessage.PROPOSE);
			reply.setContent(String.format("CAN_DO %s", reqOperation));
			logger.log(Level.INFO, String.format("%s SENT PROPOSE FOR OPERATION %s TO %s", 
				getLocalName(), reqOperation, msg.getSender().getLocalName()));
		} else {
			// Agent cannot perform this operation
			reply.setPerformative(ACLMessage.REFUSE);
			reply.setContent(String.format("OPERATION %s UNKNOWN", reqOperation));
			logger.log(Level.INFO, String.format("%s SENT REFUSE FOR UNKNOWN OPERATION %s TO %s", 
				getLocalName(), reqOperation, msg.getSender().getLocalName()));
		}
		
		send(reply);
	}
}