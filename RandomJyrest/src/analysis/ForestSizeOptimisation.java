package analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import randomjyrest.Forest;
import randomjyrest.PredictionAnalysis;
import utilities.DetermineDatasetProperties;

/**
 * Implements the testing of different forest sizes.
 */
public class ForestSizeOptimisation
{

	/**
	 * Compares different forest sizes.
	 * 
	 * Used in the optimisation of the numberOfTreesToGrow parameter of the random forest.
	 * 
	 * @param args		The file system locations of the files and directories used in the optimisation.
	 */
	public static final void main(String[] args)
	{
		String inputFile = args[0];  // The location of the dataset used to grow the forests.
		String resultsDir = args[1];  // The location where the results of the optimisation will be written.
		String parameterFile = args[2];  // The location where the parameters for the optimisation are recorded.
		
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================
		int numberOfForestsToCreate = 100;  // The number of forests to create for each forest size.
		int[] forestSizesToUse = {  // The number of trees to test.
				50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800, 850, 900,
				950, 1000, 1050, 1100, 1150, 1200, 1250, 1300, 1350, 1400, 1450, 1500, 1550, 1600, 1650, 1700, 1750, 1800, 1850,
				1900, 1950, 2000, 2050, 2100, 2150, 2200, 2250, 2300, 2350, 2400, 2450, 2500, 2550, 2600, 2650, 2700, 2750, 2800,
				2850, 2900, 2950, 3000, 3050, 3100, 3150, 3200, 3250, 3300, 3350, 3400, 3450, 3500, 3550, 3600, 3650, 3700, 3750,
				3800, 3850, 3900, 3950, 4000, 4050, 4100, 4150, 4200, 4250, 4300, 4350, 4400, 4450, 4500, 4550, 4600, 4650, 4700,
				4750, 4800, 4850, 4900, 4950, 5000};
		int mtry = 10;  // The number of features to consider at each split in a tree.
		
		// Specify the features in the input dataset that should be ignored.
		List<String> featuresToRemove = new ArrayList<String>();
		
		int numberOfThreads = 1;  // The number of threads to use when growing a forest.

		// Define the weights for each class in the input dataset.
		Map<String, Double> classWeights = new HashMap<String, Double>();
		//===================================================================
		//==================== CONTROL PARAMETER SETTING ====================
		//===================================================================
		
		// Parse the parameters.
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader(new FileReader(parameterFile));
			String line = null;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				if (line.length() == 0)
				{
					// If the line is made up of all whitespace, then ignore the line.
					continue;
				}

				String[] chunks = line.split("\t");
				if (chunks[0].equals("Forests"))
				{
					// If the first entry on the line is Forests, then the line records the number of forests to create for each forest size.
					numberOfForestsToCreate = Integer.parseInt(chunks[1]);
				}
				else if (chunks[0].equals("Trees"))
				{
					// If the first entry on the line is Trees, then the line records the forest sizes (number of trees) to test.
					String[] trees = chunks[1].split(",");
					forestSizesToUse = new int[trees.length];
					for (int i = 0; i < trees.length; i++)
					{
						forestSizesToUse[i] = Integer.parseInt(trees[i]);
					}
				}
				else if (chunks[0].equals("Mtry"))
				{
					// If the first entry on the line is Mtry, then the line contains the value of the mtry parameter.
					mtry = Integer.parseInt(chunks[1]);
				}
				else if (chunks[0].equals("Features"))
				{
					// If the first entry on the line is Features, then the line contains the features in the dataset to ignore.
					String[] features = chunks[1].split(",");
					featuresToRemove = Arrays.asList(features);
				}
				else if (chunks[0].equals("Threads"))
				{
					// If the first entry on the line is Threads, then the line contains the number of threads to use when growing a forest.
					numberOfThreads = Integer.parseInt(chunks[1]);
				}
				else if (chunks[0].equals("Weight"))
				{
					// If the first entry on the line is Weight, then the line contains a weight (third entry) for a class (second entry).
					classWeights.put(chunks[1], Double.parseDouble(chunks[2]));
				}
				else
				{
					// Got an unexpected line in the parameter file.
					System.out.println("An unexpected argument was found in the file of the parameters:");
					System.out.println(line);
					System.exit(0);
				}
			}
		}
		catch (IOException e)
		{
			// Caught an error while reading the file. Indicate this and exit.
			System.out.println("An error occurred while extracting the parameters.");
			e.printStackTrace();
			System.exit(0);
		}
		finally
		{
			try
			{
				if (reader != null)
				{
					reader.close();
				}
			}
			catch (IOException e)
			{
				// Caught an error while closing the file. Indicate this and exit.
				System.out.println("An error occurred while closing the parameters file.");
				e.printStackTrace();
				System.exit(0);
			}
		}

		boolean isCalculateOOB = true;  // OOB error is being calculated.

		// Setup the directory for the results.
		File resultsDirectory = new File(resultsDir);
		if (!resultsDirectory.exists())
		{
			// The results directory does not exist.
			boolean isDirCreated = resultsDirectory.mkdirs();
			if (!isDirCreated)
			{
				System.out.println("The results directory does not exist, but could not be created.");
				System.exit(0);
			}
		}
		else
		{
			// The results directory already exists.
			System.out.println("The results directory already exists. Please remove/rename the file or directory before retrying");
			System.exit(0);
		}

		// Initialise the results and parameters record files.
		String resultsLocation = resultsDir + "/Results.txt";
		String parameterLocation = resultsDir + "/Parameters.txt";
		try
		{
			// Record the parameters.
			FileWriter parameterOutputFile = new FileWriter(parameterLocation);
			BufferedWriter parameterOutputWriter = new BufferedWriter(parameterOutputFile);
			parameterOutputWriter.write("Forest sizes used - " + Arrays.toString(forestSizesToUse));
			parameterOutputWriter.newLine();
			parameterOutputWriter.write("Number of forests grown - " + Integer.toString(numberOfForestsToCreate));
			parameterOutputWriter.newLine();
			parameterOutputWriter.write("mtry - " + Integer.toString(mtry));
			parameterOutputWriter.newLine();
			parameterOutputWriter.write("Weights used");
			parameterOutputWriter.newLine();
			for (String s : classWeights.keySet())
			{
				parameterOutputWriter.write("\t" + s + " - " + Double.toString(classWeights.get(s)));
				parameterOutputWriter.newLine();
			}
			parameterOutputWriter.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}

		// Generate all the unique random seeds to use in growing the forests. The same numberOfForestsToCreate seeds will be used for
		// every forest size. This ensures that the only difference in the results is due to the chosen forest size.
		Random randGen = new Random();
		List<Long> seeds = new ArrayList<Long>();
		for (int i = 0; i < numberOfForestsToCreate; i++)
		{
			long seedToUse = randGen.nextLong();
			while (seeds.contains(seedToUse))
			{
				// Keep generating seeds until you get a unique one.
				seedToUse = randGen.nextLong();
			}
			seeds.add(seedToUse);
		}
		
		// Determine the class of each observation.
		List<String> classOfObservations = DetermineDatasetProperties.determineObservationClasses(inputFile);
		
		// Determine the vector of weights for the observations.
		double[] weights = DetermineDatasetProperties.determineObservationWeights(inputFile, classWeights);

		for (int i : forestSizesToUse)
		{
			DateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		    Date startTime = new Date();
		    String strDate = sdfDate.format(startTime);
			System.out.format("Now testing forests of size %d at %s.\n", i, strDate);
			
			// Write out the size of the forest being tested.
			try
			{
				FileWriter resultsFile = new FileWriter(resultsLocation, true);
				BufferedWriter resultsOutputWriter = new BufferedWriter(resultsFile);
				resultsOutputWriter.write(Integer.toString(i));
				resultsOutputWriter.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(0);
			}

			// Grow the specified number of forests.
			for (int j = 0; j < numberOfForestsToCreate; j++)
			{
				// Grow the forest, generate the OOB predictions and construct the confusion matrix.
				Forest forest = new Forest();
				Map<String, double[]> predictions = forest.main(inputFile, i, mtry, featuresToRemove, weights, seeds.get(j),
						numberOfThreads, isCalculateOOB);
				Map<String, Map<String, Double>> confusionMatrix = PredictionAnalysis.calculateConfusionMatrix(classOfObservations,
						predictions);
				
				// Write out the results for this forest.
				try
				{
					FileWriter resultsFile = new FileWriter(resultsLocation, true);
					BufferedWriter resultsOutputWriter = new BufferedWriter(resultsFile);
					resultsOutputWriter.write("\t" + Double.toString(PredictionAnalysis.calculateGMean(confusionMatrix, classOfObservations)));
					resultsOutputWriter.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					System.exit(0);
				}
			}
			
			// Finish writing out the results information for this forest size (just a new line).
			try
			{
				FileWriter resultsFile = new FileWriter(resultsLocation, true);
				BufferedWriter resultsOutputWriter = new BufferedWriter(resultsFile);
				resultsOutputWriter.newLine();
				resultsOutputWriter.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(0);
			}
		}
	}

}