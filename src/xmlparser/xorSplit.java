package xmlparser;

import java.util.List;

/**
 * 
 * @author Stefan_524450
 * 
 *         This class is used to store all the information about a xor-split
 *
 */

public class xorSplit {

	public final String id;
	public final String name;
	public final List<Edge> outgoing;

	public xorSplit(String id, String name, List<Edge> outgoing) {
		this.id = id;
		this.name = name;
		this.outgoing = outgoing;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the outgoing
	 */
	public List<Edge> getOutgoing() {
		return outgoing;
	}

}
