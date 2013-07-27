package randomjyrest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import utilities.ImmutableThreeValues;
import utilities.ImmutableTwoValues;

public class Forest
{
	
	/**
	 * The trees that make up the forest.
	 */
	public List<Tree> forest;


	public final void main(String dataset, int numberOfTrees, int mtry, List<String> featuresToRemove, double[] weights, int numberOfProcesses,
			boolean isCalcualteOOB)
	{
		this.forest = new ArrayList<Tree>(numberOfTrees);
		growForest(dataset, featuresToRemove, weights, numberOfTrees, mtry, new Random(), numberOfProcesses, isCalcualteOOB);
	}

	public final void main(String dataset, int numberOfTrees, int mtry, List<String> featuresToRemove, double[] weights, long seed,
			int numberOfProcesses, boolean isCalcualteOOB)
	{
		this.forest = new ArrayList<Tree>(numberOfTrees);
		growForest(dataset, featuresToRemove, weights, numberOfTrees, mtry, new Random(seed), numberOfProcesses, isCalcualteOOB);
	}


	public final void growForest(String dataset, List<String> featuresToRemove, double[] weights, int numberOfTrees, int mtry, Random forestRNG,
			int numberOfProcesses, boolean isCalcualteOOB)
	{
		List<Set<Integer>> oobObservations = new ArrayList<Set<Integer>>(numberOfTrees);

		{	
			ImmutableThreeValues<Map<String, double[]>, Map<String, int[]>, Map<String, double[]>> processedData =
					ProcessDataset.main(dataset, featuresToRemove, weights);
			Map<String, double[]> processedFeatureData = processedData.first;
			Map<String, int[]> processedIndexData = processedData.second;
			Map<String, double[]> processedClassData = processedData.third;
	
			// Determine the classes in the dataset, and the indices of the observations from each class.
			List<String> classes = new ArrayList<String>(processedClassData.keySet());
			int numberOfObservations = processedClassData.get(classes.get(0)).length;
			Map<String, List<Integer>> observationsFromEachClass = new HashMap<String, List<Integer>>();
			for (String s : classes)
			{
				double[] classWeights = processedClassData.get(s);
				List<Integer> observationsInClass = new ArrayList<Integer>();
				for (int i = 0; i < numberOfObservations; i++)
				{
					if (classWeights[i] != 0.0)
					{
						observationsInClass.add(i);
					}
				}
				observationsFromEachClass.put(s, observationsInClass);
			}
			
			// Grow trees.
			final ExecutorService treeGrowthPool = Executors.newFixedThreadPool(numberOfProcesses);
			List<Future<ImmutableTwoValues<Set<Integer>, Tree>>> futureGrowers = new ArrayList<Future<ImmutableTwoValues<Set<Integer>, Tree>>>(numberOfTrees);
			for (int i = 0; i < numberOfTrees; i++)
			{
				futureGrowers.add(treeGrowthPool.submit(new TreeGrower(processedFeatureData, processedIndexData, processedClassData,
						mtry, forestRNG.nextLong(), observationsFromEachClass, numberOfObservations)));
			}
			
			// Get the results of growing the trees.
			try
			{
				for (Future<ImmutableTwoValues<Set<Integer>, Tree>> t : futureGrowers)
				{
					ImmutableTwoValues<Set<Integer>, Tree> growthReturn = t.get();
					t = null;
					oobObservations.add(growthReturn.first);
					this.forest.add(growthReturn.second);
				}
			}
			catch (ExecutionException e)
			{
				System.out.println("Error in a grower thread.");
				e.printStackTrace();
				System.exit(0);
			}
			catch (InterruptedException e)
			{
				// Interrupted the thread, so exit the program.
				System.out.println("Grower interruption received.");
				e.printStackTrace();
				System.exit(0);
			}
			finally
			{
				treeGrowthPool.shutdown();
			}
		}
		
		// Make OOB predictions if required.
		if (isCalcualteOOB)
		{
			DateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		    Date startTime = new Date();
		    String strDate = sdfDate.format(startTime);
		    System.out.format("Start predicting at %s.\n", strDate);
			// Generate the entire set of prediction data.
			Map<String, double[]> datasetToPredict = ProcessPredictionData.main(dataset, featuresToRemove);

			final ExecutorService treePredictionPool = Executors.newFixedThreadPool(numberOfProcesses);
			List<Future<Map<Integer, Map<String, Double>>>> futurePredictions = new ArrayList<Future<Map<Integer, Map<String, Double>>>>(numberOfTrees);
			for (int i = 0; i < numberOfTrees; i++)
			{
				futurePredictions.add(treePredictionPool.submit(new PredictionGenerator(this.forest.get(i), datasetToPredict, oobObservations.get(i)))); 
			}
			
			try
			{
				for (Future<Map<Integer, Map<String, Double>>> f : futurePredictions)
				{
					//TODO combine the predictions.
					f.get();
				}
			}
			catch (ExecutionException e)
			{
				System.out.println("Error in a predictor thread.");
				e.printStackTrace();
				System.exit(0);
			}
			catch (InterruptedException e)
			{
				// Interrupted the thread, so exit the program.
				System.out.println("Predictor interruption received.");
				e.printStackTrace();
				System.exit(0);
			}
			finally
			{
				treePredictionPool.shutdown();
			}
			
			sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		    startTime = new Date();
		    strDate = sdfDate.format(startTime);
		    System.out.format("End Predicting at %s.\n", strDate);
		}
	}
	
	
	public final void predict(String dataset, List<String> featuresToRemove, double[] weights, int numberOfProcesses)
	{
		Map<String, double[]> datasetToPredict = ProcessPredictionData.main(dataset, featuresToRemove);
		
		int numberOfTrees = this.forest.size();
		final ExecutorService treePredictionPool = Executors.newFixedThreadPool(numberOfProcesses);
		List<Future<Map<Integer, Map<String, Double>>>> futurePredictions = new ArrayList<Future<Map<Integer, Map<String, Double>>>>(numberOfTrees);
		for (Tree t : this.forest)
		{
			futurePredictions.add(treePredictionPool.submit(new PredictionGenerator(t, datasetToPredict, new HashSet<Integer>()))); 
		}
		
		try
		{
			for (Future<Map<Integer, Map<String, Double>>> f : futurePredictions)
			{
				//TODO combine the predictions.
				f.get();
			}
		}
		catch (ExecutionException e)
		{
			System.out.println("Error in a predictor thread.");
			e.printStackTrace();
			System.exit(0);
		}
		catch (InterruptedException e)
		{
			// Interrupted the thread, so exit the program.
			System.out.println("Predictor interruption received.");
			e.printStackTrace();
			System.exit(0);
		}
		finally
		{
			treePredictionPool.shutdown();
		}
	}

}