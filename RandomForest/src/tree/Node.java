/**
 * 
 */
package tree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Bull
 *
 */
public abstract class Node
{

	int nodeDepth;
	int numberOfObservationsInNode;
	Node[] children = new Node[2];
	Map<String, Integer> classCountsInNode = new HashMap<String, Integer>();
	Map<String, Double> weights = new HashMap<String, Double>();

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

	List<List<Integer>> getConditionalGrid(ProcessDataForGrowing processedData, List<List<Integer>> currentGrid, List<String> covToConditionOn)
	{
		return null;
	}

	Map<String, Double> predict(Map<String, Double> currentObservation)
	{
		return null;
	}

	ImmutableTwoValues<String, Integer> save(Integer nodeID, Integer parentID)
	{
		// Takes the ID of the node and its parent, and returns the next available ID.
		return null;
	}

}
