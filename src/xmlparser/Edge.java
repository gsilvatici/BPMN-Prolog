package xmlparser;

/**
 * 
 * @author Stefan_524450
 * 
 *         For each edge in the business process diagram ("sequenceFlow"), we
 *         create an object Edge An Edge contains an id, a name, the id of the
 *         source, the id of the target, and eventually a condition The source
 *         and the target may be activities, subprocesses, events, or gateways
 *
 */

public class Edge {

	public final String id;
	public final String name;
	public final String source;
	public final String target;
	public final String condition;

	public Edge(String id, String name, String src, String tgt, String cond) {
		this.id = id;
		this.name = name;
		this.source = src;
		this.target = tgt;
		this.condition = cond;
	}

	public boolean hasCondition() {
		return (!(condition == null));
	}

	public boolean hasName() {
		return (!(name == ""));
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
	}

	public String getCondition() {
		return condition;
	}

}
