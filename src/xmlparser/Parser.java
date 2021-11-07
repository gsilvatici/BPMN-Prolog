package xmlparser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.util.Scanner;

/**
 * 
 * @author Stefan_524450
 * 
 *         This class is used to parse a xml/bpmn file representing a business
 *         process It generates a prolog file which acts as a knowledge base
 *         describing the business process
 *
 */

public class Parser {

	private static int N;
	public static String[] activities;
	private static String[] activitiesIds;
	private static String[] subProcessesNames;
	private static String[] subProcessesIds;
	private static String[] subProcessesStartActivities;
	private static String[] subProcessesEndActivities;
	public static String[] agents;
	private static Edge[] flow;
	private static List<String> rules;
	public static String processName;
	public static List<xorSplit> xorSplits;
	public static HashSet<String> holds_at;
	public static HashSet<String> holds;
	public static int terms;
	public static int rulesCount;
	public static int facts;
	
	public static void mainParser(Document doc, BufferedWriter out) {

		try {
			// First, we get the name of the business process
			String processId = null;
			processName = null;
			NodeList nList = doc.getElementsByTagName("bpmn2:process");
			Node nNode = nList.item(0);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				processId = eElement.getAttribute("id");
				processName = eElement.getAttribute("name");
			}
			if (processName == null) {
				processName = processId;
			}

			// We start by writing basic rules that are present in any prolog
			// file formalizing a business process
			
			writePrologCode(out);
		
			// filling in the flow
			fillInFlow(out, doc);	//every flows id name source n target

			// filling in activities
			fillInActivities(out, doc);	//every activites like task and sub processes are stored in array n write rules for them

			// agents
			agents(out, doc);	//every lanes name stored in array 
			
			dynamicDuration(out);
			
			
			
			// rules search
			// With each method, we save what rules we have found in the list
			// rules
			// so that we only add the rules we need in the end
			rules = new ArrayList<String>();
			sequential(out);
			sequential_process(out);
			andsplitjoin(out, doc);
			xorsplitjoin(out, doc);
			stepRules(out);
			
			// We now complete the prolog code with the limited_history rules
			out.write("\n");
			rulesCount++;
			out.write("extend_history( H, [Happens|H] ) :- step( Happens, H )." + "\n");
			if (rules.contains("andsplit")) {
				rulesCount++;
				out.write("extend_history( H, HH ) :- steps( List, H ), append( List, H, HH )." + "\n" + "\n");
			} else
				out.write("\n");
			rulesCount++;
			out.write("limited_history( HH, N ) :- 1<N, N1 is N-1,limited_history( H, N1 ),extend_history( H, HH )."
					+ "\n");
			rulesCount++;
			out.write("limited_history( [happens(start(act(" + activities[0] + ",1)),0)], N ) :- 0<N." + "\n" + "\n");
			

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	private static void writePrologCode(BufferedWriter out) {
		try {
			rulesCount++;
			out.write("happens3(start(A),T,H) :- member(happens(start(A),T),H)." + "\n");
			rulesCount++;
			out.write("happens3(end(A),T2,H) :- happens3(start(A),T1,H), dur_id(A,Y), T2 is T1+Y." + "\n" + "\n");
			rulesCount++;
			out.write("dur_id( act(A,_), T ) :- dur( A, T )." + "\n" + "\n");
			rulesCount++;
			out.write("newId( Action, Id_pre, Id ) :- action_number(Action,C),no_of_actions(N),Id is Id_pre*N + C."
					+ "\n" + "\n");
			rulesCount++;
			out.write(
					"follows(A1,A2) :- no_of_actions(N),limited_history([H0|H1],N),H0=happens(start(act(A1,_)),_),H1=[happens(start(act(A2,_)),_)|_],happens3(end(act(A1,_)),_,[H0|H1])."
							+ "\n");
			rulesCount++;
			out.write(
					"follows(A1,A2) :- no_of_actions(N),limited_history([H0|H1],N),H1=[happens(start(act(A1,_)),_)|[happens(start(act(A2,_)),_)|_]],happens3(end(act(A1,_)),_,[H0|H1])."
							+ "\n" + "\n");
			rulesCount++;
			out.write("list_follows(N,A,[A1|L]) :- N>1, N1 is N-1, follows(A1,A), list_follows(N1,A1,L)." + "\n");
			rulesCount++;
			out.write("list_follows(N,A,[A1]) :- N>0, follows(A1,A)." + "\n" + "\n");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void fillInFlow(BufferedWriter out, Document doc) {
		// For each edge ("sequenceFlow"), we create an object Edge
		// An Edge contains an id, a name, the id of the source, the id of the
		// target, and eventually a condition
		// The source and the target may be activities, subprocesses, events, or
		// gateways
		NodeList nList = doc.getElementsByTagName("bpmn2:sequenceFlow");
		flow = new Edge[nList.getLength()];
		int k = 0;
		for (int temp = 0; temp < nList.getLength(); temp++) {
			Node nNode = nList.item(temp);

			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				String cond = null;
				// The edge contains a condition only if the source is a
				// xorsplit gateway
				if (!(eElement.getElementsByTagName("bpmn2:conditionExpression").item(0) == null)) {
					cond = eElement.getElementsByTagName("bpmn2:conditionExpression").item(0).getTextContent();
				}
				NodeList list = doc.getElementsByTagName("bpmn2:exclusiveGateway");
				
				for (int i = 0; i < list.getLength(); i++) {
					Node node = list.item(i);
					Element element = (Element) node;
					System.out.println(element.getAttribute("id"));
					if (element.getAttribute("id").equals(eElement.getAttribute("sourceRef"))) {
						cond = eElement.getAttribute("name");
					}
				}
							
//				if (doc.getElementById(eElement.getAttribute("sourceRef")).getTagName().equals("bpmn2:exclusiveGateway")) {					
//					cond = eElement.getAttribute("name");
//				}
				flow[k++] = new Edge(eElement.getAttribute("id"), eElement.getAttribute("name"),
						eElement.getAttribute("sourceRef"), eElement.getAttribute("targetRef"), cond);
			}
		}
	}

	private static void fillInActivities(BufferedWriter out, Document doc) throws IOException {
		// First, we find all the subprocesses
		NodeList subProcesses = doc.getElementsByTagName("bpmn2:subProcess");
		String a0 = "";

		// To ensure all clauses of the same type are together in the prolog
		// file,
		// we save them in hashtables and add them at the end
		HashSet<String> begins_with = new HashSet<String>();
		HashSet<String> ends_with = new HashSet<String>();
		HashMap<String, Integer> activities_set = new HashMap<String, Integer>();

		if (subProcesses.getLength() == 0) {
			// If there is no subprocess, there should be only one start event
			// We find this only start event, the following activity is the
			// initial activity
			// We save this initial activity in the string a0
			NodeList nList = doc.getElementsByTagName("bpmn2:startEvent");
			if (nList.getLength() > 1) {
				System.out.println("Error : process containing several start events");
				return;
			} else {
				Node nNode = nList.item(0);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String strtemp = eElement.getElementsByTagName("bpmn2:outgoing").item(0).getTextContent();
					a0 = flow[getFlowId(strtemp)].target;
				}
			}
			// we select all activities ("task" in the xml file)
			NodeList tasks = doc.getElementsByTagName("bpmn2:task");
			N = tasks.getLength();
			facts++;
			out.write("no_of_actions(" + (N + 1) + ")." + "\n" + "\n");
			activities = new String[N];
			activitiesIds = new String[N];
			int k = 1;
			// we save all activities
			// activities names are saved in the array activities
			// their ids (ex: "Task11") are saved in the array activitiesIds
			// when we look for rules (sequential, andjoin,...), we find the
			// activity corresponding to an id using this array
			for (int temp = 0; temp < N; temp++) {
				Node nNode = tasks.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String strtemp = eElement.getAttribute("id");
					String strtemp2 = eElement.getAttribute("name");
					System.out.println("string1: " + strtemp2);
					strtemp2 = toPrologString(strtemp2);
					if (activities_set.containsKey(strtemp2)) {
						int count = activities_set.get(strtemp2) + 1;
						activities_set.put(strtemp2, count);
						strtemp2 = strtemp2 + count;
					} else {
						activities_set.put(strtemp2, 0);
					}
					if (!strtemp.equals(a0)) {
						facts++;
						out.write("action_number(" + strtemp2 + "," + k + ")." + "\n");
						activities[k] = strtemp2;
						activitiesIds[k++] = strtemp;
					} else {
						// here, we make sure a0 is saved as the initial
						// activity in the arrray
						activities[0] = strtemp2;
						activitiesIds[0] = strtemp;
						facts++;
						out.write("action_number(" + strtemp2 + "," + 0 + ")." + "\n");
					}
				}
			}
		}
		// if we have subprocesses, it is a little more complex
		else {
			// We save all subprocesses and their ids in arrays
			// We also save start and end activities in arrays
			HashSet<String> startActivitiesIds = new HashSet<String>();
			HashSet<String> endActivitiesIds = new HashSet<String>();
			subProcessesNames = new String[subProcesses.getLength()];
			subProcessesIds = new String[subProcesses.getLength()];
			subProcessesStartActivities = new String[subProcesses.getLength()];
			subProcessesEndActivities = new String[subProcesses.getLength()];
			for (int temp = 0; temp < subProcesses.getLength(); temp++) {
				Node nNode = subProcesses.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String processId = eElement.getAttribute("id");
					String processName = eElement.getAttribute("name");
					if (activities_set.containsKey(processName)) {
						int count = activities_set.get(processName) + 1;
						activities_set.put(processName, count);
						processName = processName + count;
					} else {
						activities_set.put(processName, 0);
					}
					subProcessesIds[temp] = processId;
					subProcessesNames[temp] = processName;
					Node startEvent = eElement.getElementsByTagName("bpmn2:startEvent").item(0);
					if (startEvent.getNodeType() == Node.ELEMENT_NODE) {
						Element startEventElement = (Element) startEvent;
						String strtemp = startEventElement.getElementsByTagName("bpmn2:outgoing").item(0)
								.getTextContent();
						startActivitiesIds.add(flow[getFlowId(strtemp)].target);
						subProcessesStartActivities[temp] = flow[getFlowId(strtemp)].target;
					}
					Node endEvent = eElement.getElementsByTagName("bpmn2:endEvent").item(0);
					if (endEvent.getNodeType() == Node.ELEMENT_NODE) {
						Element endEventElement = (Element) endEvent;
						String strtemp = endEventElement.getElementsByTagName("bpmn2:incoming").item(0)
								.getTextContent();
						endActivitiesIds.add(flow[getFlowId(strtemp)].source);
						subProcessesEndActivities[temp] = flow[getFlowId(strtemp)].source;
					}
				}
			}
			// Here, all subprocess initial activities are contained in
			// startActivities
			// There should be only one initial activity left, which is the main
			// process initial activity
			NodeList startEvents = doc.getElementsByTagName("bpmn2:startEvent");
			for (int temp = 0; temp < startEvents.getLength(); temp++) {
				Node nNode = startEvents.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String strtemp = eElement.getElementsByTagName("bpmn2:outgoing").item(0).getTextContent();
					if (!startActivitiesIds.contains(flow[getFlowId(strtemp)].target)) {
						a0 = flow[getFlowId(strtemp)].target;
					}

				}
			}
			// Now, we can save all activities in the array activities
			NodeList tasks = doc.getElementsByTagName("bpmn2:task");
			N = tasks.getLength();
			facts++;
			out.write("no_of_actions(" + (N + 1) + ")." + "\n" + "\n");
			activities = new String[N];
			activitiesIds = new String[N];
			int k = 1;
			for (int temp = 0; temp < N; temp++) {
				Node nNode = tasks.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String strtemp = eElement.getAttribute("id");
					String strtemp2 = eElement.getAttribute("name");
					System.out.println("string2: " + strtemp2);
					strtemp2 = toPrologString(strtemp2);
					if (activities_set.containsKey(strtemp2)) {
						int count = activities_set.get(strtemp2) + 1;
						activities_set.put(strtemp2, count);
						strtemp2 = strtemp2 + count;
					} else {
						activities_set.put(strtemp2, 0);
					}
					// If the activity is the start activity of a subprocess, we
					// add the clause begins_with(...)
					if (startActivitiesIds.contains(strtemp)) {
						int id = getStartActivityId(strtemp);
						facts++;
						begins_with.add("begins_with(" + subProcessesNames[id] + "," + strtemp2 + ")." + "\n" + "\n");
						facts++;
						out.write("action_number(" + strtemp2 + "," + k + ")." + "\n");
						activities[k] = strtemp2;
						activitiesIds[k++] = strtemp;
						// A start activity can also be an end activity
						if (endActivitiesIds.contains(strtemp)) {
							ends_with.add("ends_with(" + subProcessesNames[id] + "," + strtemp2 + ")." + "\n" + "\n");
						}
					}
					// If the activity is the end activity of a subprocess, we
					// add the clause ends_with(...)
					else if (endActivitiesIds.contains(strtemp)) {
						int id = getEndActivityId(strtemp);
						facts++;
						ends_with.add("ends_with(" + subProcessesNames[id] + "," + strtemp2 + ")." + "\n" + "\n");
						facts++;
						out.write("action_number(" + strtemp2 + "," + k + ")." + "\n");
						activities[k] = strtemp2;
						activitiesIds[k++] = strtemp;
					}
					// here, we make sure a0 is saved as the initial activity in
					// the arrray
					else if (strtemp.equals(a0)) {
						activities[0] = strtemp2;
						activitiesIds[0] = strtemp;
						facts++;
						out.write("action_number(" + strtemp2 + "," + 0 + ")." + "\n");
					} else {
						facts++;
						out.write("action_number(" + strtemp2 + "," + k + ")." + "\n");
						activities[k] = strtemp2;
						activitiesIds[k++] = strtemp;
					}
				}
			}

		}

		out.write("\n");

		// We assign an arbitrary duration of 2 to all activities
		/*for (int i = 0; i < activities.length; i++) {
			out.write("dur(" + activities[i] + ",2)." + "\n");
		}*/
		
		

		out.write("\n");

		// We make sure the clauses are together in the prolog file to avoid
		// errors
		for (String s : begins_with) {
			out.write(s);
		}
		for (String s : ends_with) {
			out.write(s);
		}

	}
	private static void dynamicDuration(BufferedWriter out){
		System.out.println("All Activites are shown below:");
		Integer dur=0;
		for(int i=0;i<activities.length;i++){
			System.out.println(activities[i]);
		}
		for(int i=0;i<activities.length;i++){
//			System.out.println("Enter Duration for "+activities[i]+ " Activity");
//			Scanner user_input=new Scanner(System.in);
//			dur=user_input.nextInt();
			dur = 1;
			try {
				facts++;
				out.write("dur("+activities[i]+","+dur+")."+"\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//out.write("dur(" + activities[i] + ",)." + "\n");
			
			
			
		}
		
		
	}

	private static void agents(BufferedWriter out, Document doc) throws IOException {
		// A lane represents an agent
		NodeList lanes = doc.getElementsByTagName("bpmn2:lane");

		// To ensure all clauses of the same type are together in the prolog
		// file,
		// we save them in hashtables and add them at the end
		HashSet<String> agentsNames = new HashSet<String>();
		HashSet<String> qualified = new HashSet<String>();

		agents = new String[lanes.getLength()];
		for (int temp = 0; temp < lanes.getLength(); temp++) {
			// We save every agent in the array agents and in the table
			// agentsNames
			Node nNode = lanes.item(temp);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				String name = eElement.getAttribute("name");
//				System.out.println("string1: " + name);
				if (name.equals("")) {
					name = "name";
				}
				System.out.println("string1: " + name);
				name = toPrologString(name);
				agentsNames.add(name);
				agents[temp] = name;

				// For each lane, we find all nodes inside the lane
				// If the node is an activity/task, that means this agent is
				// qualified to perform this activity
				NodeList actis = eElement.getElementsByTagName("bpmn2:flowNodeRef");
				for (int temp2 = 0; temp2 < actis.getLength(); temp2++) {
					Node nNode2 = actis.item(temp2);
					if (nNode2.getNodeType() == Node.ELEMENT_NODE) {
						String id = nNode2.getTextContent();
						if (id.substring(0, 4).equals("Task")) {
							String acti = getActivity(id);
							String quali = "qualified(" + agents[temp] + "," + acti + ")." + "\n";
							qualified.add(quali);
						}
					}
				}
			}

		}
		for (String s : agents) {
			facts++;
			out.write("agent(" + s + ")." + "\n");
		}
		out.write("\n");
		for (String s : qualified) {
			facts++;
			out.write(s);
		}
		out.write("\n");
	}

	/*
	 * All the activities and agents have successfully been saved We now look
	 * for the rules : sequential, sequential_process, andsplit, andjoin,
	 * xorsplit, xorjoin To ensure there is no error in the prolog file, we add
	 * a String representing the rule only if we find one rule like this The
	 * stepRules method then only adds step(...) rules corresponding to what we
	 * have found in the process
	 */

	private static void sequential(BufferedWriter out) throws IOException {
		for (int i = 0; i < flow.length; i++) {
			// We check every Edge in the flow
			// If the source and the target are both activities ("task"), we
			// have found two sequential activities
			if (flow[i].target.substring(0, 4).equals("Task") & flow[i].source.substring(0, 4).equals("Task")) {
				facts++;
				out.write("sequential(" + getActivity(flow[i].source) + "," + getActivity(flow[i].target) + ")." + "\n"
						+ "\n");
				rules.add("sequential");
			}
		}
	}

	private static void sequential_process(BufferedWriter out) throws IOException {
		for (int i = 0; i < flow.length; i++) {
			// If the source is a subprocess, and the target an activity, we
			// have found the end of this subprocess
			if (flow[i].target.substring(0, 4).equals("Task") & flow[i].source.substring(0, 3).equals("Sub")) {
				facts++;
				out.write("sequential_process(" + getProcess(flow[i].source) + "," + getActivity(flow[i].target) + ")."
						+ "\n" + "\n");
				rules.add("sequentialprocess");
			}
			// If the source is an activity, and the target a subprocess, we
			// have found the start of this subprocess
			if (flow[i].source.substring(0, 4).equals("Task") & flow[i].target.substring(0, 3).equals("Sub")) {
				facts++;
				out.write("sequential_process(" + getActivity(flow[i].source) + "," + getProcess(flow[i].target) + ")."
						+ "\n" + "\n");
				rules.add("sequentialprocess");
			}
		}
	}

	private static void andsplitjoin(BufferedWriter out, Document doc) throws IOException {
		// In the xml file, both andsplit and andjoin are found as parallel
		// gateways
		NodeList gateways = doc.getElementsByTagName("bpmn2:parallelGateway");
		for (int temp = 0; temp < gateways.getLength(); temp++) {
			Node nNode = gateways.item(temp);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				NodeList incoming = eElement.getElementsByTagName("bpmn2:incoming");
				NodeList outgoing = eElement.getElementsByTagName("bpmn2:outgoing");
				// For all gateways, we take all the incoming and outgoing nodes
				// If there is only one incoming, we have an andsplit
				if (incoming.getLength() == 1) {
					String andsplit = "and_split(";
					// We first add the source to the String
					andsplit = andsplit + getActivity(flow[getFlowId(incoming.item(0).getTextContent())].source) + ",[";
					// and then all the targets
					for (int i = 0; i < outgoing.getLength() - 1; i++) {
						andsplit = andsplit + getActivity(flow[getFlowId(outgoing.item(i).getTextContent())].target)
								+ ",";
					}
					andsplit = andsplit
							+ getActivity(
									flow[getFlowId(outgoing.item(outgoing.getLength() - 1).getTextContent())].target)
							+ "]).";
					facts++;
					out.write(andsplit + "\n" + "\n");
					rules.add("andsplit");
				}
				// If there is only one outgoing, we have an andjoin
				else if (outgoing.getLength() == 1) {
					String andjoin = "and_join([";
					// We first add all the sources to the String
					for (int i = 0; i < incoming.getLength() - 1; i++) {
						andjoin = andjoin + getActivity(flow[getFlowId(incoming.item(i).getTextContent())].source)
								+ ",";
					}
					andjoin = andjoin
							+ getActivity(
									flow[getFlowId(incoming.item(incoming.getLength() - 1).getTextContent())].source)
							+ "],";
					// and then the only target
					andjoin = andjoin + getActivity(flow[getFlowId(outgoing.item(0).getTextContent())].target) + ").";
					facts++;
					out.write(andjoin + "\n" + "\n");
					rules.add("andjoin");
				}
			}
		}

	}

	private static void xorsplitjoin(BufferedWriter out, Document doc) throws IOException {
		// In the xml file, both xorsplit and xorjoin are found as exclusive
		// gateways
		NodeList gateways = doc.getElementsByTagName("bpmn2:exclusiveGateway");

		// To ensure all clauses of the same type are together in the prolog
		// file,
		// we save them in hashtables and add them at the end
		HashSet<String> xor_split = new HashSet<String>();
		HashSet<String> xor_join = new HashSet<String>();
		HashSet<String> pairs = new HashSet<String>();
		holds_at = new HashSet<String>();

		xorSplits = new ArrayList<xorSplit>();
		String choice;
		for (int temp = 0; temp < gateways.getLength(); temp++) {
			Node nNode = gateways.item(temp);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				
//				System.out.println("Does Yes Condition hold at gateaway ?");
//				Scanner user_choice=new Scanner(System.in);
//				choice=user_choice.next();
				choice="yes";
				
				
				Element eElement = (Element) nNode;
				NodeList incoming = eElement.getElementsByTagName("bpmn2:incoming");
				NodeList outgoing = eElement.getElementsByTagName("bpmn2:outgoing");
				// For all gateways, we take all the incoming and outgoing nodes
				// If there is only one incoming, we have an xorsplit
				if (incoming.getLength() == 1) {
					String xorsplit = "xor_split(";
					String gatewayName = eElement.getAttribute("name");
					String gatewayId = eElement.getAttribute("id");
//					System.out.println("string2: " + gatewayId);
					System.out.println("string1: " + gatewayName);
					if (gatewayName.equals("")) {
						gatewayName = "getawayName";
					}
					gatewayName = toPrologString(gatewayName);
					List<Edge> outgoingEdges = new ArrayList<Edge>();

					// We first add the source to the String
					xorsplit = xorsplit + getActivity(flow[getFlowId(incoming.item(0).getTextContent())].source) + ",[";
					// and then all the targets
					// We pair the conditions with their activities
					// and we assign (for now) an arbitrary duration to the
					// condition
					
					for (int i = 0; i < outgoing.getLength(); i++) {
						if(choice.equalsIgnoreCase("yes")){
							String condi = flow[getFlowId(outgoing.item(i).getTextContent())].condition;
							System.out.println("VIENE: " + i + " :" +flow[getFlowId(outgoing.item(i).getTextContent())].name);
							System.out.println("cond: " + condi);
							if (condi.equals("")) {
								condi = "conditionName";
							}
							condi = toPrologString(condi);
							if(condi.contains("yes")){
								holds_at.add("holds_at("+condi+",T,_):-T > 0.\n");
							}else{
								holds_at.add("holds_at("+condi+",0,_).\n");
							}
							
						}else{
							String condi = flow[getFlowId(outgoing.item(i).getTextContent())].condition;
							System.out.println("condi: " + condi);
							if (condi.equals("")) {
								condi = "conditionName";
							}
							condi = toPrologString(condi);
							if(condi.contains("yes")){
								holds_at.add("holds_at("+condi+",0,_).\n");
							}else{
								holds_at.add("holds_at("+condi+",T,_):-T>0. \n");
								
							}
						}
					}
					for (int i = 0; i < outgoing.getLength() -1; i++) {
						
							String acti = getActivity(flow[getFlowId(outgoing.item(i).getTextContent())].target);
							xorsplit = xorsplit + acti + ",";
							String cond = flow[getFlowId(outgoing.item(i).getTextContent())].condition;
							System.out.println("condi: " + cond);
							if (cond.equals("")) {
								cond = "conditionName";
							}
							cond = toPrologString(cond);
							pairs.add("pair(" + acti + "," + cond + ")." + "\n");
						//	holds_at.add("% holds_at("+cond+",0,_)."+"\n");
							outgoingEdges.add(flow[getFlowId(outgoing.item(i).getTextContent())]);
					}
					xorsplit = xorsplit
							+ getActivity(
									flow[getFlowId(outgoing.item(outgoing.getLength() - 1).getTextContent())].target)
							+ "]).";
					String cond = flow[getFlowId(outgoing.item(outgoing.getLength() - 1).getTextContent())].condition;
					System.out.println("condi: " + cond);
					if (cond.equals("")) {
						cond = "conditionName";
					}
					cond = toPrologString(cond);
					pairs.add("pair("
							+ getActivity(
									flow[getFlowId(outgoing.item(outgoing.getLength() - 1).getTextContent())].target)
							+ "," + cond + ")." + "\n" + "\n");
					// holds_at.add("% holds_at("+cond+",T,_) :- T > 0."+"\n");
					xor_split.add(xorsplit + "\n");
					rules.add("xorsplit");

					outgoingEdges.add(flow[getFlowId(outgoing.item(outgoing.getLength() - 1).getTextContent())]);
					xorSplit gate = new xorSplit(gatewayId, gatewayName, outgoingEdges);
					xorSplits.add(gate);
				} else if (outgoing.getLength() == 1) {
					String xorjoin = "xor_join([";
					for (int i = 0; i < incoming.getLength() - 1; i++) {
						xorjoin = xorjoin + getActivity(flow[getFlowId(incoming.item(i).getTextContent())].source)
								+ ",";
					}
					xorjoin = xorjoin
							+ getActivity(
									flow[getFlowId(incoming.item(incoming.getLength() - 1).getTextContent())].source)
							+ "],";
					xorjoin = xorjoin + getActivity(flow[getFlowId(outgoing.item(0).getTextContent())].target) + ").";
					xor_join.add(xorjoin + "\n");
					rules.add("xorjoin");
					
					
				}
			}
		}
		
		for (String s : pairs) {
			facts++;
			out.write(s);
		}
		out.write("\n");
		for (String s : holds_at) {
			facts++;
			out.write(s);
		}
		out.write("\n");
		for (String s : xor_split) {
			facts++;
			out.write(s);
		}
		out.write("\n");
		for (String s : xor_join) {
			facts++;
			out.write(s);
		}
		out.write("\n");
		
	}

	// The stepRules method only adds step(...) rules corresponding to what we
	// have found in the process
	private static void stepRules(BufferedWriter out) throws IOException {
		if (rules.contains("sequential")) {
			rulesCount++;
			out.write(
					"step(happens(start(A),T),H) :- happens3(end(A0),T,H),sequential_with_ids(A0,A),not( happens3(start(A),T,H) )."
							+ "\n" + "\n");
		}
		if (rules.contains("xorsplit")) {
			rulesCount++;
			out.write(
					"step(happens(start(act(Ai,J)),T),H) :-xor_split(Aname,L), happens3(end(act(Aname,I)),T,H), member(Ai,L), pair(Ai,Cond),holds_at(Cond,T,H), not( happens3(start(act(Ai,J)),T,H)), newId(Ai,I,J)."
							+ "\n" + "\n");
		}
		if (rules.contains("xorjoin")) {
			rulesCount++;
			out.write(
					"step(happens(start(act(Aname,J)),T),H):- xor_join(L,Aname),happens3(end(act(Ai,I)),T,H),member(Ai,L),not( happens3(start(act(Aname,J)),T,H)), newId(Aname,I,J)."
							+ "\n" + "\n");
		}
		if (rules.contains("andjoin")) {
			rulesCount++;
			out.write(
					"step(happens(start(act(A,J)),T), H) :- and_join( L, A), endtime(L, I, T, H),newId(A, I, J), not( happens3(start(act(A,J)),T,H))."
							+ "\n" + "\n");
		}
		if (rules.contains("sequentialprocess")) {
			rulesCount++;
			out.write(
					"step(happens(start(A),T),H) :- happens3(end(A0),T,H),sequential_process_with_ids(A0,A),not(happens3(start(A),T,H))."
							+ "\n" + "\n");
		}
		if (rules.contains("andsplit")) {
			rulesCount++;
			out.write(
					"steps( Events, H ) :- happens3( end(A), T, H ),start_ANDsplit( A, T, Events ),Events = [happens(start(A1),_)|_],not( member( happens(start(A1),T), H ) )."
							+ "\n" + "\n");
		}
		if (rules.contains("sequential")) {
			rulesCount++;
			out.write("sequential_with_ids(act(A1,I1),act(A2,I2)) :- sequential( A1, A2 ),newId( A2, I1, I2 )." + "\n"
					+ "\n");
		}
		if (rules.contains("andsplit")) {
			rulesCount++;
			out.write(
					"start_ANDsplit( act(Aname,I), T, Events ) :- and_split( Aname, Anames ),create_events( Anames, I, T, Events )."
							+ "\n" + "\n");
			facts++;
			out.write("create_events( [], _, _, [] )." + "\n");
			rulesCount++;
			out.write(
					"create_events( [Aname|L], I, T, [happens(start(act(Aname,J)),T) | Events] ) :- newId( Aname, I, J ), create_events( L, I, T, Events )."
							+ "\n" + "\n");
		}
		if (rules.contains("andjoin")) {
			rulesCount++;
			out.write(
					"find_last_occur( A, [E|H], T ) :- E \\= happens(start(act(A,_Id)),_), find_last_occur( A, H, T )."
							+ "\n");
			rulesCount++;
			out.write(
					"find_last_occur( A, [E|H], T ) :- E = happens(start(act(A,_Id)),T1), find_last_occur( A, H, T2 ),T is max(T1,T2)."
							+ "\n");
			facts++;
			out.write("find_last_occur( _A, [], 0 )." + "\n");
			rulesCount++;
			out.write("find_last_occur(A, [], -2):- happens3(act(A,_Id),0,[])." + "\n" + "\n");
			rulesCount++;
			out.write(
					"endtime([A1], I, T, H) :- happens3(end(act(A1,I)),T,H), and_join(L, A), member(A1,L),find_last_occur(A, H, T1), T >= T1."
							+ "\n");
			rulesCount++;
			out.write(
					"endtime([A1|L], I, T, H) :- happens3(end(act(A1,_)), T1, H), endtime(L, I, TL, H), T is max(T1,TL)."
							+ "\n" + "\n");
		}
		if (rules.contains("sequentialprocess")) {
			rulesCount++;
			out.write(
					"sequential_process_with_ids( act(A1,I1), act(A2,I2)) :- sequential_process(A1,P),begins_with(P,A2),newId( A2,I1,I2)."
							+ "\n" + "\n");
			rulesCount++;
			out.write(
					"sequential_process_with_ids( act(A1,I1), act(A2,I2)) :- sequential_process(P,A2),ends_with(P,A1),newId( A2,I1,I2)."
							+ "\n" + "\n");
		}
	}

	// With this method, we erase all spaces from an activity or agent name
	// We also make sure the first letter is lower case so that it is not
	// mistaken with a variable in prolog
	private static String toPrologString(String name) {
		
		String res = name.substring(0, 1).toLowerCase() + name.substring(1);
		char c;
		for (int i = 0; i < res.length() - 1; i++) {
			c = res.charAt(i);
			if (c == ' ') {
				if (i + 1 < res.length())
					res = res.substring(0, i) + res.substring(i + 1, i + 2).toUpperCase() + res.substring(i + 2);
				else
					res = res.substring(0, i) + res.substring(i + 1).toUpperCase();
			}
		}
		if (res.substring(res.length() - 1).equals(" "))
			res = res.substring(0, res.length() - 1);

		return res;
	}

	/*
	 * With the following methods, we return the id matching the
	 * edge/activity/subprocess
	 */

	private static int getFlowId(String id) {
		for (int i = 0; i < flow.length; i++) {
			if (flow[i].id.equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int getStartActivityId(String id) {
		for (int i = 0; i < subProcessesStartActivities.length; i++) {
			if (subProcessesStartActivities[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static int getEndActivityId(String id) {
		for (int i = 0; i < subProcessesEndActivities.length; i++) {
			if (subProcessesEndActivities[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/*
	 * With the following methods, we return the name of the activity/subprocess
	 * matching the id
	 */
	private static String getActivity(String id) {
		for (int i = 0; i < N; i++) {
			if (id.equals(activitiesIds[i]))
				return activities[i];
		}
		return null;
	}

	private static String getProcess(String id) {
		for (int i = 0; i < N; i++) {
			if (id.equals(subProcessesIds[i]))
				return subProcessesNames[i];
		}
		return null;
	}
}
