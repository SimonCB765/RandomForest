/**
 * 
 */
package tree;

import java.util.List;
import java.util.Map;

/**
 * @author Simon Bull
 *
 */
public abstract class Node
{

	int nodeDepth;
	Node[] children = new Node[2];

	int countTerminalNodes()
	{
		return -1;
	}

	String display()
	{
		return null;
	}

	List<List<Integer>> getProximities(ProcessDataForGrowing processedData, List<Integer> observationIndices)
	{
		return null;
	}

	Map<Integer, Map<String, Double>> predict(ProcessDataForGrowing predData, List<Integer> observationsToPredict)
	{
		return null;
	}

	ImmutableTwoValues<String, Integer> save(Integer nodeID, Integer parentID)
	{
		// Takes the ID of the node and its parent, and returns the next available ID.
		return null;
	}

}
