package algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tree.ImmutableTwoValues;
import tree.ProcessDataForGrowing;
import tree.TreeGrowthControl;

public class KMeansPULearning
{

	/**
	 * @param args
	 */
	public ImmutableTwoValues<Set<Integer>, Map<Integer, Double>> main(String dataForLearning, int numberOfMeans, int clusteringRepetitions, boolean isReliableNegativesGenerated)
	{
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================
		TreeGrowthControl ctrl = new TreeGrowthControl();
		ctrl.isStandardised = true;

		String[] variablesToIgnore = new String[]{"OGlycosylation"};  // Make sure to ignore any variables that are constant. Otherwise the standardised value of the variable will be NaN.
		ctrl.variablesToIgnore = Arrays.asList(variablesToIgnore);
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================

		// Process the input data.
		ProcessDataForGrowing processedDataForLearning = new ProcessDataForGrowing(dataForLearning, ctrl);

		// Sanity check on the number of means.
		if (numberOfMeans > processedDataForLearning.numberObservations)
		{
			System.out.format("You specified %d means, but only have %d observations in the dataset.\n", numberOfMeans, processedDataForLearning.numberObservations);
			System.exit(0);
		}

		// Setup the results.
		Map<Integer, Double> weightModifiers = new HashMap<Integer, Double>();
		Set<Integer> finalPositiveSet = new HashSet<Integer>();

		// Determine the indices for the positive, unlabelled and all observations.
		List<Integer> allObservations = new ArrayList<Integer>();
		List<Integer> positiveObservations = new ArrayList<Integer>();
		List<Integer> unlabelledObservations = new ArrayList<Integer>();
		for (int i = 0; i < processedDataForLearning.numberObservations; i++)
		{
			allObservations.add(i);
			if (processedDataForLearning.responseData.get(i).equals("Positive"))
			{
				// If the observation is in the 'Positive' class.
				positiveObservations.add(i);
				finalPositiveSet.add(i);
				weightModifiers.put(i, 1.0);
			}
			else
			{
				// If the observation is in the 'Unlabelled' class.
				unlabelledObservations.add(i);
			}
		}
		int numberAllObservations = allObservations.size();
		int numberPositiveObservations = positiveObservations.size();
		int numberUnlabelledObservations = unlabelledObservations.size();

		// Cluster the data clusteringRepetitions times.
		for (int i = 0; i < clusteringRepetitions; i++)
		{
			// Initialise the means.
			List<Map<String, Double>> clusterMeans = initialiseClusterMeans(numberOfMeans, processedDataForLearning);

			// Perform the initial determination of the cluster assignments.
			Map<Integer, Set<Integer>> clusterAssignment = assignCluster(clusterMeans, processedDataForLearning);  // A mapping from observation indices to the index of the cluster they belong to.
			boolean isAssignmentChanged = true;

			// Update the clusters until convergence.
			while (isAssignmentChanged)
			{
				isAssignmentChanged = false;

				// Update means.
				clusterMeans = updateClusterMeans(clusterAssignment, processedDataForLearning);

				// Update the assignments.
				Map<Integer, Set<Integer>> newAssignment = assignCluster(clusterMeans, processedDataForLearning);

				// Determine if the assignment has changed.
				for (Integer j : newAssignment.keySet())
				{
					if (!newAssignment.get(j).equals(clusterAssignment.get(j)))
					{
						// If the assignment is not the same.
						isAssignmentChanged = true;
						break;
					}
				}
				clusterAssignment = newAssignment;
			}
			System.out.println(clusterAssignment);
			System.out.println(clusterMeans);
		}

		return new ImmutableTwoValues<Set<Integer>, Map<Integer, Double>>(finalPositiveSet, weightModifiers);
	}


	private Map<Integer, Set<Integer>> assignCluster(List<Map<String, Double>> clusterMeans, ProcessDataForGrowing dataset)
	{
		List<Integer> observationIndices = new ArrayList<Integer>();
		Map<Integer, Double> distancesToCluster = new HashMap<Integer, Double>();
		Map<Integer, Integer> clusterAssignment = new HashMap<Integer, Integer>();
		for (int i = 0; i < dataset.numberObservations; i++)
		{
			observationIndices.add(i);
			distancesToCluster.put(i, Double.MAX_VALUE);
			clusterAssignment.put(i, 0);
		}

		for (int i = 0; i < clusterMeans.size(); i++)
		{
			Map<String, Double> mean = clusterMeans.get(i);
			List<Double> distances = distanceFromMean(observationIndices, mean, dataset);
			// Determine if the distance to this cluster is less than the distance to all other clusters checked so far.
			for (Integer j : observationIndices)
			{
				if (distances.get(j) < distancesToCluster.get(j))
				{
					// If the distance to the curretn cluster mean is less than the distance to the closest mean checked so far,
					// then change the closest cluster mean to this one.
					distancesToCluster.put(j, distances.get(j));
					clusterAssignment.put(j, i);
				}
			}
		}

		Map<Integer, Set<Integer>> clusters = new HashMap<Integer, Set<Integer>>();
		for (int i = 0; i < clusterMeans.size(); i++)
		{
			Set<Integer> observationsInCluster = new HashSet<Integer>();
			for (Integer j : observationIndices)
			{
				if (clusterAssignment.get(j) == i)
				{
					observationsInCluster.add(j);
				}
			}
			clusters.put(i, observationsInCluster);
			observationIndices.removeAll(observationsInCluster);
		}
		return clusters;
	}

	private Map<String, Double> calculateMeanVector(List<Integer> observationsToGetMeanFor, ProcessDataForGrowing dataset)
	{
		Map<String, Double> meanVector = new HashMap<String, Double>();
		for (String s : dataset.covariableData.keySet())
		{
			double expectedValue = 0.0;
			for (Integer i : observationsToGetMeanFor)
			{
				expectedValue += dataset.covariableData.get(s).get(i);
			}
			expectedValue /= observationsToGetMeanFor.size();
			meanVector.put(s, expectedValue);
		}
		return meanVector;
	}

	private List<Double> distanceFromMean(List<Integer> observationIndices, Map<String, Double> meanPositiveVector, ProcessDataForGrowing dataset)
	{
		List<Double> distances = new ArrayList<Double>();
		for (Integer i : observationIndices)
		{
			double obsDistance = 0.0;
			for (String s : dataset.covariableData.keySet())
			{
				obsDistance += Math.pow(meanPositiveVector.get(s) - dataset.covariableData.get(s).get(i), 2);
			}
			obsDistance = Math.pow(obsDistance, 0.5);
			distances.add(obsDistance);
		}
		return distances;
	}

	private List<Map<String, Double>> initialiseClusterMeans(int numberOfMeans, ProcessDataForGrowing dataset)
	{
		// Randomise the list of indices of the observations in the dataset, and then take the first numberOfMeans observations
		// to be the observations that the cluster means are initialised to.
		List<Integer> observationIndices = new ArrayList<Integer>();
		for (int i = 0; i < dataset.numberObservations; i++)
		{
			observationIndices.add(i);
		}
		Collections.shuffle(observationIndices);

		List<Map<String, Double>> clusterMeans = new ArrayList<Map<String, Double>>();
		for (int i = 0; i < numberOfMeans; i++)
		{
			int observationIndex = observationIndices.get(i);
			Map<String, Double> newMean = new HashMap<String, Double>();
			for (String s : dataset.covariableData.keySet())
			{
				newMean.put(s, dataset.covariableData.get(s).get(observationIndex));
			}
			clusterMeans.add(newMean);
		}

		return clusterMeans;
	}

	private List<Map<String, Double>> updateClusterMeans(Map<Integer, Set<Integer>> clusterAssignment, ProcessDataForGrowing dataset)
	{
		List<Map<String, Double>> clusterMeans = new ArrayList<Map<String, Double>>();
		for (int i = 0; i < clusterAssignment.size(); i++)
		{
			clusterMeans.add(calculateMeanVector(new ArrayList<Integer>(clusterAssignment.get(i)), dataset));
		}
		return clusterMeans;
	}

}
